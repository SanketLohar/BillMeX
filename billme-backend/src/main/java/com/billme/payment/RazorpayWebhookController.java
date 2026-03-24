package com.billme.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String payload) {

        if (signature == null || !verifySignature(payload, signature)) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        webhookService.processWebhook(payload);

        return ResponseEntity.ok("Webhook processed");

    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256");

            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder generatedSignature = new StringBuilder();
            for (byte b : hash) {
                generatedSignature.append(String.format("%02x", b));
            }

            return java.security.MessageDigest.isEqual(
                    generatedSignature.toString().getBytes(),
                    signature.getBytes()
            );
        } catch (Exception e) {
            return false;
        }
    }

//    // 🔥 Debug endpoint (temporary)
    @PostMapping("/debug-signature")
   public String generateSignature(@RequestBody String payload) throws Exception {

        Mac mac = Mac.getInstance("HmacSHA256");

        SecretKeySpec secretKeySpec =
                new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256");

        mac.init(secretKeySpec);

        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            hexString.append(String.format("%02x", b));
        }

    return hexString.toString();
  }
}