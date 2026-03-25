package com.billme.payment;

import lombok.Data;

@Data
public class FacePayRequest {

    private String invoiceNumber; // 🔥 NEW
    private String token;         // 🔥 NEW
    private Object embedding;
}