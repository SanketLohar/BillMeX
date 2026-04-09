package com.billme.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawalRequest {
    private BigDecimal amount;

    /**
     * Optional: ID of the bank account to receive the withdrawal payout.
     * If null → service will use the merchant's default bank account (existing behavior).
     */
    private Long bankAccountId;
}