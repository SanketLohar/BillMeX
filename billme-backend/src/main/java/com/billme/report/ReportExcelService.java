package com.billme.report;

import com.billme.report.dto.ProfitLossResponse;
import com.billme.report.dto.StatementResponse;
import com.billme.report.dto.StatementTransaction;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
public class ReportExcelService {

    private double safeDouble(BigDecimal v) {
        return v != null ? v.doubleValue() : 0d;
    }

    private long safeLong(Long v) {
        return v != null ? v : 0L;
    }

    public byte[] generatePnlExcel(ProfitLossResponse pnl) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Keep 100 rows in memory
            
            // Sheet 1: Summary Defaults
            SXSSFSheet summarySheet = workbook.createSheet("P&L Summary");
            
            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("[$₹-en-IN]#,##0.00"));

            Row headerRow = summarySheet.createRow(0);
            headerRow.createCell(0).setCellValue("Metric");
            headerRow.createCell(1).setCellValue("Amount");

            createStatRow(summarySheet, 1, "Total Revenue", safeDouble(pnl.getTotalRevenue()), currencyStyle);
            createStatRow(summarySheet, 2, "Cost of Goods Sold (COGS)", safeDouble(pnl.getTotalCogs()), currencyStyle);
            createStatRow(summarySheet, 3, "Gross Profit", safeDouble(pnl.getGrossProfit()), currencyStyle);
            createStatRow(summarySheet, 4, "Processing Fees", safeDouble(pnl.getTotalProcessingFees()), currencyStyle);
            createStatRow(summarySheet, 5, "Net Profit (from known data)", safeDouble(pnl.getNetProfit()), currencyStyle);
            
            // 🛡️ Audit Transparency
            BigDecimal unknownRevenue = pnl.getUnknownCogsRevenue();
            if (unknownRevenue != null && unknownRevenue.compareTo(BigDecimal.ZERO) > 0) {
                createStatRow(summarySheet, 7, "Unknown COGS Impact (Revenue Excluded)", safeDouble(unknownRevenue), currencyStyle);
                Row warningRow = summarySheet.createRow(8);
                warningRow.createCell(0).setCellValue("Notice: " + pnl.getTransparencyNote());
            }

            // Formatting
            summarySheet.setColumnWidth(0, 10000);
            summarySheet.setColumnWidth(1, 4000);

            // Sheet 2: Missing/Legacy Info
            long unknownCount = safeLong(pnl.getUnknownCogsCount());
            if (unknownCount > 0) {
                SXSSFSheet unknownSheet = workbook.createSheet("Legacy & Unknown Data");
                Row uHeader = unknownSheet.createRow(0);
                uHeader.createCell(0).setCellValue("Metric");
                uHeader.createCell(1).setCellValue("Value");

                createStatRow(unknownSheet, 1, "Excluded Legacy Revenue", safeDouble(unknownRevenue), currencyStyle);
                unknownSheet.createRow(2).createCell(0).setCellValue("Invoices Excluded");
                unknownSheet.getRow(2).createCell(1).setCellValue(unknownCount);
                
                unknownSheet.setColumnWidth(0, 8000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateStatementExcel(StatementResponse statement) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            
            SXSSFSheet sheet = workbook.createSheet("Merchant Statement");
            
            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("[$₹-en-IN]#,##0.00"));

            // Setup Headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Date", "Transaction ID", "Invoice Ref", "Type", "Status", "Amount", "Closing Balance"};
            for(int i=0; i<headers.length; i++){
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Write Data
            int rowIdx = 1;
            
            // Opening Balance Row
            Row obRow = sheet.createRow(rowIdx++);
            obRow.createCell(3).setCellValue("OPENING BALANCE");
            Cell obCell = obRow.createCell(6);
            obCell.setCellValue(safeDouble(statement.getOpeningBalance()));
            obCell.setCellStyle(currencyStyle);
            
            // Transactions
            List<StatementTransaction> txs = statement.getTransactions() != null ? statement.getTransactions() : Collections.emptyList();
            for (StatementTransaction tx : txs) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(tx.getTimestamp() != null ? tx.getTimestamp().toString() : "");
                row.createCell(1).setCellValue(tx.getTransactionId() != null ? tx.getTransactionId() : "");
                row.createCell(2).setCellValue(tx.getInvoiceNumber() != null ? tx.getInvoiceNumber() : "N/A");
                row.createCell(3).setCellValue(tx.getType() != null ? tx.getType() : "");
                row.createCell(4).setCellValue(tx.getStatus() != null ? tx.getStatus() : "");
                
                Cell amtCell = row.createCell(5);
                amtCell.setCellValue(safeDouble(tx.getAmount()));
                amtCell.setCellStyle(currencyStyle);
                
                Cell cbCell = row.createCell(6);
                cbCell.setCellValue(safeDouble(tx.getClosingBalance()));
                cbCell.setCellStyle(currencyStyle);
            }

            // Summary Totals
            rowIdx++;
            Row summaryTitleRow = sheet.createRow(rowIdx++);
            summaryTitleRow.createCell(0).setCellValue("--- PERIOD SUMMARY ---");
            
            Row creditsRow = sheet.createRow(rowIdx++);
            creditsRow.createCell(0).setCellValue("Total Credits:");
            Cell totalCreditsCell = creditsRow.createCell(1);
            totalCreditsCell.setCellValue(safeDouble(statement.getTotalCredits()));
            totalCreditsCell.setCellStyle(currencyStyle);
            
            Row debitsRow = sheet.createRow(rowIdx++);
            debitsRow.createCell(0).setCellValue("Total Debits:");
            Cell totalDebitsCell = debitsRow.createCell(1);
            totalDebitsCell.setCellValue(safeDouble(statement.getTotalDebits()));
            totalDebitsCell.setCellStyle(currencyStyle);

            for(int i=0; i<headers.length; i++) {
                sheet.setColumnWidth(i, 6000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateSummaryExcel(com.billme.report.dto.SummaryReportResponse summary, String businessName) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            String range = summary.getRange() != null ? summary.getRange() : "daily";
            SXSSFSheet sheet = workbook.createSheet("Summary Analytics - " + range.toUpperCase());
            
            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("[$₹-en-IN]#,##0.00"));

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Business Name: " + businessName);
            titleRow.createCell(1).setCellValue("Range: " + range.toUpperCase());

            Row headerRow = sheet.createRow(2);
            headerRow.createCell(0).setCellValue("Bucket/Date");
            headerRow.createCell(1).setCellValue("Revenue");
            headerRow.createCell(2).setCellValue("Withdrawals");

            int rowIdx = 3;
            // Merge labels into a unified trend
            List<com.billme.report.dto.DataPoint> revenueTrend = summary.getRevenueTrend() != null ? summary.getRevenueTrend() : Collections.emptyList();
            List<com.billme.report.dto.DataPoint> withdrawalTrend = summary.getWithdrawalTrend() != null ? summary.getWithdrawalTrend() : Collections.emptyList();

            java.util.Map<String, java.math.BigDecimal> revenueMap = revenueTrend.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            dp -> dp.getLabel() != null ? dp.getLabel() : "",
                            com.billme.report.dto.DataPoint::getValue,
                            (a, b) -> a));
            java.util.Map<String, java.math.BigDecimal> withdrawalMap = withdrawalTrend.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            dp -> dp.getLabel() != null ? dp.getLabel() : "",
                            com.billme.report.dto.DataPoint::getValue,
                            (a, b) -> a));

            java.util.Set<String> allBuckets = new java.util.TreeSet<>(revenueMap.keySet());
            allBuckets.addAll(withdrawalMap.keySet());

            for (String bucket : allBuckets) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(bucket != null ? bucket : "");
                Cell rCell = row.createCell(1);
                rCell.setCellValue(safeDouble(revenueMap.getOrDefault(bucket, java.math.BigDecimal.ZERO)));
                rCell.setCellStyle(currencyStyle);
                Cell wCell = row.createCell(2);
                wCell.setCellValue(safeDouble(withdrawalMap.getOrDefault(bucket, java.math.BigDecimal.ZERO)));
                wCell.setCellStyle(currencyStyle);
            }

            rowIdx++;
            createStatRow(sheet, rowIdx++, "Total Revenue", safeDouble(summary.getTotalRevenue()), currencyStyle);
            createStatRow(sheet, rowIdx++, "Total Withdrawals", safeDouble(summary.getTotalWithdrawals()), currencyStyle);
            createStatRow(sheet, rowIdx++, "Net Cash Flow", safeDouble(summary.getNetCashFlow()), currencyStyle);
            
            BigDecimal unknownImpact = summary.getUnknownCogsImpact();
            if (unknownImpact != null && unknownImpact.compareTo(BigDecimal.ZERO) > 0) {
                createStatRow(sheet, rowIdx++, "Unknown COGS Impact (Excluded Rev)", safeDouble(unknownImpact), currencyStyle);
                Row tRow = sheet.createRow(rowIdx++);
                tRow.createCell(0).setCellValue(summary.getTransparencyNote() != null ? summary.getTransparencyNote() : "");
            }

            for(int i=0; i<3; i++) {
                sheet.setColumnWidth(i, 6000);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createStatRow(Sheet sheet, int rowNum, String label, double value, CellStyle currencyStyle) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        cell.setCellValue(value);
        if (currencyStyle != null) {
            cell.setCellStyle(currencyStyle);
        }
    }
}
