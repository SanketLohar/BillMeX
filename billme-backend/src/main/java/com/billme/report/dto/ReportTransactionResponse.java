package com.billme.report.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReportTransactionResponse {
    private String invoiceNumber;
    private String customerName;
    private BigDecimal amount;
    private BigDecimal gst;
    private String paymentMethod;
    private LocalDateTime date;
}
