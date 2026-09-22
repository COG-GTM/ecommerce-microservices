package com.ibatulanand.orderservice.dto;

import com.ibatulanand.orderservice.model.TenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenderDto {
    private TenderType type;
    private BigDecimal amount;
    private String reference;
}
