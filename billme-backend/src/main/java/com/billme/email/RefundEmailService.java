package com.billme.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.backend.url}")
    private String backendUrl;


    public void sendRefundRequestEmail(
            String merchantEmail,
            String invoiceNumber,
            String customerName,
            BigDecimal amount,
            String paymentMethod,
            String approveToken,
            String rejectToken) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(merchantEmail);
            helper.setSubject("Refund Request: " + invoiceNumber);

            String approveUrl = backendUrl + "/api/refund/email/approve/" + approveToken;
            String rejectUrl = backendUrl + "/api/refund/email/reject/" + rejectToken;


            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <h2>Refund Request Received</h2>
                    <p>A customer has requested a refund for an invoice.</p>
                    <ul>
                        <li><strong>Invoice Number:</strong> %s</li>
                        <li><strong>Customer:</strong> %s</li>
                        <li><strong>Amount:</strong> ₹%.2f</li>
                        <li><strong>Payment Method:</strong> %s</li>
                    </ul>
                    <p>Please approve or reject this request:</p>
                    <div style="margin-top: 20px;">
                        <a href="%s" style="background-color: #28a745; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px; margin-right: 10px;">Approve Refund</a>
                        <a href="%s" style="background-color: #dc3545; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Reject Refund</a>
                    </div>
                </body>
                </html>
            """, invoiceNumber, customerName, amount, paymentMethod, approveUrl, rejectUrl);

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            log.error("Failed to send refund request email to {}: {}", merchantEmail, e.getMessage());
        }
    }

    public void sendRefundCompletedEmail(String customerEmail, String invoiceNumber) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(customerEmail);
            helper.setSubject("Refund Completed");

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <h2>Refund Processed</h2>
                    <p>Your refund for Invoice <strong>%s</strong> has been successfully processed.</p>
                    <p>The funds will be credited to your original payment method shortly.</p>
                </body>
                </html>
            """, invoiceNumber);

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (MessagingException e) {
            log.error("Failed to send refund completed email to {}: {}", customerEmail, e.getMessage());
        }
    }
    
    public void sendRefundRejectedEmail(String customerEmail, String invoiceNumber, String reason) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(customerEmail);
            helper.setSubject("Refund Request Update - Invoice #" + invoiceNumber);

            String htmlContent = String.format("""
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <div style="max-width: 600px; margin: auto; border: 1px solid #ddd; padding: 20px;">
                        <h2 style="color: #F44336;">Refund Request Update</h2>
                        <p>Hello,</p>
                        <p>Your refund request for Invoice <strong>#%s</strong> has been reviewed.</p>
                        <div style="background-color: #fce4e4; padding: 15px; border-radius: 5px; border-left: 5px solid #F44336;">
                            <p><strong>Status:</strong> REJECTED</p>
                            <p><strong>Reason:</strong> %s</p>
                        </div>
                        <p>If you have any questions, please contact the merchant directly.</p>
                        <hr>
                        <p>Thank you for using BillMe.</p>
                    </div>
                </body>
                </html>
            """, invoiceNumber, reason != null ? reason : "Standard merchant policy.");

            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("✅ [EMAIL SENT] Refund rejected email sent to {}", customerEmail);
        } catch (MessagingException e) {
            log.error("🚨 [EMAIL ERROR] Failed to send refund rejected email: {}", e.getMessage());
        }
    }
}
