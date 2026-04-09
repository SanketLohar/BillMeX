package com.billme.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class StatementTransaction {
    private String transactionId;
    private String invoiceNumber;
    private String type; // e.g. PAYMENT, REFUND, ESCROW_RELEASE
    private BigDecimal amount;
    private BigDecimal closingBalance;
    private LocalDateTime timestamp;
    private String status;
}
