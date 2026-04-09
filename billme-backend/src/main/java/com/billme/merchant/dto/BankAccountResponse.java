package com.billme.merchant.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BankAccountResponse {
    private Long id;
    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifsc;
    private boolean isDefault;
    private boolean isVerified;
}
