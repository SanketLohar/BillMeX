package com.billme.payment;

import lombok.Data;

@Data
public class FacePayRequest {

    private Object embedding; // 🔥 accept ANY format (array / object / string)
}