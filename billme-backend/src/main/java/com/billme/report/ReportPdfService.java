package com.billme.report;

import com.billme.report.dto.ProfitLossResponse;
import com.billme.report.dto.StatementResponse;
import com.billme.report.dto.SummaryReportResponse;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private final TemplateEngine templateEngine;

    public byte[] generatePnlPdf(ProfitLossResponse pnl, String merchantName, String gstin) {
        ProfitLossResponse safePnl = normalizeProfitLoss(pnl);
        log.info("Generating PNL PDF: merchantName={}, gstinPresent={}, revenue={}, cogs={}, grossProfit={}",
                merchantName,
                gstin != null && !gstin.isBlank(),
                safePnl.getTotalRevenue(),
                safePnl.getTotalCogs(),
                safePnl.getGrossProfit());
        Context context = new Context();
        context.setVariable("pnl", safePnl);
        context.setVariable("merchantName", merchantName);
        context.setVariable("gstin", gstin);

        String html = renderTemplate("pnl-report", context);
        log.debug("Rendered PNL HTML: {}", html);
        log.info("Rendered PNL HTML length={}", html != null ? html.length() : 0);
        return generatePdfFromHtml(html);
    }

    public byte[] generateStatementPdf(StatementResponse statement) {
        StatementResponse safeStatement = normalizeStatement(statement);
        log.info("Generating Statement PDF: merchantName={}, txCount={}, openingBalance={}, closingBalance={}",
                safeStatement.getMerchantName(),
                safeStatement.getTransactions() != null ? safeStatement.getTransactions().size() : 0,
                safeStatement.getOpeningBalance(),
                safeStatement.getClosingBalance());
        Context context = new Context();
        context.setVariable("statement", safeStatement);

        String html = renderTemplate("statement-report", context);
        log.debug("Rendered Statement HTML: {}", html);
        log.info("Rendered Statement HTML length={}", html != null ? html.length() : 0);
        return generatePdfFromHtml(html);
    }

    public byte[] generateSummaryPdf(com.billme.report.dto.SummaryReportResponse summary, String merchantName, String gstin) {
        SummaryReportResponse safeSummary = normalizeSummary(summary);
        log.info("Generating Summary PDF: merchantName={}, range={}, revenue={}, withdrawals={}, netCashFlow={}",
                merchantName,
                safeSummary.getRange(),
                safeSummary.getTotalRevenue(),
                safeSummary.getTotalWithdrawals(),
                safeSummary.getNetCashFlow());
        Context context = new Context();
        context.setVariable("summary", safeSummary);
        context.setVariable("merchantName", merchantName);
        context.setVariable("gstin", gstin);

        String html = renderTemplate("summary-report", context);
        log.debug("Rendered Summary HTML: {}", html);
        log.info("Rendered Summary HTML length={}", html != null ? html.length() : 0);
        return generatePdfFromHtml(html);
    }

    private String renderTemplate(String templateName, Context context) {
        log.info("Rendering template {}", templateName);
        try {
            return templateEngine.process(templateName, context);
        } catch (Exception e) {
            log.error("TEMPLATE_RENDER_FAILED template={} message={}", templateName, e.getMessage(), e);
            throw new RuntimeException("TEMPLATE_RENDER_FAILED", e);
        }
    }

    private byte[] generatePdfFromHtml(String html) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);

            // Font is optional in production environments.
            // If the custom font file isn't available, fall back to renderer defaults.
            ClassPathResource fontResource = new ClassPathResource("fonts/NotoSans-Regular.ttf");
            if (fontResource.exists()) {
                builder.useFont(
                        () -> {
                            try {
                                return fontResource.getInputStream();
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to load PDF font", e);
                            }
                        },
                        "NotoSans",
                        400,
                        PdfRendererBuilder.FontStyle.NORMAL,
                        true
                );
            } else {
                log.warn("PDF font not found: fonts/NotoSans-Regular.ttf. Falling back to default fonts.");
            }

            builder.toStream(outputStream);
            log.info("Starting PDF render");
            try {
                builder.run();
            } catch (Exception e) {
                log.error("PDF_RENDER_FAILED message={}", e.getMessage(), e);
                throw new RuntimeException("PDF_RENDER_FAILED", e);
            }

            log.info("PDF generated successfully. bytes={}", outputStream.size());
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    private ProfitLossResponse normalizeProfitLoss(ProfitLossResponse pnl) {
        if (pnl == null) {
            return ProfitLossResponse.builder()
                    .totalRevenue(BigDecimal.ZERO)
                    .totalCogs(BigDecimal.ZERO)
                    .grossProfit(BigDecimal.ZERO)
                    .netProfit(BigDecimal.ZERO)
                    .totalProcessingFees(BigDecimal.ZERO)
                    .unknownCogsRevenue(BigDecimal.ZERO)
                    .unknownCogsCount(0L)
                    .transparencyNote("")
                    .build();
        }

        return ProfitLossResponse.builder()
                .totalRevenue(safeBigDecimal(pnl.getTotalRevenue()))
                .totalCogs(safeBigDecimal(pnl.getTotalCogs()))
                .grossProfit(safeBigDecimal(pnl.getGrossProfit()))
                .netProfit(safeBigDecimal(pnl.getNetProfit()))
                .totalProcessingFees(safeBigDecimal(pnl.getTotalProcessingFees()))
                .unknownCogsRevenue(safeBigDecimal(pnl.getUnknownCogsRevenue()))
                .unknownCogsCount(safeLong(pnl.getUnknownCogsCount()))
                .transparencyNote(Objects.toString(pnl.getTransparencyNote(), ""))
                .build();
    }

    private StatementResponse normalizeStatement(StatementResponse statement) {
        if (statement == null) {
            LocalDateTime now = LocalDateTime.now();
            return StatementResponse.builder()
                    .merchantName("")
                    .gstin("")
                    .email("")
                    .startDate(now)
                    .endDate(now)
                    .openingBalance(BigDecimal.ZERO)
                    .closingBalance(BigDecimal.ZERO)
                    .totalCredits(BigDecimal.ZERO)
                    .totalDebits(BigDecimal.ZERO)
                    .transactions(Collections.emptyList())
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        return StatementResponse.builder()
                .merchantName(Objects.toString(statement.getMerchantName(), ""))
                .gstin(Objects.toString(statement.getGstin(), ""))
                .email(Objects.toString(statement.getEmail(), ""))
                .startDate(statement.getStartDate() != null ? statement.getStartDate() : now)
                .endDate(statement.getEndDate() != null ? statement.getEndDate() : now)
                .openingBalance(safeBigDecimal(statement.getOpeningBalance()))
                .closingBalance(safeBigDecimal(statement.getClosingBalance()))
                .totalCredits(safeBigDecimal(statement.getTotalCredits()))
                .totalDebits(safeBigDecimal(statement.getTotalDebits()))
                .transactions(statement.getTransactions() != null ? statement.getTransactions() : Collections.emptyList())
                .build();
    }

    private SummaryReportResponse normalizeSummary(SummaryReportResponse summary) {
        if (summary == null) {
            return SummaryReportResponse.builder()
                    .range("daily")
                    .revenueTrend(Collections.emptyList())
                    .withdrawalTrend(Collections.emptyList())
                    .totalRevenue(BigDecimal.ZERO)
                    .grossProfit(BigDecimal.ZERO)
                    .totalWithdrawals(BigDecimal.ZERO)
                    .withdrawals(BigDecimal.ZERO)
                    .netCashFlow(BigDecimal.ZERO)
                    .unknownCogsImpact(BigDecimal.ZERO)
                    .transparencyNote("")
                    .build();
        }

        return SummaryReportResponse.builder()
                .range(Objects.toString(summary.getRange(), "daily"))
                .revenueTrend(summary.getRevenueTrend() != null ? summary.getRevenueTrend() : Collections.emptyList())
                .withdrawalTrend(summary.getWithdrawalTrend() != null ? summary.getWithdrawalTrend() : Collections.emptyList())
                .totalRevenue(safeBigDecimal(summary.getTotalRevenue()))
                .grossProfit(safeBigDecimal(summary.getGrossProfit()))
                .totalWithdrawals(safeBigDecimal(summary.getTotalWithdrawals()))
                .withdrawals(safeBigDecimal(summary.getWithdrawals()))
                .netCashFlow(safeBigDecimal(summary.getNetCashFlow()))
                .unknownCogsImpact(safeBigDecimal(summary.getUnknownCogsImpact()))
                .transparencyNote(Objects.toString(summary.getTransparencyNote(), ""))
                .build();
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }
}
