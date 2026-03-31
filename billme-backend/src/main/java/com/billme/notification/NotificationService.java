package com.billme.notification;

import com.billme.notification.dto.NotificationResponse;
import com.billme.user.User;
import com.billme.invoice.Invoice;
import com.billme.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    // Internal helper (participates in its own transaction)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(User user, String message, NotificationType type) {
        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .type(type)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void sendPaymentNotifications(Invoice invoice) {
        log.info("🔔 Processing notifications for Invoice {}", invoice.getInvoiceNumber());

        // 🚀 Capture emails BEFORE synchronization to avoid LazyInitializationException
        String merchantEmail = null;
        String customerEmail = null;
        try {
            if (invoice.getMerchant() != null && invoice.getMerchant().getUser() != null) {
                merchantEmail = invoice.getMerchant().getUser().getEmail();
            }
            if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
                customerEmail = invoice.getCustomer().getUser().getEmail();
            }
        } catch (Exception e) {
            log.warn("Failed to capture emails for notifications: {}", e.getMessage());
        }

        final String finalMerchantEmail = merchantEmail;
        final String finalCustomerEmail = customerEmail;

        // 1. In-App Notifications (Best Effort - Don't fail the payment)
        try {
            if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
                String customerMsg = String.format("Payment of ₹%s for Invoice %s is successful.",
                        invoice.getTotalPayable(), invoice.getInvoiceNumber());
                createNotification(invoice.getCustomer().getUser(), customerMsg, NotificationType.PAYMENT_SUCCESS);
            }

            if (invoice.getMerchant() != null && invoice.getMerchant().getUser() != null) {
                String merchantMsg = String.format("Received ₹%s from %s for Invoice %s.",
                        invoice.getTotalPayable().subtract(invoice.getProcessingFee()),
                        invoice.getResolvedCustomerName(),
                        invoice.getInvoiceNumber());
                createNotification(invoice.getMerchant().getUser(), merchantMsg, NotificationType.PAYMENT_RECEIVED);
            }
        } catch (Exception e) {
            log.error("Failed to create in-app notifications: {}", e.getMessage());
            // 🚨 We do NOT re-throw, so the outer transaction (payment) can commit.
        }

        // 3. Email Synchronization (Safe)

        // 3. Trigger Emails AFTER Transaction Commit (Safe Guard)
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("📧 Attempting to send synchronized emails for Invoice {}", invoice.getInvoiceNumber());
                    
                    // 1. Customer Email
                    // 🚨 REMOVED REDUNDANT SIMPLE EMAIL. Detailed PDF email is sent via InvoiceEmailService.
                    /* if (finalCustomerEmail != null) {
                        try {
                            emailService.sendCustomerPaymentSuccessEmail(invoice);
                        } catch (Exception e) {
                            log.error("🚨 Failed to send customer email after commit: {}", e.getMessage());
                        }
                    } */

                    // 2. Merchant Email
                    if (finalMerchantEmail != null) {
                        try {
                            emailService.sendMerchantPaymentReceivedEmail(invoice);
                        } catch (Exception e) {
                            log.error("🚨 Failed to send merchant email after commit: {}", e.getMessage());
                        }
                    }
                }
            }
        );
    }

    @Transactional
    public void sendRefundRequestNotifications(Invoice invoice) {
        log.info("🔔 Processing refund request notifications for Invoice {}", invoice.getInvoiceNumber());

        try {
            // 1. In-App: Notify Customer
            if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
                String customerMsg = "Refund request submitted for Invoice " + invoice.getInvoiceNumber();
                createNotification(invoice.getCustomer().getUser(), customerMsg, NotificationType.INFO);
            }

            // 2. In-App: Notify Merchant
            if (invoice.getMerchant() != null && invoice.getMerchant().getUser() != null) {
                String merchantMsg = "Refund request received for Invoice " + invoice.getInvoiceNumber();
                createNotification(invoice.getMerchant().getUser(), merchantMsg, NotificationType.REFUND_REQUESTED);
            }
        } catch (Exception e) {
            log.error("Failed to create in-app refund request notifications: {}", e.getMessage());
        }

        // 3. Email Notification is handled separately in RefundService for now because it needs tokens.
        // Keeping it there to avoid complex dependency injection of RefundTokenService here.
    }

    @Transactional
    public void sendRefundSuccessNotifications(Invoice invoice) {
        log.info("🔔 Processing refund completion notifications for Invoice {}", invoice.getInvoiceNumber());

        try {
            // 1. In-App: Notify Customer
            if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
                String customerMsg = "Refund completed for Invoice " + invoice.getInvoiceNumber();
                createNotification(invoice.getCustomer().getUser(), customerMsg, NotificationType.REFUND_COMPLETED);
            }

            // 2. In-App: Notify Merchant
            if (invoice.getMerchant() != null && invoice.getMerchant().getUser() != null) {
                String merchantMsg = "Refund successfully processed for Invoice " + invoice.getInvoiceNumber();
                createNotification(invoice.getMerchant().getUser(), merchantMsg, NotificationType.SUCCESS);
            }
        } catch (Exception e) {
            log.error("Failed to create in-app refund completion notifications: {}", e.getMessage());
        }

        // Email Sync
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
                            emailService.sendRefundCompletedEmail(invoice.getCustomer().getUser().getEmail(), invoice.getInvoiceNumber());
                        }
                    } catch (Exception e) {
                        log.error("🚨 Failed to send refund completed email: {}", e.getMessage());
                    }
                }
            }
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .type(notification.getType())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
    
    @Transactional
    public void markAsRead(Long notificationId, User user) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }
}
