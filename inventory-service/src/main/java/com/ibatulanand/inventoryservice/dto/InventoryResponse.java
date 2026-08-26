package com.ibatulanand.inventoryservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryResponse {
    @JsonProperty("skuCode")
    private String skuCode;

    @JsonProperty("inStock")
    private boolean isInStock;
}
