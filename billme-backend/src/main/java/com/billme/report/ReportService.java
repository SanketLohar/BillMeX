package com.billme.report;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceItem;
import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.ReportTransactionResponse;
import com.billme.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReportTransactionResponse> getTransactions(
            MerchantProfile merchant, 
            org.springframework.data.domain.Pageable pageable) {
        
        org.springframework.data.domain.Page<Invoice> invoices = 
                invoiceRepository.findByMerchant_User_Id(merchant.getUser().getId(), pageable);
        
        return invoices.map(invoice -> {
            BigDecimal totalGst = invoice.getItems().stream()
                .map(InvoiceItem::getGstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return ReportTransactionResponse.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerName(invoice.getResolvedCustomerName())
                .amount(invoice.getAmount())
                .gst(totalGst)
                .paymentMethod(invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : "PENDING")
                .date(invoice.getIssuedAt())
                .build();
        });
    }

    @Transactional(readOnly = true)
    public List<ReportTransactionResponse> getAllTransactions(MerchantProfile merchant) {
        List<Invoice> invoices = invoiceRepository.findByMerchant_User_Id(merchant.getUser().getId());
        return invoices.stream().map(invoice -> {
            BigDecimal totalGst = invoice.getItems().stream()
                .map(InvoiceItem::getGstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return ReportTransactionResponse.builder()
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerName(invoice.getResolvedCustomerName())
                .amount(invoice.getAmount())
                .gst(totalGst)
                .paymentMethod(invoice.getPaymentMethod() != null ? invoice.getPaymentMethod().name() : "PENDING")
                .date(invoice.getIssuedAt())
                .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getPaymentMethods(MerchantProfile merchant) {
        List<Invoice> invoices = invoiceRepository.findByMerchant_User_IdAndStatus(
                merchant.getUser().getId(), 
                com.billme.invoice.InvoiceStatus.PAID
        );
        
        java.util.Map<String, Long> distribution = new java.util.HashMap<>();
        distribution.put("UPI", 0L);
        distribution.put("FACE_PAY", 0L);
        distribution.put("CARD", 0L);

        for (Invoice inv : invoices) {
            String method = inv.getPaymentMethod() != null ? inv.getPaymentMethod().name() : "UPI";
            distribution.put(method, distribution.getOrDefault(method, 0L) + 1);
        }
        return distribution;
    }
}
