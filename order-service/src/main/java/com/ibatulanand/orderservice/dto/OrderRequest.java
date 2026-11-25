package com.ibatulanand.orderservice.dto;

import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@GroupSequence({OrderRequest.class, OrderRequest.LineItems.class})
public class OrderRequest {
    @NotEmpty
    @Size(max = 100)
    private List<@NotNull(groups = LineItems.class) @Valid @ConvertGroup(from = LineItems.class, to = Default.class) OrderLineItemsDto> orderLineItemsDtoList;

    /** Line items are validated only after the list size check passes. */
    public interface LineItems {
    }
}
