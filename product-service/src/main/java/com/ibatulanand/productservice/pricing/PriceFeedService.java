package com.ibatulanand.productservice.pricing;

import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Applies prices from the active price source to the catalogue (the {@code product} collection). */
@Service
@Slf4j
public class PriceFeedService {

    static final int MAX_PRICE_PENCE = 9_999_999;
    static final int MAX_BATCH_SIZE = 10_000;

    /** {@code pricePence} is a {@link Number} so fractional values (399.9) are rejected rather than truncated by Jackson. */
    public record PriceUpdate(String barcode, Number pricePence) {
    }

    public record PriceSnapshot(PriceSource source, List<PriceRecord> prices) {
    }

    public record ImportResult(int applied, List<String> unknownBarcodes) {
    }

    private final ProductRepository productRepository;
    private final PricingProperties properties;

    public PriceFeedService(ProductRepository productRepository, PricingProperties properties) {
        this.productRepository = productRepository;
        this.properties = properties;
    }

    public PriceSource source() {
        return properties.source();
    }

    /** Current catalogue prices for every product with a barcode, in barcode order. */
    public PriceSnapshot snapshot() {
        List<PriceRecord> prices = productRepository.findAll().stream()
                .filter(p -> p.getBarcode() != null && p.getPricePence() != null)
                .sorted(Comparator.comparing(Product::getBarcode))
                .map(PriceFeedService::toRecord)
                .toList();
        return new PriceSnapshot(properties.source(), prices);
    }

    /**
     * Applies a price-feed batch. All-or-nothing: the whole batch is validated before any price is written.
     * Rejected while the legacy file is still the price source.
     */
    public List<PriceRecord> applyFeed(List<PriceUpdate> updates) {
        if (properties.source() != PriceSource.PRICE_FEED) {
            throw new PricingException(HttpStatus.CONFLICT, "PRICE_FEED_DISABLED",
                    "Prices come from the legacy price file; set pricing.source=price-feed to use the feed");
        }
        if (updates == null || updates.isEmpty()) {
            throw new PricingException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "No prices in the feed");
        }
        if (updates.size() > MAX_BATCH_SIZE) {
            throw new PricingException(HttpStatus.BAD_REQUEST, "BATCH_TOO_LARGE",
                    "A feed batch holds at most " + MAX_BATCH_SIZE + " prices");
        }
        List<Product> products = new ArrayList<>();
        for (PriceUpdate update : updates) {
            if (update == null || update.barcode() == null || update.barcode().isBlank()) {
                throw new PricingException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Every price needs a barcode");
            }
            Integer pence = wholePence(update.pricePence());
            if (pence == null || pence <= 0 || pence > MAX_PRICE_PENCE) {
                throw new PricingException(HttpStatus.BAD_REQUEST, "INVALID_PRICE",
                        "Price for " + update.barcode() + " must be 1-" + MAX_PRICE_PENCE + " pence");
            }
            Product product = productRepository.findFirstByBarcode(update.barcode()).orElseThrow(() ->
                    new PricingException(HttpStatus.NOT_FOUND, "UNKNOWN_BARCODE", "No product for barcode " + update.barcode()));
            products.add(withPrice(product, pence));
        }
        productRepository.saveAll(products);
        log.info("Price feed applied {} prices", products.size());
        return products.stream().map(PriceFeedService::toRecord).toList();
    }

    /** Writes legacy price-file records; barcodes the catalogue does not hold are skipped and reported. */
    public ImportResult importLegacy(List<PriceRecord> records) {
        List<String> unknown = new ArrayList<>();
        int applied = 0;
        for (PriceRecord record : records) {
            Product product = productRepository.findFirstByBarcode(record.barcode()).orElse(null);
            if (product == null) {
                unknown.add(record.barcode());
                continue;
            }
            if (!Objects.equals(product.getPricePence(), record.pricePence())) {
                productRepository.save(withPrice(product, record.pricePence()));
            }
            applied++;
        }
        if (!unknown.isEmpty()) {
            log.warn("Legacy price file listed {} barcodes not in the catalogue: {}", unknown.size(), unknown);
        }
        return new ImportResult(applied, List.copyOf(unknown));
    }

    /** The value as whole pence (425 or 425.0), or null when missing, fractional or out of int range. */
    private static Integer wholePence(Number raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static Product withPrice(Product product, int pricePence) {
        product.setPricePence(pricePence);
        product.setPrice(BigDecimal.valueOf(pricePence, 2));
        return product;
    }

    private static PriceRecord toRecord(Product product) {
        return new PriceRecord(product.getBarcode(), product.getSkuCode(), product.getPricePence());
    }
}
