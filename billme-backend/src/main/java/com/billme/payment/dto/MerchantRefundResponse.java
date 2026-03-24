package com.billme.payment.dto;

import com.billme.invoice.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MerchantRefundResponse {

    private String invoiceNumber;

    private BigDecimal amount;

    private PaymentMethod paymentMethod;

    private LocalDateTime refundedAt;

    private String referenceId;
}