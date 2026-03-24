package com.billme.invoice.dto;

import lombok.Data;

@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class CreateInvoiceItemRequest {

    private Long productId;   // optional
    private String barcode;   // optional
    private Integer quantity;
}