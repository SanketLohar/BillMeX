package com.billme.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {

    private String invoiceNumber;
    private String merchantName;
    private String customerName;

    private BigDecimal amount;

    private String paymentMethod;

    private String status;

    private LocalDateTime paidAt;
}