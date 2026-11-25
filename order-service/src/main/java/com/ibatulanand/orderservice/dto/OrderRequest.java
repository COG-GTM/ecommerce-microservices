package com.ibatulanand.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {
    public static final int MAX_LINE_ITEMS = 100;

    @NotEmpty
    @Size(max = MAX_LINE_ITEMS)
    private List<@Valid @NotNull OrderLineItemsDto> orderLineItemsDtoList;
}
