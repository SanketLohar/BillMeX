package com.billme.auth;

import lombok.Data;

@Data
public class RefreshRequest {
    private String refreshToken;
}
