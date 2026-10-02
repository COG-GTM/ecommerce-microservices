package com.ibatulanand.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenderDto {
    private String type;
    private String label;
    private BigDecimal amount;

    public String effectiveLabel() {
        return label == null ? type : label;
    }
}
