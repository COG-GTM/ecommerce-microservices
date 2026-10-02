package com.ibatulanand.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRequest {
    private String storeId;
    private String registerId;
    private String associateId;
    private List<LineItemRequest> lineItems;
    private List<String> promotions;
    private List<TenderDto> tenders;
    private boolean taxExempt;
}
