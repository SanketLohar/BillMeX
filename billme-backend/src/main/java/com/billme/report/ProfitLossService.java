package com.billme.report;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.ProfitLossResponse;
import com.billme.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfitLossService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public ProfitLossResponse calculateProfitLoss(MerchantProfile merchant, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Calculating P&L for merchant {} between {} and {}", merchant.getId(), startDate, endDate);

        // Ideally this should use native aggregation queries. Since JPA aggregates over nested collections (getItems().cogsTotal)
        // require native views, we will safely collect and process just the valid ones to avoid OOM, or use a streaming approach.
        // For zero-regression and schema independence, we will aggregate paid invoices in Java for now, but skipping missing COGS.

        List<InvoiceStatus> validStatuses = List.of(
                InvoiceStatus.PAID,
                InvoiceStatus.REFUND_REJECTED,
                InvoiceStatus.REFUND_REQUESTED
        );

        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();
        LocalDateTime start = startDate != null ? startDate : end.minusYears(1);

        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366) {
            throw new IllegalArgumentException("P&L report date range cannot exceed 1 year. Please select a smaller date range.");
        }

        List<Invoice> invoices = invoiceRepository.findByMerchant_IdAndStatusInAndPaidAtBetween(
                merchant.getId(), validStatuses, start, end
        );

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCogs = BigDecimal.ZERO;
        BigDecimal totalProcessingFees = BigDecimal.ZERO;
        BigDecimal unknownRevenue = BigDecimal.ZERO;
        long unknownCounts = 0;

        for (Invoice invoice : invoices) {
            BigDecimal invoiceRevenue = invoice.getSubtotal() != null ? invoice.getSubtotal() : invoice.getAmount();
            BigDecimal invoiceCogs = BigDecimal.ZERO;
            boolean allItemsHaveCost = true;

            for (com.billme.invoice.InvoiceItem item : invoice.getItems()) {
                if (item.getCogsTotal() != null) {
                    invoiceCogs = invoiceCogs.add(item.getCogsTotal());
                } else if (item.getCostPriceSnapshot() != null) {
                    // Fallback to snapshot (Deterministic)
                    invoiceCogs = invoiceCogs.add(item.getCostPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())));
                } else if (item.getProduct() != null && item.getProduct().getCostPrice() != null) {
                    // Fallback to live product cost (Deterministic Lookup)
                    invoiceCogs = invoiceCogs.add(item.getProduct().getCostPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                } else {
                    allItemsHaveCost = false;
                    log.warn("AUDIT_WARN: Unknown COGS for ItemID: {} in Invoice: {}", item.getId(), invoice.getInvoiceNumber());
                }
            }

            if (allItemsHaveCost) {
                totalRevenue = totalRevenue.add(invoiceRevenue);
                totalProcessingFees = totalProcessingFees.add(invoice.getProcessingFee() != null ? invoice.getProcessingFee() : BigDecimal.ZERO);
                totalCogs = totalCogs.add(invoiceCogs);
            } else {
                unknownRevenue = unknownRevenue.add(invoiceRevenue);
                unknownCounts++;
            }
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCogs);
        BigDecimal netProfit = grossProfit.subtract(totalProcessingFees);

        return ProfitLossResponse.builder()
                .totalRevenue(totalRevenue)
                .totalCogs(totalCogs)
                .grossProfit(grossProfit)
                .netProfit(netProfit)
                .totalProcessingFees(totalProcessingFees)
                .unknownCogsRevenue(unknownRevenue)
                .unknownCogsCount(unknownCounts)
                .transparencyNote("Net Profit calculated only from known cost profiles.")
                .build();
    }
}
