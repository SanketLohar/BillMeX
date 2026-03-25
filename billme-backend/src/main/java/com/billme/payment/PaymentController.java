package com.billme.payment;

import com.billme.invoice.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final InvoiceService invoiceService;

    @PostMapping("/create-order/{invoiceId}")
    public ResponseEntity<String> createOrder(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) String token) {

        String orderId = invoiceService.createRazorpayOrder(invoiceId, token);

        return ResponseEntity.ok(orderId);
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verifyPayment(@RequestBody com.billme.payment.dto.VerifyRazorpayRequest request) {
        invoiceService.verifyRazorpayPayment(request);
        return ResponseEntity.ok("Payment verified successfully");
    }

    @PostMapping("/retry/{invoiceId}")
    public ResponseEntity<String> retryPayment(@PathVariable Long invoiceId) {
        String orderId = invoiceService.retryPayment(invoiceId);
        return ResponseEntity.ok(orderId);
    }
}