package com.billme.invoice;

import com.billme.invoice.CreateInvoiceRequest;
import com.billme.invoice.dto.CustomerInvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/merchant/invoices")
@RequiredArgsConstructor
public class MerchantInvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<String> createInvoice(
            @RequestBody CreateInvoiceRequest request) {


        invoiceService.createInvoice(request);

        return ResponseEntity.ok("Invoice created successfully");
    }

    @GetMapping
    public ResponseEntity<List<CustomerInvoiceResponse>> getInvoices(Authentication authentication) {
        return ResponseEntity.ok(invoiceService.getMerchantInvoices(authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateInvoice(
            @PathVariable Long id,
            @RequestBody CreateInvoiceRequest request) {

        invoiceService.updateInvoice(id, request);
        return ResponseEntity.ok("Invoice updated successfully. A new payment link has been sent to the customer.");
    }
}