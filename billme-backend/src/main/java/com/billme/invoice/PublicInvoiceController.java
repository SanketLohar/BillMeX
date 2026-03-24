package com.billme.invoice;

import com.billme.invoice.dto.PublicInvoiceResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/invoices")
public class PublicInvoiceController {

    private final InvoiceService invoiceService;

    public PublicInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{invoiceNumber}")
    public PublicInvoiceResponse getInvoice(
            @PathVariable String invoiceNumber,
            @RequestParam String token
    ) {
        return invoiceService.getPublicInvoice(invoiceNumber, token);
    }
}