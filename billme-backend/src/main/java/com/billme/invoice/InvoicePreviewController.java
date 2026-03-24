package com.billme.invoice;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoicePreviewController {

    private final InvoiceService invoiceService;

    // ==========================================
    // HTML PREVIEW (Browser View)
    // ==========================================
    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> previewInvoice(@PathVariable Long id) {

        byte[] pdf = invoiceService.generateInvoicePdf(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=invoice_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(new ByteArrayInputStream(pdf)));
    }

    // ==========================================
    // DOWNLOAD PDF
    // ==========================================
    @GetMapping("/{id}/pdf")
    public ResponseEntity<InputStreamResource> downloadInvoice(@PathVariable Long id) {

        byte[] pdf = invoiceService.generateInvoicePdf(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(new ByteArrayInputStream(pdf)));
    }
}