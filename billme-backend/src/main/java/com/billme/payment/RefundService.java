package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.invoice.PaymentMethod;
import com.billme.payment.dto.MerchantRefundResponse;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.TransactionRepository;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.user.Role;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import com.billme.wallet.WalletService;
import com.billme.notification.NotificationService;
import com.billme.notification.NotificationType;
import com.billme.email.RefundEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.billme.repository.UserRepository;
import com.billme.repository.WalletRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final InvoiceRepository invoiceRepository;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;
    private final RazorpayService razorpayService;
    private final UserRepository userRepository;
    private final RefundTokenService refundTokenService;
    private final NotificationService notificationService;
    private final RefundEmailService refundEmailService;

    @Transactional
    public void validateAndProcessRefund(Long invoiceId, Long merchantId, boolean approve) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔒 Verify merchant ownership
        if (!invoice.getMerchant().getId().equals(merchantId)) {
            throw new RuntimeException("Security violation: Merchant ID mismatch");
        }

        // 🔒 Ensure correct status for email approval
        if (invoice.getStatus() != InvoiceStatus.REFUND_REQUESTED) {
            throw new RuntimeException("Invoice is not in REFUND_REQUESTED state. Current status: " + invoice.getStatus());
        }

        if (approve) {
            // 🔒 Second-level check: Verify refund window again before processing
            if (invoice.getRefundWindowExpiry() == null ||
                    invoice.getRefundWindowExpiry().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Refund window expired. This transaction is no longer eligible for refund.");
            }
            refundInvoice(invoiceId);
        } else {
            rejectRefund(invoiceId);
        }
    }

    @Transactional
    public void requestRefund(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw new RuntimeException("Only paid invoices can be refunded");
        }

        // 🔒 Enforce refund window validation AT THE SOURCE
        if (invoice.getRefundWindowExpiry() != null &&
                invoice.getRefundWindowExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refund window expired. This invoice is no longer eligible for a refund.");
        }

        invoice.setStatus(InvoiceStatus.REFUND_REQUESTED);
        invoiceRepository.save(invoice);

        // 🔔 Centralized Notifications (In-App)
        notificationService.sendRefundRequestNotifications(invoice);

        User merchantUser = invoice.getMerchant().getUser();

        // Send Email
        String approveToken = refundTokenService.generateRefundToken(invoice.getId(), invoice.getMerchant().getId());
        String rejectToken = refundTokenService.generateRefundToken(invoice.getId(), invoice.getMerchant().getId());
        
        refundEmailService.sendRefundRequestEmail(
                merchantUser.getEmail(),
                invoice.getInvoiceNumber(),
                invoice.getCustomer() != null ? invoice.getCustomer().getName() : "Customer",
                invoice.getTotalPayable(),
                invoice.getPaymentMethod().name(),
                approveToken,
                rejectToken
        );
    }

    @Transactional
    public void rejectRefund(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.REFUND_REQUESTED) {
            throw new RuntimeException("Invoice is not in refund requested status");
        }

        invoice.setStatus(InvoiceStatus.REFUND_REJECTED);
        invoiceRepository.save(invoice);

        // Notify Customer
        if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
            String msg = "Your refund request for Invoice " + invoice.getInvoiceNumber() + " was rejected by the merchant.";
            notificationService.createNotification(invoice.getCustomer().getUser(), msg, NotificationType.ERROR);
            refundEmailService.sendRefundRejectedEmail(invoice.getCustomer().getUser().getEmail(), invoice.getInvoiceNumber(), "Merchant policy / already processed.");
        }

        // Notify Merchant
        String merchantMsg = "You rejected the refund request for Invoice " + invoice.getInvoiceNumber();
        notificationService.createNotification(invoice.getMerchant().getUser(), merchantMsg, NotificationType.INFO);
    }

    @Transactional
    public void refundInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 1. Idempotency & Eligibility Guard
        if (invoice.getStatus() == InvoiceStatus.REFUNDED) {
            log.warn("⚠️ [IDEMPOTENCY] Invoice {} already refunded. Skipping.", invoice.getInvoiceNumber());
            return;
        }

        if (invoice.getStatus() != InvoiceStatus.PAID && invoice.getStatus() != InvoiceStatus.REFUND_REQUESTED) {
            throw new RuntimeException("Only paid or refund-requested invoices can be refunded");
        }

        // 2. Window Check
        if (invoice.getRefundWindowExpiry() == null ||
                invoice.getRefundWindowExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refund window expired");
        }

        BigDecimal amount = invoice.getTotalPayable();
        String ref = "REFUND-" + invoice.getInvoiceNumber();

        // 3. Branch Refund Logic by Payment Method
        if (invoice.getPaymentMethod() == PaymentMethod.FACE_PAY) {
            log.info("💳 [FACE_PAY REFUND] Reversing internal wallet transfer");
            walletService.updateBalanceRefundInternal(
                    invoice.getMerchant().getUser(),
                    invoice.getCustomer().getUser(),
                    amount,
                    ref
            );
        } else if (invoice.getPaymentMethod() == PaymentMethod.UPI_PAY || invoice.getPaymentMethod() == PaymentMethod.CARD) {
            log.info("🌐 [EXTERNAL REFUND] Attempting Razorpay refund for invoice {}", invoice.getInvoiceNumber());
            
            // 🔒 SAFETY: External API call happens BEFORE wallet deduction
            razorpayService.refundPayment(
                    invoice.getTransaction().getExternalReference(),
                    amount
            );

            log.info("✅ [EXTERNAL REFUND SUCCESS] Razorpay refund successful. Proceeding to merchant escrow deduction.");
            walletService.updateBalanceRefundExternal(
                    invoice.getMerchant().getUser(),
                    amount,
                    ref
            );
        } else {
            throw new RuntimeException("Unsupported payment method for refund: " + invoice.getPaymentMethod());
        }

        // 4. Create Refund Ledger Transaction
        Transaction refundTx = Transaction.builder()
                .senderWallet(null)
                .receiverWallet(null)
                .invoice(invoice)
                .amount(amount)
                .transactionType(TransactionType.REFUND)
                .status(TransactionStatus.SUCCESS)
                .externalReference(ref)
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(refundTx);

        // 5. Finalize Invoice Status
        invoice.setStatus(InvoiceStatus.REFUNDED);
        invoiceRepository.saveAndFlush(invoice);

        log.info("✅ [REFUND SUCCESS] Invoice: {} | Method: {}", invoice.getInvoiceNumber(), invoice.getPaymentMethod());

        // 6. Notifications
        notificationService.sendRefundSuccessNotifications(invoice);
    }

    @Transactional(readOnly = true)
    public List<MerchantRefundResponse> getMerchantRefundHistory(String merchantEmail) {

        User user = userRepository.findByEmail(merchantEmail)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        if (user.getRole() != Role.MERCHANT) {
            throw new RuntimeException("Access denied");
        }

        Wallet wallet = walletService.getWalletByUser(user);

        return transactionRepository.findMerchantRefundHistory(wallet.getId());
    }

}