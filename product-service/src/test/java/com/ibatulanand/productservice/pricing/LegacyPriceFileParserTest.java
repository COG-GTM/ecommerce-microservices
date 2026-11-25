package com.ibatulanand.productservice.pricing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyPriceFileParserTest {

    private static final String HEADER = "H202611240417LEGACY-PRICE-BRIDGE \n";

    @Test
    void parsesTheSampleLegacyFile() throws IOException {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/legacy/PRICEFILE.DAT")), StandardCharsets.US_ASCII)) {
            List<PriceRecord> records = LegacyPriceFileParser.parse(reader);
            assertThat(records).hasSize(11);
            assertThat(records.get(0)).isEqualTo(new PriceRecord("1001", "MOR-MILK-SS-4PT", 155));
            assertThat(records).contains(new PriceRecord("4003", "MOR-DIGESTIVE-400", 99));
        }
    }

    @Test
    void rejectsTrailerCountMismatch() {
        String file = HEADER + "D1001         MOR-MILK-SS-4PT     000015520261125\nT0000002\n";
        assertThatThrownBy(() -> LegacyPriceFileParser.parse(new StringReader(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trailer count 2 but 1");
    }

    @Test
    void rejectsNonNumericPrice() {
        String file = HEADER + "D1001         MOR-MILK-SS-4PT     00001X520261125\nT0000001\n";
        assertThatThrownBy(() -> LegacyPriceFileParser.parse(new StringReader(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 2: price");
    }

    @Test
    void rejectsTruncatedDetailAndMissingTrailer() {
        assertThatThrownBy(() -> LegacyPriceFileParser.parse(new StringReader(HEADER + "D1001  MOR\nT0000001\n")))
                .hasMessageContaining("expected 49");
        assertThatThrownBy(() -> LegacyPriceFileParser.parse(new StringReader(HEADER)))
                .hasMessageContaining("missing trailer");
        assertThatThrownBy(() -> LegacyPriceFileParser.parse(new StringReader("T0000000\n")))
                .hasMessageContaining("missing header");
    }
}
