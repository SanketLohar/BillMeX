package com.billme.wallet.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletSummaryResponse {

    private BigDecimal currentBalance;
    private BigDecimal totalReceived;
    private BigDecimal totalWithdrawn;
    private BigDecimal platformFee;
    private BigDecimal escrowBalance;

}