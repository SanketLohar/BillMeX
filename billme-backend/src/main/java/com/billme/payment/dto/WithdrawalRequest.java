package com.billme.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawalRequest {
    private BigDecimal amount;
}