package com.billme.scheduler;

import com.billme.email.InvoiceEmailService;
import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceReminderService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceEmailService invoiceEmailService;

    @Scheduled(cron = "0 0 8 * * ?") // Every day at 8 AM
    public void sendInvoiceReminders() {
        log.info("Starting scheduled job: sendInvoiceReminders");

        LocalDateTime now = LocalDateTime.now();
        List<Invoice> allInvoices = invoiceRepository.findAll();

        for (Invoice invoice : allInvoices) {
            
            if (invoice.getStatus() == InvoiceStatus.UNPAID && invoice.getIssuedAt() != null) {
                
                long daysSinceIssue = java.time.Duration.between(invoice.getIssuedAt(), now).toDays();

                if (daysSinceIssue == 2 || daysSinceIssue == 5) {
                    try {
                        invoiceEmailService.sendInvoiceEmail(invoice);
                        log.info("Reminder sent for Invoice ID: {} after {} days", invoice.getId(), daysSinceIssue);
                    } catch (Exception e) {
                        log.error("Failed to send reminder for Invoice ID: {}", invoice.getId(), e);
                    }
                }
            }
        }
        
        log.info("Completed scheduled job: sendInvoiceReminders");
    }
}
