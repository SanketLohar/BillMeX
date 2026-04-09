package com.billme.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class DataPoint {
    private String label;
    private BigDecimal value;
}
