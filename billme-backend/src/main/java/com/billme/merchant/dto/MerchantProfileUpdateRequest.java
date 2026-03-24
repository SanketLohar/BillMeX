package com.billme.merchant.dto;

import lombok.Data;

@Data
public class MerchantProfileUpdateRequest {

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

    private boolean gstRegistered;
    private String gstin;
}