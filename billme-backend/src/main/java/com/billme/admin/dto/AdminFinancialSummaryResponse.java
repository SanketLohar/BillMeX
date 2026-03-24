package com.billme.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AdminFinancialSummaryResponse {

    private BigDecimal totalRevenue;

    private BigDecimal totalPlatformFees;

    private BigDecimal totalRefundAmount;

    private BigDecimal totalWithdrawals;

    private BigDecimal totalLockedAmount;

    // Counts
    private long totalMerchants;
    private long totalCustomers;
    private long totalTransactions;

    // Analytics (Trend)
    private java.util.List<String> monthlyLabels;
    private java.util.List<Long> monthlyTransactions;
    
    // Distribution
    private java.util.Map<String, Long> paymentMethods;

    // Growth
    private java.util.List<String> growthLabels;
    private java.util.List<Long> merchantGrowth;

    // Activity
    private java.util.List<AdminActivityDTO> recentActivity;

    @Data
    @AllArgsConstructor
    public static class AdminActivityDTO {
        private String user;
        private String action;
        private String status;
        private String time;
    }
}