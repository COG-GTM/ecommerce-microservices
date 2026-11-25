package com.ibatulanand.productservice.pricing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The legacy price-file path: imports the nightly fixed-width file at startup (after the catalogue seeders) and
 * on {@code pricing.legacy-cron}. Retired when {@code pricing.source=price-feed}.
 */
@Component
@Slf4j
public class LegacyPriceFileImporter {

    private final PriceFeedService priceFeedService;
    private final PricingProperties properties;
    private final ResourceLoader resourceLoader;

    public LegacyPriceFileImporter(PriceFeedService priceFeedService, PricingProperties properties,
                                   ResourceLoader resourceLoader) {
        this.priceFeedService = priceFeedService;
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void importAtStartup() {
        if (properties.source() == PriceSource.LEGACY_FILE) {
            runSafely();
        } else {
            log.info("Legacy price file retired (pricing.source={}); prices come from the price feed", properties.source());
        }
    }

    @Scheduled(cron = "${pricing.legacy-cron:0 0 2 * * *}")
    public void nightly() {
        if (properties.source() == PriceSource.LEGACY_FILE) {
            runSafely();
        }
    }

    /** Runs the import now. Rejected once the file path is retired. */
    public PriceFeedService.ImportResult importNow() {
        if (properties.source() != PriceSource.LEGACY_FILE) {
            throw new PricingException(HttpStatus.CONFLICT, "LEGACY_PRICE_FILE_RETIRED",
                    "The legacy price file is retired; prices come from the price feed");
        }
        Resource file = resourceLoader.getResource(properties.legacyFile());
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.US_ASCII)) {
            PriceFeedService.ImportResult result = priceFeedService.importLegacy(LegacyPriceFileParser.parse(reader));
            log.info("Legacy price file {} imported: {} prices applied", properties.legacyFile(), result.applied());
            return result;
        } catch (IOException e) {
            log.error("Cannot read legacy price file {}", properties.legacyFile(), e);
            throw new PricingException(HttpStatus.INTERNAL_SERVER_ERROR, "LEGACY_PRICE_FILE_UNREADABLE",
                    "Cannot read the legacy price file");
        } catch (IllegalArgumentException e) {
            throw new PricingException(HttpStatus.UNPROCESSABLE_ENTITY, "LEGACY_PRICE_FILE_INVALID", e.getMessage());
        }
    }

    private void runSafely() {
        try {
            importNow();
        } catch (RuntimeException e) {
            log.error("Legacy price file import failed: {}", e.getMessage());
        }
    }
}
