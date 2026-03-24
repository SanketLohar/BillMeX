package com.billme.payment.dto;

import lombok.Data;

@Data
public class VerifyRazorpayRequest {
    private String razorpay_payment_id;
    private String razorpay_order_id;
    private String razorpay_signature;
}
