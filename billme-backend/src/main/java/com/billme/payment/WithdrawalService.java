package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.merchant.MerchantBankAccount;
import com.billme.merchant.MerchantProfile;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.MerchantBankAccountRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.payment.dto.WithdrawalResponse;
import com.billme.repository.TransactionRepository;
import com.billme.repository.UserRepository;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.notification.NotificationService;
import com.billme.notification.NotificationType;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import com.billme.wallet.WalletService;
import com.billme.wallet.dto.WalletSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final MerchantBankAccountRepository bankAccountRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final NotificationService notificationService;

    @Value("${platform.withdrawal.fee-percent}")
    private BigDecimal withdrawalFeePercent;

    private static final BigDecimal MIN_WITHDRAWAL = BigDecimal.valueOf(100);

    // ============================================================
    // WITHDRAW WITH PLATFORM FEE + REFUND LOCK
    // bankAccountId is optional → null means use default bank.
    // ============================================================
    @Transactional
    public void withdraw(BigDecimal amount, Long bankAccountId) {

        // ── 1. Strict Server-Side Amount Validation ──────────────────────
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Withdrawal amount must be greater than zero");
        }

        if (amount.compareTo(MIN_WITHDRAWAL) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum withdrawal amount is ₹" + MIN_WITHDRAWAL.toPlainString());
        }

        User user = getLoggedInUser();
        Wallet merchantWallet = walletService.getWalletByUser(user);

        // ── 2. Refund Lock Calculation (unchanged) ───────────────────────
        BigDecimal lockedAmount = invoiceRepository
                .findByMerchant_User_IdAndStatus(
                        user.getId(),
                        com.billme.invoice.InvoiceStatus.PAID
                )
                .stream()
                .filter(inv -> inv.getRefundWindowExpiry() != null
                        && inv.getRefundWindowExpiry().isAfter(LocalDateTime.now()))
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal withdrawableBalance = merchantWallet.getBalance().subtract(lockedAmount);

        // ── 3. Strict Server-Side Balance Validation ─────────────────────
        if (withdrawableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No withdrawable balance available. Funds may be locked under active refund windows.");
        }

        if (withdrawableBalance.compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient withdrawable balance. Available: ₹" + withdrawableBalance.toPlainString());
        }

        // ── 4. Bank Account Resolution with Ownership Validation ─────────
        String resolvedBankName;
        Long resolvedBankAccountId;

        if (bankAccountId != null) {
            // SELECTED BANK: validate ownership — security critical
            MerchantBankAccount selectedBank = bankAccountRepository
                    .findByIdAndMerchant_User_Id(bankAccountId, user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Selected bank account does not exist or does not belong to your account"));

            resolvedBankAccountId = selectedBank.getId();
            resolvedBankName      = selectedBank.getBankName()
                    + " — " + maskAccountNumber(selectedBank.getAccountNumber());

        } else {
            // DEFAULT BANK FALLBACK: existing behavior — read from legacy MerchantProfile proxy
            MerchantProfile profile = merchantProfileRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Merchant profile not found"));

            List<MerchantBankAccount> allBanks = bankAccountRepository.findByMerchant_User_Id(user.getId());
            MerchantBankAccount defaultBank = allBanks.stream()
                    .filter(MerchantBankAccount::isDefault)
                    .findFirst()
                    .orElse(null);

            if (defaultBank != null) {
                resolvedBankAccountId = defaultBank.getId();
                resolvedBankName      = defaultBank.getBankName()
                        + " — " + maskAccountNumber(defaultBank.getAccountNumber());
            } else {
                // Legacy fallback: merchant added bank via old profile form only
                resolvedBankAccountId = null;
                resolvedBankName      = profile.getBankName() != null ? profile.getBankName() : "DEFAULT";
            }
        }

        // ── 5. Platform Fee Calculation (unchanged) ──────────────────────
        BigDecimal fee = amount
                .multiply(withdrawalFeePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // ── 6. Wallet Debit (unchanged) ──────────────────────────────────
        walletService.debitMainBalance(user, amount, "WITHDRAW-" + System.currentTimeMillis());

        // ── 7. Withdrawal Transaction — with bank snapshot ───────────────
        Transaction withdrawalTx = Transaction.builder()
                .senderWallet(merchantWallet)
                .receiverWallet(null)
                .amount(amount)
                .transactionType(TransactionType.WITHDRAWAL)
                .status(TransactionStatus.SUCCESS)
                .externalReference("SIMULATED-PAYOUT")
                .bankAccountId(resolvedBankAccountId)   // ✅ bank audit trail
                .bankName(resolvedBankName)              // ✅ denormalized snapshot
                .processedAt(LocalDateTime.now())        // ✅ NEW: Final business timestamp
                .build();

        transactionRepository.save(withdrawalTx);

        // ── 7a. Withdrawal Notification ───────────────────────────────
        notificationService.createNotification(
                user,
                "Withdrawal of ₹" + amount + " processed successfully",
                NotificationType.INFO
        );

        // ── 8. Platform Fee Transaction (unchanged) ─────────────────────
        Transaction feeTx = Transaction.builder()
                .senderWallet(merchantWallet)
                .receiverWallet(null)
                .amount(fee)
                .transactionType(TransactionType.PLATFORM_FEE)
                .status(TransactionStatus.SUCCESS)
                .externalReference("PLATFORM-FEE")
                .build();

        transactionRepository.save(feeTx);
    }

    // ============================================================
    // WITHDRAWAL HISTORY — enriched with bank fields
    // ============================================================
    @Transactional(readOnly = true)
    public List<WithdrawalResponse> getWithdrawalHistory() {

        User user = getLoggedInUser();
        Wallet wallet = walletService.getWalletByUser(user);

        return transactionRepository
                .findBySenderWalletAndTransactionTypeOrderByCreatedAtDesc(
                        wallet,
                        TransactionType.WITHDRAWAL
                )
                .stream()
                .map(tx -> WithdrawalResponse.builder()
                        .amount(tx.getAmount())
                        .status(tx.getStatus().name())
                        .createdAt(tx.getCreatedAt())
                        .reference(tx.getExternalReference())
                        .bankAccountId(tx.getBankAccountId())
                        .bankName(tx.getBankName())
                        .build()
                )
                .toList();
    }

    // ============================================================
    // WALLET SUMMARY (unchanged)
    // ============================================================
    @Transactional(readOnly = true)
    public WalletSummaryResponse getWalletSummary() {

        User user = getLoggedInUser();
        Wallet wallet = walletService.getWalletByUser(user);

        BigDecimal totalReceived = transactionRepository.getTotalReceived(
                wallet,
                TransactionType.INVOICE_PAYMENT
        );

        BigDecimal totalWithdrawn = transactionRepository.getTotalWithdrawn(
                wallet,
                TransactionType.WITHDRAWAL
        );

        BigDecimal totalPlatformFee = transactionRepository.getTotalWithdrawn(
                wallet,
                TransactionType.PLATFORM_FEE
        );

        return WalletSummaryResponse.builder()
                .currentBalance(wallet.getBalance())
                .totalReceived(totalReceived)
                .totalWithdrawn(totalWithdrawn)
                .platformFee(totalPlatformFee)
                .escrowBalance(wallet.getEscrowBalance())
                .build();
    }

    // ── Private Helpers ──────────────────────────────────────────────────

    private User getLoggedInUser() {
        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Masks all but the last 4 digits of an account number for safe display in statements.
     * e.g. "1234567890" → "******7890"
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }
}