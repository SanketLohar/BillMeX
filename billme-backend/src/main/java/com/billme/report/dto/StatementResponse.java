package com.billme.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StatementResponse {
    private String merchantName;
    private String gstin;
    private String email;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    
    private List<StatementTransaction> transactions;
}
