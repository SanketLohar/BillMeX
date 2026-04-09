package com.billme.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStockRequest {
    @NotNull(message = "Stock quantity is required")
    private Integer stockQuantity;
}
