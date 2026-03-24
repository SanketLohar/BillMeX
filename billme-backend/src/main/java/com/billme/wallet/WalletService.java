package com.billme.wallet;

import com.billme.transaction.LedgerEntryType;
import com.billme.transaction.LedgerService;
import com.billme.repository.WalletRepository;
import com.billme.user.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    /**
     * Internal Transfer: FacePay (Customer Debit -> Merchant Escrow Credit)
     */
    @Transactional
    public void updateBalanceInternal(User sender, User receiver, BigDecimal amount, String ref) {
        Wallet senderWallet = walletRepository.findByUserWithLock(sender.getId())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));
        Wallet receiverWallet = walletRepository.findByUserWithLock(receiver.getId())
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        log.info("[WALLET BEFORE] CustomerID: {} Balance: ₹{} | MerchantID: {} Escrow: ₹{} | Ref: {}", 
                sender.getId(), senderWallet.getBalance(), receiver.getId(), receiverWallet.getEscrowBalance(), ref);

        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance for FacePay");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(amount));
        receiverWallet.setEscrowBalance(receiverWallet.getEscrowBalance().add(amount));

        walletRepository.saveAndFlush(senderWallet);
        walletRepository.saveAndFlush(receiverWallet);

        log.info("[WALLET AFTER] CustomerID: {} Balance: ₹{} | MerchantID: {} Escrow: ₹{} | Ref: {}", 
                sender.getId(), senderWallet.getBalance(), receiver.getId(), receiverWallet.getEscrowBalance(), ref);
        
        // Ledger entries
        ledgerService.record(senderWallet.getId(), amount, LedgerEntryType.DEBIT, senderWallet.getBalance(), ref);
        ledgerService.record(receiverWallet.getId(), amount, LedgerEntryType.ESCROW_CREDIT, receiverWallet.getEscrowBalance(), ref);
    }

    /**
     * External Payment: UPI/Card (Merchant Escrow Credit Only)
     */
    @Transactional
    public void updateBalanceExternal(User receiver, BigDecimal amount, String ref) {
        Wallet receiverWallet = walletRepository.findByUserWithLock(receiver.getId())
                .orElseThrow(() -> new RuntimeException("Merchant wallet not found"));

        log.info("[WALLET BEFORE] MerchantID: {} Escrow: ₹{} | Ref: {}", 
                receiver.getId(), receiverWallet.getEscrowBalance(), ref);

        receiverWallet.setEscrowBalance(receiverWallet.getEscrowBalance().add(amount));
        walletRepository.saveAndFlush(receiverWallet);

        log.info("[WALLET AFTER] MerchantID: {} Escrow: ₹{} | Ref: {}", 
                receiver.getId(), receiverWallet.getEscrowBalance(), ref);

        ledgerService.record(receiverWallet.getId(), amount, LedgerEntryType.ESCROW_CREDIT, receiverWallet.getEscrowBalance(), ref);
    }

    /**
     * FacePay Refund: (Merchant Escrow Debit -> Customer Balance Credit)
     */
    @Transactional
    public void updateBalanceRefundInternal(User merchant, User customer, BigDecimal amount, String ref) {
        Wallet merchantWallet = walletRepository.findByUserWithLock(merchant.getId())
                .orElseThrow(() -> new RuntimeException("Merchant wallet not found"));
        Wallet customerWallet = walletRepository.findByUserWithLock(customer.getId())
                .orElseThrow(() -> new RuntimeException("Customer wallet not found"));

        log.info("[WALLET BEFORE] MerchantID: {} Escrow: ₹{} | CustomerID: {} Balance: ₹{} | Ref: {}", 
                merchant.getId(), merchantWallet.getEscrowBalance(), customer.getId(), customerWallet.getBalance(), ref);

        if (merchantWallet.getEscrowBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient merchant escrow for refund");
        }

        merchantWallet.setEscrowBalance(merchantWallet.getEscrowBalance().subtract(amount));
        customerWallet.setBalance(customerWallet.getBalance().add(amount));

        walletRepository.saveAndFlush(merchantWallet);
        walletRepository.saveAndFlush(customerWallet);

        log.info("[WALLET AFTER] MerchantID: {} Escrow: ₹{} | CustomerID: {} Balance: ₹{} | Ref: {}", 
                merchant.getId(), merchantWallet.getEscrowBalance(), customer.getId(), customerWallet.getBalance(), ref);

        ledgerService.record(merchantWallet.getId(), amount, LedgerEntryType.DEBIT, merchantWallet.getEscrowBalance(), ref); // Generic Debit
        ledgerService.record(customerWallet.getId(), amount, LedgerEntryType.CREDIT, customerWallet.getBalance(), ref);
    }

    /**
     * UPI/Card Refund: (Merchant Escrow Debit Only)
     */
    @Transactional
    public void updateBalanceRefundExternal(User merchant, BigDecimal amount, String ref) {
        Wallet merchantWallet = walletRepository.findByUserWithLock(merchant.getId())
                .orElseThrow(() -> new RuntimeException("Merchant wallet not found"));

        log.info("[WALLET BEFORE] MerchantID: {} Escrow: ₹{} | Ref: {}", 
                merchant.getId(), merchantWallet.getEscrowBalance(), ref);

        if (merchantWallet.getEscrowBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient merchant escrow for external refund");
        }

        merchantWallet.setEscrowBalance(merchantWallet.getEscrowBalance().subtract(amount));
        walletRepository.saveAndFlush(merchantWallet);

        log.info("[WALLET AFTER] MerchantID: {} Escrow: ₹{} | Ref: {}", 
                merchant.getId(), merchantWallet.getEscrowBalance(), ref);

        ledgerService.record(merchantWallet.getId(), amount, LedgerEntryType.DEBIT, merchantWallet.getEscrowBalance(), ref);
    }

    @Transactional
    public void debitMainBalance(User user, BigDecimal amount, String ref) {
        Wallet wallet = walletRepository.findByUserWithLock(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        log.info("[WALLET BEFORE DEBIT] User: {} Balance: ₹{} | Ref: {}", 
                user.getId(), wallet.getBalance(), ref);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance for operation");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.saveAndFlush(wallet);

        log.info("[WALLET AFTER DEBIT] User: {} Balance: ₹{} | Ref: {}", 
                user.getId(), wallet.getBalance(), ref);

        ledgerService.record(wallet.getId(), amount, LedgerEntryType.DEBIT, wallet.getBalance(), ref);
    }

    @Transactional(readOnly = true)
    public Wallet getWalletByUser(User user) {
        return walletRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + user.getId()));
    }
}
