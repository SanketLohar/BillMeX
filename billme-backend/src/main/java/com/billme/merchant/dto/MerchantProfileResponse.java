package com.billme.merchant.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MerchantProfileResponse {

    private String businessName;
    private String ownerName;

    private String phone;

    private String address;
    private String state;
    private String city;
    private String pinCode;

    private String upiId;

    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;

    private boolean profileCompleted;

    private boolean gstRegistered;
    private String gstin;

    private String email;
    private String profileImageUrl;
    private String message;
}