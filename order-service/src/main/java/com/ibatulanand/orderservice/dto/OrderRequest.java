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
    public static final int MAX_LINE_ITEMS = 100;

    public interface ListSize {}

    /** Checks the list size before any item, so an oversized list fails with one cheap error. */
    @GroupSequence({ListSize.class, Default.class})
    public interface ValidationOrder {}

    @NotEmpty(groups = ListSize.class)
    @Size(max = MAX_LINE_ITEMS, groups = ListSize.class)
    private List<@NotNull @Valid OrderLineItemsDto> orderLineItemsDtoList;
}
