package com.billme.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProfitLossResponse {
    private BigDecimal totalRevenue;
    private BigDecimal totalCogs;
    private BigDecimal grossProfit;
    private BigDecimal netProfit; // Gross Profit minus processing fees
    private BigDecimal totalProcessingFees;

    // 🛡️ Audit Transparency: Revenue excluded from Profit calculation due to missing Cost profiles
    private BigDecimal unknownCogsRevenue;
    private Long unknownCogsCount;
    private String transparencyNote;
}
