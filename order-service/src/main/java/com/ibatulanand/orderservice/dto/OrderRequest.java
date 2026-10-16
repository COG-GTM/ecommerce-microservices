package com.ibatulanand.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Mirrors {@code pos-webapp/src/api/types.ts#OrderRequest} plus the POS-3 additions. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {
    private String storeId;
    private String registerId;
    private String associateId;
    @Builder.Default
    private List<OrderLineItemsDto> lineItems = new ArrayList<>();
    @Builder.Default
    private List<String> promotions = new ArrayList<>();
    @Builder.Default
    private List<TenderDto> tenders = new ArrayList<>();
    private boolean taxExempt;
    @Builder.Default
    private List<ServiceFeeDto> services = new ArrayList<>();
    private ReceiptDeliveryDto receiptDelivery;
    private String taxExemptCertificate;
}
