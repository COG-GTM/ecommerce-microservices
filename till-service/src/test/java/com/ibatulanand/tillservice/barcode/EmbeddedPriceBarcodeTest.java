package com.ibatulanand.tillservice.barcode;

import com.ibatulanand.tillservice.web.TillException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddedPriceBarcodeTest {

    @Test
    void parsesPluAndPriceFromPrefix2Ean13() {
        EmbeddedPriceBarcode label = EmbeddedPriceBarcode.parse("2002101003490").orElseThrow();
        assertThat(label.plu()).isEqualTo("2101");
        assertThat(label.pricePence()).isEqualTo(349);
    }

    @Test
    void computesGs1CheckDigit() {
        assertThat(EmbeddedPriceBarcode.checkDigit("200210100349")).isEqualTo(0);
        assertThat(EmbeddedPriceBarcode.checkDigit("500015940989")).isEqualTo(6);
        assertThat(EmbeddedPriceBarcode.checkDigit("200220100000")).isEqualTo(9);
    }

    @Test
    void rejectsInvalidCheckDigitWithClearError() {
        assertThatThrownBy(() -> EmbeddedPriceBarcode.parse("2002101003491"))
                .isInstanceOf(TillException.class)
                .hasMessageContaining("invalid check digit")
                .extracting("code").isEqualTo("INVALID_CHECK_DIGIT");
    }

    @Test
    void ignoresOrdinaryBarcodes() {
        assertThat(EmbeddedPriceBarcode.parse("1001")).isEmpty();
        assertThat(EmbeddedPriceBarcode.parse("5000159409896")).isEmpty();
        assertThat(EmbeddedPriceBarcode.parse("200210100349")).isEmpty();
        assertThat(EmbeddedPriceBarcode.parse("2101")).isEmpty();
    }
}
