package com.billme.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        String resetLink = frontendUrl + "/reset-password.html?token=" + rawToken;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("hello@billme.com");
            helper.setTo(toEmail);
            helper.setSubject("Reset your BillMe password");

            String htmlContent = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                    "<h2>Hello,</h2>" +
                    "<p>We received a request to reset your password for your BillMe account.</p>" +
                    "<p>Click the button below to set a new password:</p>" +
                    "<a href=\"" + resetLink + "\" style=\"" +
                    "display:inline-block;" +
                    "padding:12px 20px;" +
                    "background:#2563eb;" +
                    "color:white;" +
                    "border-radius:6px;" +
                    "text-decoration:none;" +
                    "font-weight:500;" +
                    "\">Reset Password</a>" +
                    "<p style=\"margin-top: 20px; color: #5f6368;\">This link will expire in 15 minutes.</p>" +
                    "<p style=\"color: #5f6368;\">If you did not request this, you can safely ignore this email.</p>" +
                    "</div>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }
}
