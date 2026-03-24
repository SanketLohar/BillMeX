package com.billme.product.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class CreateProductRequest {

    @NotBlank
    private String name;

    @NotNull
    @Positive
    private BigDecimal price;

    private String barcode;

@JsonAlias({"gstPercent","gst_rate"})
@NotNull
@Min(0)
@Max(28)
private Integer gstRate;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }
}