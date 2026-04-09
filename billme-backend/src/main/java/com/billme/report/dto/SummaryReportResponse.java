package com.billme.report.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SummaryReportResponse {
    private String range;
    private List<DataPoint> revenueTrend;
    private List<DataPoint> withdrawalTrend;
    private BigDecimal totalRevenue;
    // Convenience fields expected by some consumers of /summary
    private BigDecimal grossProfit;
    private BigDecimal totalWithdrawals;
    private BigDecimal withdrawals;
    private BigDecimal netCashFlow;
    private BigDecimal unknownCogsImpact;
    private String transparencyNote;
}
