package com.ibatulanand.productservice.pricing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Modern price-feed API replacing the legacy price-file bridge. */
@RestController
@RequestMapping("/api/product/price-feed")
public class PriceFeedController {

    public record PriceFeedRequest(List<PriceFeedService.PriceUpdate> prices) {
    }

    private final PriceFeedService priceFeedService;
    private final LegacyPriceFileImporter legacyImporter;

    public PriceFeedController(PriceFeedService priceFeedService, LegacyPriceFileImporter legacyImporter) {
        this.priceFeedService = priceFeedService;
        this.legacyImporter = legacyImporter;
    }

    @GetMapping
    public PriceFeedService.PriceSnapshot prices() {
        return priceFeedService.snapshot();
    }

    @PutMapping
    public PriceFeedService.PriceSnapshot publish(@RequestBody PriceFeedRequest request) {
        return new PriceFeedService.PriceSnapshot(priceFeedService.source(),
                priceFeedService.applyFeed(request == null ? null : request.prices()));
    }

    /** Runs the legacy nightly import on demand (only while pricing.source=legacy-file). */
    @PostMapping("/legacy-import")
    public PriceFeedService.ImportResult legacyImport() {
        return legacyImporter.importNow();
    }

    @ExceptionHandler(PricingException.class)
    public ResponseEntity<Map<String, String>> rejected(PricingException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getCode(), "message", e.getMessage()));
    }
}
