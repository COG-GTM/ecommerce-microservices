package com.ibatulanand.orderservice.dto;

import com.ibatulanand.orderservice.model.TenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Mirrors {@code types.ts#Tender}. Only type, label and amount are ever accepted or stored. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenderDto {
    private TenderType type;
    private String label;
    private BigDecimal amount;
}
