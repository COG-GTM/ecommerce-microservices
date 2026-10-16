package com.ibatulanand.notificationservice.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibatulanand.notificationservice.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptRendererTest {

    private final ReceiptRenderer renderer = new ReceiptRenderer("UTC");

    private OrderPlacedEvent golden() throws IOException {
        return new ObjectMapper().readValue(new ClassPathResource("events/golden-cart-v2.json").getInputStream(),
                OrderPlacedEvent.class);
    }

    @Test
    void goldenCartReceiptShowsPre1Numbers() throws IOException {
        String receipt = renderer.render(golden());

        assertThat(receipt)
                .contains("Store 1969   Register 04", "Associate A-4471", "2026-10-16 21:30 UTC",
                        "Order POS-1969-04-GOLDEN")
                .contains("Vintage Soft Crewneck Tee", "Heather Grey / L", "268341-016-L", "** FINAL SALE - NO RETURNS **")
                .contains("Medium Indigo / 29 Reg", "471902-004-29", "Navy Uniform / M", "512884-022-M")
                .containsPattern("Merchandise \\(3 items\\) +159\\.85")
                .containsPattern("Markdowns +-29\\.96")
                .containsPattern("Promo FALL30 +-33\\.58")
                .containsPattern("ALT / Alterations +12\\.00")
                .containsPattern("Taxable subtotal +108\\.31")
                .containsPattern("Sales tax 8\\.625% +9\\.34")
                .containsPattern("TOTAL +117\\.65")
                .containsPattern("Credit/Debit +117\\.65")
                .contains("You saved $63.54 today")
                .doesNotContain("jordan.lee@example.com");
        assertThat(receipt.lines()).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(ReceiptRenderer.WIDTH));
        assertThat(receipt.lines().filter(line -> line.contains("FINAL SALE")).count()).isEqualTo(1);
    }

    @Test
    void taxExemptReceiptSaysTaxExempt() throws IOException {
        OrderPlacedEvent event = golden();
        event.setTaxExempt(true);
        event.getTotals().setTaxExempt(true);
        event.getTotals().setSalesTax(BigDecimal.ZERO);
        event.getTotals().setTotal(new BigDecimal("108.31"));

        String receipt = renderer.render(event);

        assertThat(receipt).containsPattern("Tax exempt +0\\.00").containsPattern("TOTAL +108\\.31")
                .doesNotContain("Sales tax");
    }

    @Test
    void v1ShapedEventDoesNotBlowUp() {
        assertThat(renderer.render(new OrderPlacedEvent("ORD-1"))).contains("Order ORD-1", "TOTAL");
    }
}
