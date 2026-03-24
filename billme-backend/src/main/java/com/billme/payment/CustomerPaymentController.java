package com.billme.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/invoices")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final FacePayService facePayService;

    @PostMapping("/{id}/pay/face")
    public ResponseEntity<String> payInvoice(
            @PathVariable Long id,
            @RequestBody FacePayRequest request) {

        Object embedding = request.getEmbedding();

        // 🔥 DEBUG LOG (IMPORTANT)
        if (embedding != null) {

        }

        String result = facePayService.payInvoice(id, embedding);
        return ResponseEntity.ok(result);
    }
}