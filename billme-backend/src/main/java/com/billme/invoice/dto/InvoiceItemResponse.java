package com.billme.invoice.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InvoiceItemResponse {

    private Long productId;
    private String productName;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal gstRate;

    private BigDecimal gstAmount;

    private BigDecimal cgstAmount;

    private BigDecimal sgstAmount;

    private BigDecimal igstAmount;

    private BigDecimal totalPrice;
}