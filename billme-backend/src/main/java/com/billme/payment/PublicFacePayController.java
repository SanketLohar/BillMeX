package com.billme.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/pay")
@RequiredArgsConstructor
public class PublicFacePayController {

    private final FacePayService facePayService;

    @PostMapping("/face")
    public ResponseEntity<String> payInvoice(@RequestBody FacePayRequest request) {

        String result = facePayService.payInvoicePublic(request);

        return ResponseEntity.ok(result);
    }
}