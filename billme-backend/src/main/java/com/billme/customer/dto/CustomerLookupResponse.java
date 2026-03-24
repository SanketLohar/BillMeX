package com.billme.customer.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerLookupResponse {

    private Long id;

    private String name;

    private String email;

    private String state;
    private String city;
    private String profileImageUrl;

}