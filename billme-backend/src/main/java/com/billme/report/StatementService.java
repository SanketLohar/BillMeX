package com.billme.report;

import com.billme.invoice.Invoice;
import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.StatementResponse;
import com.billme.report.dto.StatementTransaction;
import com.billme.repository.TransactionRepository;
import com.billme.transaction.Transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatementService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public StatementResponse generateStatement(MerchantProfile merchant, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating Merchant Statement for {} from {} to {}", merchant.getId(), startDate, endDate);

        Long userId = merchant.getUser().getId();
        
        // Ensure bounds
        LocalDateTime end = endDate != null ? endDate : LocalDateTime.now();
        LocalDateTime start = startDate != null ? startDate : end.minusMonths(1);

        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 366) {
            throw new IllegalArgumentException("Statement date range cannot exceed 365 days. Please select a smaller date range.");
        }

        // 1. Calculate Opening Balance
        BigDecimal openingBalance = transactionRepository.calculateOpeningBalance(userId, start);
        if (openingBalance == null) openingBalance = BigDecimal.ZERO;

        // 2. Fetch Transactions
        List<Transaction> dbTransactions = transactionRepository.findMerchantStatementTransactions(userId, start, end);
        log.info("STATEMENT_FETCH: merchantUserId={} fetchedTxCount={}", userId, dbTransactions.size());
        for (Transaction t : dbTransactions) {
            Long senderId = t.getSenderWallet() != null && t.getSenderWallet().getUser() != null
                    ? t.getSenderWallet().getUser().getId()
                    : null;
            Long receiverId = t.getReceiverWallet() != null && t.getReceiverWallet().getUser() != null
                    ? t.getReceiverWallet().getUser().getId()
                    : null;
            
            log.info("TXN_FETCHED: id={} type={} sender={} receiver={} status={} amount={}",
                    t.getId(), t.getTransactionType(), senderId, receiverId, t.getStatus(), t.getAmount());
        }

        // 3. Process Running Balance
        BigDecimal currentBalance = openingBalance;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDebits = BigDecimal.ZERO;

        List<StatementTransaction> transactions = new ArrayList<>();

        for (Transaction t : dbTransactions) {
            BigDecimal impact = getFinancialImpact(t, userId);
            
            log.info("TXN_IMPACT: id={} type={} impact={} merchantUserId={}", 
                    t.getId(), t.getTransactionType(), impact, userId);
            
            if (impact.compareTo(BigDecimal.ZERO) == 0) {
                continue; // Skip zero-impact transactions for this merchant
            }

            if (impact.compareTo(BigDecimal.ZERO) > 0) {
                totalCredits = totalCredits.add(impact);
            } else {
                totalDebits = totalDebits.add(impact.abs());
            }

            currentBalance = currentBalance.add(impact);

            Invoice invoice = t.getInvoice();
            String invNumber = invoice != null ? invoice.getInvoiceNumber() : null;
            
            transactions.add(StatementTransaction.builder()
                    .transactionId(t.getId().toString())
                    .invoiceNumber(invNumber)
                    .type(t.getTransactionType().name())
                    .amount(impact) // Show signed impact matching credit/debit
                    .closingBalance(currentBalance)
                    .timestamp(t.getCreatedAt())
                    .status(t.getStatus().name())
                    .build());
        }

        return StatementResponse.builder()
                .merchantName(merchant.getBusinessName())
                .gstin(merchant.getGstin())
                .email(merchant.getUser().getEmail())
                .startDate(start)
                .endDate(end)
                .openingBalance(openingBalance)
                .closingBalance(currentBalance)
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .transactions(transactions)
                .build();
    }

    private BigDecimal getFinancialImpact(Transaction t, Long merchantUserId) {
        // 1. Check if merchant is RECEIVER (Credit)
        if (t.getReceiverWallet() != null && 
            t.getReceiverWallet().getUser() != null && 
            t.getReceiverWallet().getUser().getId().equals(merchantUserId)) {
            return t.getAmount();
        }

        // 2. Check if merchant is SENDER (Debit)
        if (t.getSenderWallet() != null && 
            t.getSenderWallet().getUser() != null && 
            t.getSenderWallet().getUser().getId().equals(merchantUserId)) {
            return t.getAmount().negate();
        }

        // 3. Fallback (Safe-guard for platform fees/withdrawals where references might be nested)
        // If the transaction belongs to the merchant but roles aren't captured by wallets (Edge Case)
        return BigDecimal.ZERO;
    }
}
