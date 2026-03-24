package com.billme.transaction;

import com.billme.repository.PaymentTransactionRepository;
import com.billme.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionLedgerService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional(readOnly = true)
    public List<TransactionResponse> getMerchantTransactions() {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return paymentTransactionRepository
                .findByInvoice_Merchant_User_Email(email)
                .stream()
                .map(tx -> TransactionResponse.builder()
                        .invoiceNumber(tx.getInvoice().getInvoiceNumber())
                        .merchantName(tx.getInvoice().getMerchant().getBusinessName())
                        .customerName(tx.getInvoice().getResolvedCustomerName())
                        .amount(tx.getAmount())
                        .paymentMethod(
                                tx.getInvoice().getPaymentMethod() != null
                                        ? tx.getInvoice().getPaymentMethod().name()
                                        : "UNKNOWN"
                        )
                        .status(tx.getStatus())
                        .paidAt(tx.getCapturedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getCustomerTransactions() {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        return paymentTransactionRepository
                .findByInvoice_Customer_User_Email(email)
                .stream()
                .map(tx -> TransactionResponse.builder()
                        .invoiceNumber(tx.getInvoice().getInvoiceNumber())
                        .merchantName(tx.getInvoice().getMerchant().getBusinessName())
                        .customerName(tx.getInvoice().getResolvedCustomerName())
                        .amount(tx.getAmount())
                        .paymentMethod(
                                tx.getInvoice().getPaymentMethod() != null
                                        ? tx.getInvoice().getPaymentMethod().name()
                                        : "UNKNOWN"
                        )
                        .status(tx.getStatus())
                        .paidAt(tx.getCapturedAt())
                        .build())
                .toList();
    }
}