package com.ibatulanand.orderservice.dto;

import jakarta.validation.GroupSequence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {
    @NotEmpty(groups = {ListChecks.class, Default.class})
    @Size(max = 100, groups = {ListChecks.class, Default.class})
    private List<@NotNull @Valid OrderLineItemsDto> orderLineItemsDtoList;

    public interface ListChecks {
    }

    /** Validates line items only after the list size check passes. */
    @GroupSequence({ListChecks.class, Default.class})
    public interface ListFirst {
    }
}
