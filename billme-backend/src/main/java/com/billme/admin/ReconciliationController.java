package com.billme.admin;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.repository.InvoiceRepository;
import com.billme.transaction.LedgerEntryType;
import com.billme.transaction.LedgerService;
import com.billme.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reconcile")
@RequiredArgsConstructor
@Slf4j
public class ReconciliationController {

    private final InvoiceRepository invoiceRepository;
    private final WalletService walletService;
    private final LedgerService ledgerService;

    /**
     * Data Reconciliation for REFUND_REJECTED invoices with stuck escrow.
     * 🛡️ PRODUCTION SAFE: Includes dryRun mode and idempotency checks.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reconcileEscrow(@RequestParam(defaultValue = "true") boolean dryRun) {
        log.info("🚀 [RECONCILIATION START] Mode: {}", dryRun ? "DRY_RUN (Log only)" : "ACTUAL_FIX (Production)");

        // 1. Find all invoices in REFUND_REJECTED status
        List<Invoice> rejectedInvoices = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.REFUND_REJECTED)
                .toList();

        List<Map<String, Object>> report = new ArrayList<>();
        int fixedCount = 0;
        int skippedCount = 0;

        for (Invoice inv : rejectedInvoices) {
            String ref = "RELEASE-" + inv.getInvoiceNumber();
            BigDecimal amount = inv.getTotalPayable() != null ? inv.getTotalPayable() : inv.getAmount();
            Long walletId = walletService.getWalletByUser(inv.getMerchant().getUser()).getId();

            // 2. Check if already released via Ledger audit
            boolean alreadyReleased = ledgerService.existsByWalletAndReferenceAndType(
                    walletId, ref, LedgerEntryType.ESCROW_RELEASE
            );

            if (alreadyReleased) {
                log.info("✅ Invoice {} already has a release ledger entry. Skipping.", inv.getInvoiceNumber());
                skippedCount++;
                continue;
            }

            // 3. Prepare Log/Report entry
            Map<String, Object> entry = Map.of(
                "invoiceId", inv.getId(),
                "invoiceNumber", inv.getInvoiceNumber(),
                "merchant", inv.getMerchant().getUser().getEmail(),
                "amount", amount,
                "status", "STUCK_ESCROW"
            );
            report.add(entry);

            // 4. Actual Fix (if not dryRun)
            if (!dryRun) {
                try {
                    log.info("🔥 FIXING Invoice {}: Releasing ₹{} from escrow...", inv.getInvoiceNumber(), amount);
                    walletService.releaseFromEscrow(inv.getMerchant().getUser(), amount, ref);
                    fixedCount++;
                } catch (Exception e) {
                    log.error("❌ Failed to fix Invoice {}: {}", inv.getInvoiceNumber(), e.getMessage());
                }
            }
        }

        Map<String, Object> finalSummary = Map.of(
            "mode", dryRun ? "DRY_RUN" : "ACTUAL_FIX",
            "totalRejectedInvoicesFound", rejectedInvoices.size(),
            "stuckEscrowDetected", report.size(),
            "alreadyReleased", skippedCount,
            "fixedInThisSession", fixedCount,
            "details", report
        );

        log.info("🏁 [RECONCILIATION FINISHED] Summary: {}", finalSummary);
        return ResponseEntity.ok(finalSummary);
    }
}
