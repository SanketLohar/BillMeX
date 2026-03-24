package com.billme.report;

import com.billme.invoice.InvoiceStatus;
import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.BalanceSheetResponse;
import com.billme.repository.InvoiceRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.TransactionRepository;
import com.billme.transaction.TransactionType;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BalanceSheetService {

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;

    public BalanceSheetResponse generateBalanceSheet(MerchantProfile merchant) {
        
        BigDecimal totalRevenue = invoiceRepository.sumAmountByMerchantIdAndStatus(merchant.getId(), InvoiceStatus.PAID);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal platformFees = invoiceRepository.sumProcessingFeeByMerchantIdAndStatus(merchant.getId(), InvoiceStatus.PAID);
        if (platformFees == null) platformFees = BigDecimal.ZERO;

        BigDecimal totalRefunds = transactionRepository.getTotalWithdrawn(merchant.getUser().getWallet(), TransactionType.REFUND);
        if (totalRefunds == null) totalRefunds = BigDecimal.ZERO;

        BigDecimal withdrawals = transactionRepository.getTotalWithdrawn(merchant.getUser().getWallet(), TransactionType.WITHDRAWAL);
        if (withdrawals == null) withdrawals = BigDecimal.ZERO;

        BigDecimal walletBalance = merchant.getUser().getWallet() != null 
            ? merchant.getUser().getWallet().getBalance() 
            : BigDecimal.ZERO;
        BigDecimal netEarnings = totalRevenue.subtract(totalRefunds);

        return BalanceSheetResponse.builder()
                .totalRevenue(totalRevenue)
                .totalRefunds(totalRefunds)
                .walletBalance(walletBalance)
                .platformFees(platformFees)
                .netEarnings(netEarnings)
                .withdrawals(withdrawals)
                .build();
    }
}
