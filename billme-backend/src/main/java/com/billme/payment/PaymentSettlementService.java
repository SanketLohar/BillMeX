package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.repository.WalletRepository;
import com.billme.transaction.LedgerEntryType;
import com.billme.transaction.LedgerService;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.wallet.Wallet;
import com.billme.notification.NotificationService;
import com.billme.email.InvoiceEmailService;
import org.springframework.context.ApplicationEventPublisher;
import com.billme.invoice.events.InvoicePaidEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.billme.invoice.PaymentMethod;
import com.billme.wallet.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSettlementService {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final InvoiceEmailService invoiceEmailService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void settlePayment(Long invoiceId, String externalRef) {
        Invoice invoice = invoiceRepository.findWithDetailsById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with full details"));

        log.info("🚀 [SETTLEMENT START] Invoice: {} | Method: {} | Ref: {}", 
                invoice.getInvoiceNumber(), invoice.getPaymentMethod(), externalRef);

        // 1. Idempotency & Safety Guards
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.warn("⚠️ [IDEMPOTENCY] Invoice {} already paid. Skipping settlement.", invoice.getInvoiceNumber());
            return;
        }

        BigDecimal amount = (invoice.getTotalPayable() != null) ? invoice.getTotalPayable() : invoice.getAmount();
        BigDecimal processingFee = (invoice.getProcessingFee() != null) ? invoice.getProcessingFee() : BigDecimal.ZERO;
        BigDecimal merchantSettlement = amount.subtract(processingFee);

        // 2. Branch Settlement by Payment Method
        if (invoice.getPaymentMethod() == PaymentMethod.FACE_PAY) {
            log.info("💳 [FACE_PAY] Executing internal wallet transfer");
            walletService.updateBalanceInternal(
                    invoice.getCustomer().getUser(),
                    invoice.getMerchant().getUser(),
                    amount,
                    externalRef
            );
        } else if (invoice.getPaymentMethod() == PaymentMethod.UPI_PAY || invoice.getPaymentMethod() == PaymentMethod.CARD) {
            log.info("🌐 [{}] Executing external settlement (No customer debit)", invoice.getPaymentMethod());
            walletService.updateBalanceExternal(
                    invoice.getMerchant().getUser(),
                    amount,
                    externalRef
            );
        } else {
            throw new IllegalStateException("Unsupported payment method for settlement: " + invoice.getPaymentMethod());
        }

        TransactionType type = (invoice.getPaymentMethod() == PaymentMethod.FACE_PAY) 
                ? TransactionType.FACE_PAY 
                : TransactionType.UPI_PAY;

        // 3. Create Transaction Ledger
        Wallet receiverWallet = walletService.getWalletByUser(invoice.getMerchant().getUser());
        Wallet senderWallet = (invoice.getCustomer() != null) 
                ? walletService.getWalletByUser(invoice.getCustomer().getUser()) 
                : null;

        Transaction ledgerTransaction = Transaction.builder()
                .senderWallet(senderWallet) 
                .receiverWallet(receiverWallet)
                .amount(amount)
                .invoiceAmount(amount)
                .processingFee(processingFee)
                .merchantSettlement(merchantSettlement)
                .transactionType(type)
                .status(TransactionStatus.SUCCESS)
                .externalReference(externalRef)
                .createdAt(LocalDateTime.now())
                .invoice(invoice)
                .build();

        transactionRepository.save(ledgerTransaction);

        // 4. Update Invoice Status
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setTransaction(ledgerTransaction);
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setPaymentInProgress(false);
        invoice.setRefundWindowExpiry(LocalDateTime.now().plusDays(3));

        invoiceRepository.saveAndFlush(invoice);

        log.info("✅ [SETTLEMENT SUCCESS] Invoice: {} | Method: {}", invoice.getInvoiceNumber(), invoice.getPaymentMethod());

        // 5. Notifications
        notificationService.sendPaymentNotifications(invoice);
        
        // 6. Trigger Payment Success Email
        invoiceEmailService.sendPaymentSuccessEmail(invoice);
        
        // 7. Fire Async Inventory Deductions
        eventPublisher.publishEvent(new InvoicePaidEvent(this, invoice));
    }
}
