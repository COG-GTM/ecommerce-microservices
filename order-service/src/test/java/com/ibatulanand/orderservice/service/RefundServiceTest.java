package com.ibatulanand.orderservice.service;

import com.ibatulanand.orderservice.dto.RefundDtos.RefundLineRequest;
import com.ibatulanand.orderservice.dto.RefundDtos.RefundRequest;
import com.ibatulanand.orderservice.dto.RefundDtos.RefundableSale;
import com.ibatulanand.orderservice.event.OrderPlacedEvent;
import com.ibatulanand.orderservice.model.Order;
import com.ibatulanand.orderservice.model.OrderLineItems;
import com.ibatulanand.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RefundServiceTest {

    OrderRepository repository = mock(OrderRepository.class);
    KafkaTemplate<String, OrderPlacedEvent> kafka = mock(KafkaTemplate.class);
    RefundService service = new RefundService(repository, kafka);
    List<Order> refunds = new ArrayList<>();
    Order sale;

    @BeforeEach
    void setUp() {
        sale = new Order();
        sale.setOrderNumber("SALE-1");
        sale.setChannel("TILL");
        sale.setStoreNumber("0123");
        sale.setTillId("T1");
        sale.setTransactionId("0123-T1-0001");
        sale.setTenderType("CARD");
        sale.setTotalPence(155 * 3 + 95);
        sale.setOrderLineItemsList(List.of(line("MOR-MILK", 155, 3), line("MOR-BANANA", 95, 1)));
        when(repository.findFirstByOrderNumber(anyString())).thenReturn(Optional.empty());
        when(repository.findFirstByOrderNumber("SALE-1")).thenReturn(Optional.of(sale));
        when(repository.findWithLockByOrderNumber("SALE-1")).thenReturn(Optional.of(sale));
        when(repository.findByTransactionIdOrderByIdDesc(anyString())).thenReturn(List.of());
        when(repository.findByTransactionIdOrderByIdDesc("0123-T1-0001")).thenReturn(List.of(sale));
        when(repository.findByOriginalOrderNumber("SALE-1")).thenReturn(refunds);
        when(repository.save(any(Order.class))).thenAnswer(inv -> {
            refunds.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    @Test
    void looksUpSaleByOrderReceiptOrTransactionNumber() {
        assertThat(service.refundable("SALE-1").orderNumber()).isEqualTo("SALE-1");
        assertThat(service.refundable("R-0123-T1-0001").orderNumber()).isEqualTo("SALE-1");
        assertThat(service.refundable("0123-T1-0001").lines()).hasSize(2);
        assertThatThrownBy(() -> service.refundable("NOPE"))
                .isInstanceOfSatisfying(RefundException.class, e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_ORDER"));
    }

    @Test
    void partialRefundIsANegativeOrderLinkedToTheSaleAndEmitsARefundEvent() {
        Order refund = service.refund("SALE-1", request(new RefundLineRequest("MOR-MILK", 2)));

        assertThat(refund.getOrderType()).isEqualTo("REFUND");
        assertThat(refund.getOriginalOrderNumber()).isEqualTo("SALE-1");
        assertThat(refund.getTotalPence()).isEqualTo(-310);
        assertThat(refund.getOrderLineItemsList()).singleElement()
                .satisfies(l -> assertThat(l.getQuantity()).isEqualTo(-2));
        ArgumentCaptor<OrderPlacedEvent> event = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(kafka).send(eq("notificationTopic"), event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("REFUND");
        assertThat(event.getValue().getOriginalOrderNumber()).isEqualTo("SALE-1");
        assertThat(event.getValue().getTotalPence()).isEqualTo(-310);

        RefundableSale remaining = service.refundable("SALE-1");
        assertThat(remaining.lines().get(0).refundableQuantity()).isEqualTo(1);
        assertThat(remaining.refundedPence()).isEqualTo(310);
        assertThat(remaining.refunds()).containsExactly(refund.getOrderNumber());
    }

    @Test
    void secondRefundUpToTheRemainingQuantityThenOverRefundIsRejected() {
        service.refund("SALE-1", request(new RefundLineRequest("MOR-MILK", 2)));
        service.refund("SALE-1", request(new RefundLineRequest("MOR-MILK", 1)));

        assertThatThrownBy(() -> service.refund("SALE-1", request(new RefundLineRequest("MOR-MILK", 1))))
                .isInstanceOfSatisfying(RefundException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("REFUND_EXCEEDS_SOLD");
                });
        assertThat(refunds).hasSize(2);
        assertThat(service.refundable("SALE-1").lines().get(0).refundableQuantity()).isZero();
    }

    @Test
    void duplicateLinesInOneRequestCountTogether() {
        assertThatThrownBy(() -> service.refund("SALE-1",
                request(new RefundLineRequest("MOR-MILK", 2), new RefundLineRequest("MOR-MILK", 2))))
                .isInstanceOfSatisfying(RefundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("REFUND_EXCEEDS_SOLD"));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidRequestsAndRefundsOfRefunds() {
        assertThatThrownBy(() -> service.refund("SALE-1", request()))
                .isInstanceOfSatisfying(RefundException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_REFUND"));
        assertThatThrownBy(() -> service.refund("SALE-1", request(new RefundLineRequest("MOR-MILK", 0))))
                .isInstanceOfSatisfying(RefundException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_REFUND"));
        assertThatThrownBy(() -> service.refund("SALE-1", request(new RefundLineRequest("MOR-BREAD", 1))))
                .isInstanceOfSatisfying(RefundException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_REFUND"));

        Order refund = service.refund("SALE-1", request(new RefundLineRequest("MOR-BANANA", 1)));
        when(repository.findFirstByOrderNumber(refund.getOrderNumber())).thenReturn(Optional.of(refund));
        assertThatThrownBy(() -> service.refundable(refund.getOrderNumber()))
                .isInstanceOfSatisfying(RefundException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_REFUNDABLE"));
    }

    @Test
    void receiptSharedByTwoSalesIsAmbiguous() {
        Order other = new Order();
        other.setOrderNumber("SALE-2");
        when(repository.findByTransactionIdOrderByIdDesc("0123-T1-0001")).thenReturn(List.of(other, sale));
        assertThatThrownBy(() -> service.refundable("R-0123-T1-0001"))
                .isInstanceOfSatisfying(RefundException.class, e -> assertThat(e.getCode()).isEqualTo("AMBIGUOUS_RECEIPT"));
        assertThat(service.refundable("SALE-1").orderNumber()).isEqualTo("SALE-1");
    }

    private static RefundRequest request(RefundLineRequest... lines) {
        return new RefundRequest("0123", "T2", "0123-T2-R0001", List.of(lines));
    }

    private static OrderLineItems line(String sku, int pence, int quantity) {
        OrderLineItems item = new OrderLineItems();
        item.setSkuCode(sku);
        item.setPrice(BigDecimal.valueOf(pence, 2));
        item.setQuantity(quantity);
        return item;
    }
}
