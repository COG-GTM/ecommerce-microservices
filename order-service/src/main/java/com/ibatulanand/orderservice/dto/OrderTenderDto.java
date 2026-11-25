package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderTenderDto {
    private String tenderType;
    private Integer amountPence;
    private Integer tenderedPence;
    private Integer changeDuePence;
    private String authCode;
}
