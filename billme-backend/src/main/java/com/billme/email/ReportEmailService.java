package com.billme.email;

import com.billme.merchant.MerchantProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class ReportEmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendReportEmail(MerchantProfile merchant, byte[] excelFile) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(merchant.getUser().getEmail());
            helper.setSubject("Your Transactions Report - BillMe");
            
            helper.setText("Hello " + merchant.getBusinessName() + ",\n\nPlease find your requested transactions report attached.\n\nThank you,\nBillMe");

            helper.addAttachment("transactions_report.xlsx", () -> new java.io.ByteArrayInputStream(excelFile));

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Failed to send report email", e);
        }
    }
}
