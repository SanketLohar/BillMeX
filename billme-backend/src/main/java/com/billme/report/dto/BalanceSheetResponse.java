package com.billme.report.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class BalanceSheetResponse {
    private BigDecimal totalRevenue;
    private BigDecimal totalRefunds;
    private BigDecimal walletBalance;
    private BigDecimal platformFees;
    private BigDecimal netEarnings;
    private BigDecimal withdrawals;
}
