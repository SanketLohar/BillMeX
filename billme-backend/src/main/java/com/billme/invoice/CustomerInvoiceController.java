package com.billme.invoice;

import com.billme.invoice.dto.CustomerInvoiceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/invoices")
@RequiredArgsConstructor
public class CustomerInvoiceController {

    private final InvoiceService invoiceService;

    // ==========================================
    // GET ALL CUSTOMER INVOICES
    // ==========================================
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<CustomerInvoiceResponse> getAllInvoices(Authentication authentication) {
        return invoiceService.getCustomerInvoices(authentication.getName());
    }

    // ==========================================
    // GET PENDING INVOICES
    // ==========================================
    @GetMapping("/pending")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<CustomerInvoiceResponse> getPending(Authentication authentication) {
        return invoiceService.getPendingInvoices(authentication.getName());
    }

    // ==========================================
    // GET SINGLE INVOICE
    // ==========================================
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerInvoiceResponse getInvoice(
            @PathVariable Long id,
            Authentication authentication) {

        return invoiceService.getInvoiceById(id, authentication.getName());
    }
}