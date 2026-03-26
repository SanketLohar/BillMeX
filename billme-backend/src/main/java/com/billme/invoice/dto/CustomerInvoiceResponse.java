package com.billme.invoice.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CustomerInvoiceResponse {

    private Long invoiceId;
    private String invoiceNumber;
    private BigDecimal amount;
    private String status;
    private String paymentMethod;
    private LocalDateTime issuedAt;
    private LocalDateTime dueDate;
    private LocalDateTime paidAt;
    private List<InvoiceItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal processingFee;
    private BigDecimal totalPayable;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private String paymentToken;
    private String customerName;
    private String customerEmail;
    private LocalDateTime refundWindowExpiry;
}