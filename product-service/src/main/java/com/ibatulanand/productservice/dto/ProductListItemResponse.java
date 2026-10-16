package com.ibatulanand.productservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;

/** A {@code GET /api/product} list item: always carries the legacy {@code id} and {@code price}, even when null. */
public class ProductListItemResponse extends ProductResponse {

    public static ProductListItemResponse of(ProductResponse source) {
        ProductListItemResponse item = new ProductListItemResponse();
        BeanUtils.copyProperties(source, item);
        return item;
    }

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String getId() {
        return super.getId();
    }

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public BigDecimal getPrice() {
        return super.getPrice();
    }
}
