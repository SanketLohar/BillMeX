package com.billme.report;

import com.billme.report.util.DateRangeUtils;


import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.DataPoint;
import com.billme.report.dto.SummaryReportResponse;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.WalletRepository;
import com.billme.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final ProfitLossService profitLossService;
    private final WalletRepository walletRepository;

    public SummaryReportResponse getMerchantSummary(MerchantProfile merchant, String range, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating analytics summary for merchant: {}. Range: {}, Start: {}, End: {}", 
                merchant.getId(), range, startDate, endDate);
        
        DateRangeUtils.DateRange resolvedDates = DateRangeUtils.resolveRange(range, startDate, endDate);
        LocalDateTime start = resolvedDates.getStartDate();
        LocalDateTime end = resolvedDates.getEndDate();


        List<Object[]> revenueData;
        List<Object[]> withdrawalData;

        // 🛡️ FINANCIAL CONSOLIDATION — SOURCE OF TRUTH
        // Revenue: Strictly Invoices (PAID) — Using paid_at + IST
        // Withdrawals: Strictly Transactions (TYPE=WITHDRAWAL, STATUS=SUCCESS) — Using processed_at + IST
        Long walletId = (merchant.getUser().getWallet() != null) ? merchant.getUser().getWallet().getId() : null;
        if (walletId == null) {
            walletId = walletRepository.findByUser(merchant.getUser())
                    .map(w -> w.getId())
                    .orElse(null);
        }

        if (walletId == null) {
            log.warn("Analytics: Wallet not found for merchant {}, returning empty trends.", merchant.getId());
            return SummaryReportResponse.builder()
                    .range(range)
                    .totalRevenue(BigDecimal.ZERO)
                    .grossProfit(BigDecimal.ZERO)
                    .totalWithdrawals(BigDecimal.ZERO)
                    .withdrawals(BigDecimal.ZERO)
                    .netCashFlow(BigDecimal.ZERO)
                    .unknownCogsImpact(BigDecimal.ZERO)
                    .transparencyNote("")
                    .revenueTrend(List.of())
                    .withdrawalTrend(List.of())
                    .build();
        }

        // Determine bucket grouping based on the range (or the spread if dates provided)
        String resolvedRange = range != null ? range.toLowerCase() : "daily";
        
        switch (resolvedRange) {
            case "weekly":
                revenueData = invoiceRepository.findWeeklyRevenue(merchant.getId(), start, end);
                withdrawalData = transactionRepository.findWeeklyWithdrawalTrends(walletId, start, end);
                break;
            case "monthly":
                revenueData = invoiceRepository.findMonthlyRevenue(merchant.getId(), start, end);
                withdrawalData = transactionRepository.findMonthlyWithdrawalTrends(walletId, start, end);
                break;
            case "yearly":
                revenueData = invoiceRepository.findYearlyRevenue(merchant.getId(), start, end);
                withdrawalData = transactionRepository.findYearlyWithdrawalTrends(walletId, start, end);
                break;
            case "quarterly":
                revenueData = invoiceRepository.findMonthlyRevenue(merchant.getId(), start, end);
                withdrawalData = transactionRepository.findMonthlyWithdrawalTrends(walletId, start, end);
                break;
            default: // daily
                revenueData = invoiceRepository.findDailyRevenue(merchant.getId(), start, end);
                withdrawalData = transactionRepository.findDailyWithdrawalTrends(walletId, start, end);
                break;
        }

        List<DataPoint> revenueTrend = mapToDataPoints(revenueData);
        List<DataPoint> withdrawalTrend = mapToDataPoints(withdrawalData);

        // 🛡️ ARITHMETIC IDENTITY: KPI Card Totals must match exactly the sum of Chart Buckets
        BigDecimal totalRevenue = revenueTrend.stream().map(DataPoint::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWithdrawals = withdrawalTrend.stream().map(DataPoint::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Reconciliation with P&L (Detailed audit).
        // P&L service intentionally limits range to <= 366 days.
        // For larger analytics windows (e.g. yearly), compute P&L on the trailing 366-day window
        // to avoid breaking /summary with 400 errors.
        LocalDateTime pnlStart = start;
        LocalDateTime pnlEnd = end;
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            pnlStart = end.minusDays(366);
            log.warn("Summary range exceeds 366 days for merchant {}. Limiting P&L reconciliation to {} -> {}",
                    merchant.getId(), pnlStart, pnlEnd);
        }

        var pnl = profitLossService.calculateProfitLoss(merchant, pnlStart, pnlEnd);
        
        return SummaryReportResponse.builder()
                .range(range)
                .revenueTrend(revenueTrend)
                .withdrawalTrend(withdrawalTrend)
                .totalRevenue(totalRevenue)
                .grossProfit(pnl.getGrossProfit())
                .totalWithdrawals(totalWithdrawals)
                .withdrawals(totalWithdrawals)
                .netCashFlow(totalRevenue.subtract(totalWithdrawals))
                .unknownCogsImpact(pnl.getUnknownCogsRevenue())
                .transparencyNote(pnl.getTransparencyNote())
                .build();
    }

    private List<DataPoint> mapToDataPoints(List<Object[]> data) {
        return data.stream()
                .map(obj -> new DataPoint(obj[0].toString(), new BigDecimal(obj[1].toString())))
                .collect(Collectors.toList());
    }



}
