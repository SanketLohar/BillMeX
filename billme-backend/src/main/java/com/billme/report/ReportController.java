package com.billme.report;

import com.billme.report.util.DateRangeUtils;


import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.ProfitLossResponse;
import com.billme.report.dto.StatementResponse;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/merchant/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ProfitLossService profitLossService;
    private final StatementService statementService;
    private final ReportExcelService reportExcelService;
    private final ReportPdfService reportPdfService;
    private final BalanceSheetService balanceSheetService;
    private final ReportService reportService;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    private MerchantProfile getLoggedInMerchant() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return merchantProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<com.billme.report.dto.BalanceSheetResponse> getBalanceSheet() {
        return ResponseEntity.ok(balanceSheetService.generateBalanceSheet(getLoggedInMerchant()));
    }

    @GetMapping("/payment-methods")
    public ResponseEntity<java.util.Map<String, Long>> getPaymentMethods() {
        return ResponseEntity.ok(reportService.getPaymentMethods(getLoggedInMerchant()));
    }

    @GetMapping("/pnl/export")
    public ResponseEntity<byte[]> exportPnl(
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) throws IOException {

        MerchantProfile merchant = getLoggedInMerchant();
        DateRangeUtils.DateRange resolved = DateRangeUtils.resolveRange(range, startDate, endDate);
        log.info("Export request: format={}, range={}", format, range);
        ProfitLossResponse pnl = profitLossService.calculateProfitLoss(merchant, resolved.getStartDate(), resolved.getEndDate());
        if (pnl == null) {
            pnl = ProfitLossResponse.builder()
                    .totalRevenue(java.math.BigDecimal.ZERO)
                    .totalCogs(java.math.BigDecimal.ZERO)
                    .grossProfit(java.math.BigDecimal.ZERO)
                    .netProfit(java.math.BigDecimal.ZERO)
                    .totalProcessingFees(java.math.BigDecimal.ZERO)
                    .unknownCogsRevenue(java.math.BigDecimal.ZERO)
                    .unknownCogsCount(0L)
                    .transparencyNote("")
                    .build();
        }

        byte[] data;
        String ext;
        MediaType mediaType;

        if ("excel".equalsIgnoreCase(format)) {
            data = reportExcelService.generatePnlExcel(pnl);
            ext = ".xlsx";
            mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else {
            data = reportPdfService.generatePnlPdf(pnl, merchant.getBusinessName(), merchant.getGstin());
            ext = ".pdf";
            mediaType = MediaType.APPLICATION_PDF;
        }

        String filename = "pnl-" + (range != null ? range.toLowerCase() : "custom") + "-" + resolved.getDescription() + ext;

        log.info("EXPORT_AUDIT: User={} Merchant={} Type=PNL Format={} Range={} ({}) to {}", 
                getLoggedInMerchant().getUser().getEmail(), merchant.getId(), format, range, resolved.getStartDate(), resolved.getEndDate());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(mediaType)
                .body(data);
    }


    @GetMapping("/statement/export")
    public ResponseEntity<byte[]> exportStatement(
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) throws IOException {

        MerchantProfile merchant = getLoggedInMerchant();
        DateRangeUtils.DateRange resolved = DateRangeUtils.resolveRange(range, startDate, endDate);
        StatementResponse statement = statementService.generateStatement(merchant, resolved.getStartDate(), resolved.getEndDate());
        if (statement == null) {
            statement = StatementResponse.builder()
                    .merchantName("")
                    .gstin("")
                    .email("")
                    .startDate(resolved.getStartDate())
                    .endDate(resolved.getEndDate())
                    .openingBalance(java.math.BigDecimal.ZERO)
                    .closingBalance(java.math.BigDecimal.ZERO)
                    .totalCredits(java.math.BigDecimal.ZERO)
                    .totalDebits(java.math.BigDecimal.ZERO)
                    .transactions(java.util.Collections.emptyList())
                    .build();
        }

        byte[] data;
        String ext;
        MediaType mediaType;

        if ("excel".equalsIgnoreCase(format)) {
            data = reportExcelService.generateStatementExcel(statement);
            ext = ".xlsx";
            mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else {
            data = reportPdfService.generateStatementPdf(statement);
            ext = ".pdf";
            mediaType = MediaType.APPLICATION_PDF;
        }

        String filename = "statement-" + resolved.getDescription() + ext;

        log.info("EXPORT_AUDIT: User={} Merchant={} Type=STATEMENT Format={} Range={} ({}) to {}", 
                getLoggedInMerchant().getUser().getEmail(), merchant.getId(), format, range, resolved.getStartDate(), resolved.getEndDate());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(mediaType)
                .body(data);
    }


    @GetMapping("/summary")
    public ResponseEntity<com.billme.report.dto.SummaryReportResponse> getSummary(
            @RequestParam(defaultValue = "daily") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(analyticsService.getMerchantSummary(getLoggedInMerchant(), range, startDate, endDate));
    }

    @GetMapping("/summary/export")
    public ResponseEntity<byte[]> exportSummary(
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) throws IOException {

        MerchantProfile merchant = getLoggedInMerchant();
        DateRangeUtils.DateRange resolved = DateRangeUtils.resolveRange(range, startDate, endDate);
        com.billme.report.dto.SummaryReportResponse summary = analyticsService.getMerchantSummary(merchant, range, startDate, endDate);
        if (summary == null) {
            summary = com.billme.report.dto.SummaryReportResponse.builder()
                    .range(range != null ? range : "daily")
                    .revenueTrend(java.util.Collections.emptyList())
                    .withdrawalTrend(java.util.Collections.emptyList())
                    .totalRevenue(java.math.BigDecimal.ZERO)
                    .grossProfit(java.math.BigDecimal.ZERO)
                    .totalWithdrawals(java.math.BigDecimal.ZERO)
                    .withdrawals(java.math.BigDecimal.ZERO)
                    .netCashFlow(java.math.BigDecimal.ZERO)
                    .unknownCogsImpact(java.math.BigDecimal.ZERO)
                    .transparencyNote("")
                    .build();
        }

        byte[] data;
        String ext;
        MediaType mediaType;

        if ("excel".equalsIgnoreCase(format)) {
            data = reportExcelService.generateSummaryExcel(summary, merchant.getBusinessName());
            ext = ".xlsx";
            mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else {
            data = reportPdfService.generateSummaryPdf(summary, merchant.getBusinessName(), merchant.getGstin());
            ext = ".pdf";
            mediaType = MediaType.APPLICATION_PDF;
        }

        String filename = "summary-" + (range != null ? range.toLowerCase() : "daily") + "-" + resolved.getDescription() + ext;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(mediaType)
                .body(data);
    }

    // Range-specific export aliases for backward-compatible explicit URLs.
    @GetMapping("/daily/export")
    public ResponseEntity<byte[]> exportDaily(
            @RequestParam(defaultValue = "pdf") String format) throws IOException {
        return exportSummary(format, "daily", null, null);
    }

    @GetMapping("/weekly/export")
    public ResponseEntity<byte[]> exportWeekly(
            @RequestParam(defaultValue = "pdf") String format) throws IOException {
        return exportSummary(format, "weekly", null, null);
    }

    @GetMapping("/monthly/export")
    public ResponseEntity<byte[]> exportMonthly(
            @RequestParam(defaultValue = "pdf") String format) throws IOException {
        return exportSummary(format, "monthly", null, null);
    }

    @GetMapping("/quarterly/export")
    public ResponseEntity<byte[]> exportQuarterly(
            @RequestParam(defaultValue = "pdf") String format) throws IOException {
        return exportSummary(format, "quarterly", null, null);
    }

    @GetMapping("/yearly/export")
    public ResponseEntity<byte[]> exportYearly(
            @RequestParam(defaultValue = "pdf") String format) throws IOException {
        return exportSummary(format, "yearly", null, null);
    }

}
