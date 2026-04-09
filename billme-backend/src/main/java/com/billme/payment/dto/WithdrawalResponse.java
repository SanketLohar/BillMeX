package com.billme.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WithdrawalResponse {

    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
    private String reference;

    /** Bank account that received this payout — null for legacy withdrawals */
    private Long bankAccountId;
    private String bankName;
}