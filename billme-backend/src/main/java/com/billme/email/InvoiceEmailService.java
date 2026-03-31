package com.billme.email;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoicePdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import com.billme.util.NumberToWords;
/**
 * Service for sending invoice emails with PDF attachments.
 * Production-safe: non-blocking + clean email UX
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceEmailService {

    private final JavaMailSender mailSender;
    private final InvoicePdfService pdfService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async
    public void sendInvoiceEmail(Invoice invoice) {

        if (invoice == null) {
            log.error("Cannot send email: Invoice is null");
            return;
        }

        log.info("📧 Sending invoice email: {}", invoice.getInvoiceNumber());

        try {
            // ✅ Generate PDF
            byte[] pdf = pdfService.generateInvoicePdf(invoice);

            // ✅ Create email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(invoice.getResolvedCustomerEmail());

            helper.setSubject(
                    "Invoice " +
                            invoice.getInvoiceNumber() +
                            " from " +
                            invoice.getMerchant().getBusinessName()
            );

            // ✅ Secure Pay Now link
            String payLink = frontendUrl
                    + "/pay-invoice.html?invoiceId="
                    + invoice.getInvoiceNumber()
                    + "&token="
                    + invoice.getPaymentToken();

            // ✅ CLEAN EMAIL TEMPLATE (IMPORTANT FIX)
            String htmlContent =
                    "<div style='font-family: Arial, sans-serif; max-width:600px;margin:auto'>" +
                            "<h2 style='color:#1a73e8'>BillMe</h2>" +
                            "<p>You have received an invoice from <b>" +
                            invoice.getMerchant().getBusinessName() +
                            "</b></p>" +
                            "<p><b>Invoice Number:</b> " + invoice.getInvoiceNumber() + "</p>" +
                            "<p><b>Amount Due:</b> ₹" + invoice.getTotalPayable() + "</p>" +
                            "<br>" +
                            "<a href='" + payLink + "' " +
                            "style='padding:12px 24px;background:#1a73e8;color:white;text-decoration:none;border-radius:6px'>" +
                            "Pay Now</a>" +
                            "<br><br>" +
                            "<p>Please find the invoice attached.</p>" +
                            "</div>";

            helper.setText(htmlContent, true);

            // ✅ Attach PDF
            helper.addAttachment(
                    "invoice-" + invoice.getInvoiceNumber() + ".pdf",
                    () -> new ByteArrayInputStream(pdf)
            );

            // ✅ Send email
            mailSender.send(message);

            log.info("✅ Email sent successfully: {}", invoice.getInvoiceNumber());

        } catch (Exception e) {
            log.error("❌ [ASYNC EMAIL ERROR] Failed to send invoice email for #{}: {}", 
                    invoice.getInvoiceNumber(), e.getMessage(), e);
        }
    }

    @Async
    public void sendPaymentSuccessEmail(Invoice invoice) {
        log.info("EMAIL METHOD TRIGGERED");
        if (invoice == null) {
            log.error("Cannot send email: Invoice is null");
            return;
        }

        log.info("📧 Sending payment receipt email: {}", invoice.getInvoiceNumber());

        try {
            // ✅ Generate UPDATED PAID PDF
            byte[] pdf = pdfService.generateInvoicePdf(invoice);
            log.info("PDF SIZE: {}", pdf.length);

            // ✅ Create email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(invoice.getResolvedCustomerEmail());

            helper.setSubject("Payment Receipt: Invoice " + invoice.getInvoiceNumber());

            // ✅ CLEAN RECEIPT TEMPLATE (NO PAY NOW BUTTON)
            String htmlContent =
                    "<div style='font-family: Arial, sans-serif; max-width:600px;margin:auto'>" +
                            "<h2 style='color:#34a853'>Payment Successful</h2>" +
                            "<p>Your payment for invoice <b>" + invoice.getInvoiceNumber() + "</b> has been successfully received.If you want refund then please visit your own dashboard and Request Refund on the Bill Me </p>" +
                            "<p><b>Amount Paid:</b> ₹" + invoice.getTotalPayable() + "</p>" +
                            "<p><b>Paid Date:</b> " + (invoice.getPaidAt() != null ? invoice.getPaidAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) : "N/A") + "</p>" +
                            "<br>" +
                            "<p>Please find your updated receipt attached for your records.</p>" +
                            "</div>";

            helper.setText(htmlContent, true);

            // ✅ Attach PDF
            helper.addAttachment(
                    "paid-invoice-" + invoice.getInvoiceNumber() + ".pdf",
                    () -> new ByteArrayInputStream(pdf)
            );

            // ✅ Send email
            mailSender.send(message);

            log.info("✅ Payment success email sent successfully: {}", invoice.getInvoiceNumber());

        } catch (Exception e) {
            log.error("❌ [ASYNC EMAIL ERROR] Failed to send payment success email for #{}: {}", 
                    invoice.getInvoiceNumber(), e.getMessage(), e);
        }
    }
}