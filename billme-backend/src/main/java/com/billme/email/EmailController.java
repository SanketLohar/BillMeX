package com.billme.email;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping("/test")
    public String sendTestEmail(@RequestParam String to) {

        emailService.sendEmail(
                to,
                "BillMe Test Email",
                "Hello from BillMe backend 🚀"
        );

        return "Email sent successfully!";
    }
}