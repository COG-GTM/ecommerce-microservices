package com.ibatulanand.notificationservice.receipt;

import com.ibatulanand.notificationservice.OrderPlacedEvent;
import com.ibatulanand.notificationservice.OrderPlacedEvent.LineItem;
import com.ibatulanand.notificationservice.OrderPlacedEvent.Service;
import com.ibatulanand.notificationservice.OrderPlacedEvent.Tender;
import com.ibatulanand.notificationservice.OrderPlacedEvent.Totals;
import com.ibatulanand.notificationservice.support.LogText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Renders a plain-text store receipt from a v2 {@link OrderPlacedEvent} alone (no calls back to order-service). */
@Component
public class ReceiptRenderer {

    static final int WIDTH = 42;
    private static final String RULE = "-".repeat(WIDTH);
    private static final Map<String, String> TENDER_LABELS = Map.of(
            "CREDIT_DEBIT", "Credit/Debit",
            "GIFT_CARD", "Gift Card",
            "MOBILE_WALLET", "Mobile Wallet");

    private final DateTimeFormatter timestampFormat;

    public ReceiptRenderer(@Value("${notification.receipt.zone:UTC}") String zone) {
        this.timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.of(zone));
    }

    public String subject(OrderPlacedEvent event) {
        return "Your Gap receipt - order " + event.getOrderNumber();
    }

    public String render(OrderPlacedEvent event) {
        StringBuilder out = new StringBuilder();
        center(out, "GAP");
        center(out, "Store " + event.getStoreId() + "   Register " + event.getRegisterId());
        center(out, "Associate " + event.getAssociateId());
        center(out, formatPlacedAt(event.getPlacedAt()));
        center(out, "Order " + event.getOrderNumber());
        out.append(RULE).append('\n');

        List<LineItem> lines = nonNull(event.getLineItems());
        BigDecimal extendedTotal = BigDecimal.ZERO;
        int itemCount = 0;
        for (LineItem line : lines) {
            int qty = line.getQuantity() == null ? 1 : line.getQuantity();
            itemCount += qty;
            extendedTotal = extendedTotal.add(orZero(line.getExtendedPrice()));
            out.append(truncate(line.getDescription())).append('\n');
            row(out, "  " + joinNonBlank(line.getColorName(), line.getSize()), line.getSkuCode());
            row(out, "  " + qty + " @ " + money(line.getUnitPrice()), money(line.getExtendedPrice()));
            if ("SHIP_FROM_STORE".equals(line.getFulfillmentType())) {
                out.append(truncate("  Ships from store " + line.getFulfillmentStoreId())).append('\n');
            }
            if (line.isFinalSale()) {
                out.append("  ** FINAL SALE - NO RETURNS **\n");
            }
        }
        out.append(RULE).append('\n');

        Totals totals = event.getTotals() == null ? new Totals() : event.getTotals();
        BigDecimal merchandise = orZero(totals.getMerchandiseTotal());
        BigDecimal discount = orZero(totals.getDiscountTotal());
        BigDecimal markdowns = merchandise.subtract(extendedTotal).max(BigDecimal.ZERO);
        BigDecimal promotionTotal = discount.subtract(markdowns).max(BigDecimal.ZERO);
        List<String> promotions = nonNull(event.getPromotions());

        row(out, "Merchandise (" + itemCount + (itemCount == 1 ? " item)" : " items)"), money(merchandise));
        if (markdowns.signum() > 0) {
            row(out, "Markdowns", "-" + money(markdowns));
        }
        if (!promotions.isEmpty()) {
            row(out, "Promo " + String.join(", ", promotions), "-" + money(promotionTotal));
        }
        for (Service service : nonNull(event.getServices())) {
            row(out, joinNonBlank(service.getCode(), service.getDescription()), money(service.getAmount()));
        }
        row(out, "Taxable subtotal", money(totals.getTaxableSubtotal()));
        if (event.isTaxExempt() || totals.isTaxExempt()) {
            row(out, "Tax exempt", money(BigDecimal.ZERO));
        } else {
            row(out, "Sales tax " + percent(totals.getTaxRate()), money(totals.getSalesTax()));
        }
        row(out, "TOTAL", money(totals.getTotal()));
        out.append(RULE).append('\n');

        for (Tender tender : nonNull(event.getTenders())) {
            row(out, TENDER_LABELS.getOrDefault(tender.getType(), String.valueOf(tender.getType())), money(tender.getAmount()));
        }
        BigDecimal saved = totals.getSavedToday() != null ? totals.getSavedToday() : discount;
        if (saved.signum() > 0) {
            center(out, "You saved $" + money(saved) + " today");
        }
        center(out, "Thank you for shopping at Gap");
        return out.toString();
    }

    private String formatPlacedAt(String placedAt) {
        if (placedAt == null || placedAt.isBlank()) {
            return "";
        }
        try {
            return timestampFormat.format(Instant.parse(placedAt));
        } catch (DateTimeParseException e) {
            return placedAt;
        }
    }

    static String money(BigDecimal amount) {
        return orZero(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percent(BigDecimal rate) {
        if (rate == null) {
            return "";
        }
        return rate.movePointRight(2).setScale(3, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private static String joinNonBlank(String a, String b) {
        boolean hasA = a != null && !a.isBlank();
        boolean hasB = b != null && !b.isBlank();
        if (hasA && hasB) {
            return a + " / " + b;
        }
        return hasA ? a : hasB ? b : "";
    }

    private static String truncate(String text) {
        String value = text == null ? "" : LogText.oneLine(text);
        return value.length() > WIDTH ? value.substring(0, WIDTH) : value;
    }

    private static void row(StringBuilder out, String left, String right) {
        String r = right == null ? "" : LogText.oneLine(right);
        if (r.length() > WIDTH - 2) {
            r = r.substring(0, WIDTH - 2);
        }
        String cleanLeft = LogText.oneLine(left);
        int room = WIDTH - r.length() - 1;
        String l = cleanLeft.length() > room ? cleanLeft.substring(0, room) : cleanLeft;
        out.append(l).append(" ".repeat(WIDTH - l.length() - r.length())).append(r).append('\n');
    }

    private static void center(StringBuilder out, String text) {
        String t = truncate(text);
        int pad = (WIDTH - t.length()) / 2;
        out.append(" ".repeat(Math.max(0, pad))).append(t).append('\n');
    }
}
