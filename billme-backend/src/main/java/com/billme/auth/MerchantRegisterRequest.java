package com.billme.auth;

import lombok.Data;

import java.time.LocalDate;

@Data
public class MerchantRegisterRequest {

    private String email;
    private String password;

    private String businessName;
    private String ownerName;

    private LocalDate dob;
}