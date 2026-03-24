package com.billme.transaction.dto;

import com.billme.transaction.Direction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionHistoryResponse {

    private Long transactionId;

    private Direction direction; // DEBIT or CREDIT

    private BigDecimal amount;

    private TransactionType type;

    private TransactionStatus status;

    private String counterparty; // email or business name

    private LocalDateTime timestamp;
}