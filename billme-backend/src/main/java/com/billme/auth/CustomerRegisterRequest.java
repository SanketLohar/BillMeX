package com.billme.auth;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CustomerRegisterRequest {

    private String email;
    private String password;
    private String name;

    private LocalDate dob;

    private String contactNumber;

    private String address;
    private String state;
    private String city;
    private String pinCode;

    // JSON string of averaged embedding from frontend
    private String faceEmbeddings;
}