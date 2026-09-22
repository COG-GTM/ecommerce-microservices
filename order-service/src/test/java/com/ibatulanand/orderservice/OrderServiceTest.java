package com.ibatulanand.orderservice;

import com.ibatulanand.orderservice.dto.OrderLineItemsDto;
import com.ibatulanand.orderservice.dto.OrderRequest;
import com.ibatulanand.orderservice.dto.OrderResponse;
import com.ibatulanand.orderservice.dto.PromotionDto;
import com.ibatulanand.orderservice.dto.TenderDto;
import com.ibatulanand.orderservice.model.TenderType;
import com.ibatulanand.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the pricing rules of {@link OrderService} without the inventory/kafka collaborators.
 */
class OrderServiceTest {

    private final OrderService orderService = new OrderService(null, null, null);

    @Test
    void shouldComputeMerchandiseTotalTaxAndGrandTotal() {
        OrderRequest orderRequest = OrderRequest.builder()
                .storeId("1042")
                .registerId("03")
                .associateId("A-2291")
                .lane("2")
                .taxRate(new BigDecimal("0.0875"))
                .servicesAndFees(new BigDecimal("5.00"))
                .orderLineItemsDtoList(List.of(
                        lineItem("268341-016-L", new BigDecimal("49.95"), new BigDecimal("34.97"), 2),
                        lineItem("452119-400-32", new BigDecimal("79.95"), new BigDecimal("79.95"), 1)))
                .build();

        OrderResponse orderResponse = totalsOf(orderRequest);

        assertEquals(new BigDecimal("149.89"), orderResponse.getMerchandiseTotal());
        assertEquals(new BigDecimal("5.00"), orderResponse.getServicesAndFees());
        assertEquals(new BigDecimal("154.89"), orderResponse.getTaxableSubtotal());
        assertEquals(new BigDecimal("13.55"), orderResponse.getSalesTax());
        assertEquals(new BigDecimal("168.44"), orderResponse.getTotal());
        assertEquals(new BigDecimal("29.96"), orderResponse.getSavedToday());
        assertEquals(new BigDecimal("69.94"), orderResponse.getOrderLineItemsDtoList().get(0).getExtendedPrice());
        assertEquals(new BigDecimal("29.96"), orderResponse.getOrderLineItemsDtoList().get(0).getDiscountAmount());
    }

    @Test
    void shouldApplyOrderLevelPromotionsToTheTaxableSubtotal() {
        OrderRequest orderRequest = OrderRequest.builder()
                .taxRate(new BigDecimal("0.10"))
                .orderLineItemsDtoList(List.of(
                        lineItem("268341-016-L", new BigDecimal("100.00"), new BigDecimal("100.00"), 1)))
                .promotions(List.of(PromotionDto.builder()
                        .code("FALL30")
                        .description("30% off full-price")
                        .amount(new BigDecimal("30.00"))
                        .build()))
                .build();

        OrderResponse orderResponse = totalsOf(orderRequest);

        assertEquals(new BigDecimal("30.00"), orderResponse.getDiscountTotal());
        assertEquals(new BigDecimal("70.00"), orderResponse.getTaxableSubtotal());
        assertEquals(new BigDecimal("7.00"), orderResponse.getSalesTax());
        assertEquals(new BigDecimal("77.00"), orderResponse.getTotal());
        assertEquals(new BigDecimal("30.00"), orderResponse.getSavedToday());
    }

    @Test
    void shouldNotChargeTaxForTaxExemptTransactions() {
        OrderRequest orderRequest = OrderRequest.builder()
                .taxRate(new BigDecimal("0.0875"))
                .taxExempt(true)
                .orderLineItemsDtoList(List.of(
                        lineItem("268341-016-L", new BigDecimal("49.95"), new BigDecimal("49.95"), 1)))
                .build();

        OrderResponse orderResponse = totalsOf(orderRequest);

        assertEquals(new BigDecimal("0.00"), orderResponse.getSalesTax());
        assertEquals(new BigDecimal("49.95"), orderResponse.getTotal());
    }

    @Test
    void shouldAcceptSplitTenderCoveringTheTotal() {
        OrderRequest orderRequest = OrderRequest.builder()
                .taxRate(BigDecimal.ZERO)
                .orderLineItemsDtoList(List.of(
                        lineItem("268341-016-L", new BigDecimal("100.00"), new BigDecimal("100.00"), 1)))
                .tenders(List.of(
                        TenderDto.builder().type(TenderType.GIFT_CARD).amount(new BigDecimal("25.00")).build(),
                        TenderDto.builder().type(TenderType.CREDIT_DEBIT).amount(new BigDecimal("75.00")).build()))
                .build();

        OrderResponse orderResponse = totalsOf(orderRequest);

        assertEquals(new BigDecimal("100.00"), orderResponse.getTotal());
        assertEquals(List.of(TenderType.GIFT_CARD, TenderType.CREDIT_DEBIT),
                orderResponse.getTenders().stream().map(TenderDto::getType).toList());
    }

    @Test
    void shouldRejectTenderThatDoesNotCoverTheTotal() {
        OrderRequest orderRequest = OrderRequest.builder()
                .taxRate(BigDecimal.ZERO)
                .orderLineItemsDtoList(List.of(
                        lineItem("268341-016-L", new BigDecimal("100.00"), new BigDecimal("100.00"), 1)))
                .tenders(List.of(
                        TenderDto.builder().type(TenderType.GIFT_CARD).amount(new BigDecimal("25.00")).build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> totalsOf(orderRequest));
    }

    private OrderResponse totalsOf(OrderRequest orderRequest) {
        return orderService.priceOrder(orderRequest);
    }

    private OrderLineItemsDto lineItem(String skuCode, BigDecimal listPrice, BigDecimal unitPrice, int quantity) {
        return OrderLineItemsDto.builder()
                .skuCode(skuCode)
                .description("Ribbed Mock-Neck Tank")
                .colorName("Black")
                .size("L")
                .listPrice(listPrice)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .discountReason("Clearance 40% — final sale")
                .build();
    }
}
