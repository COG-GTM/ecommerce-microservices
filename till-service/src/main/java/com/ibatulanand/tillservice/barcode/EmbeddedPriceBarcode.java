package com.ibatulanand.tillservice.barcode;

import com.ibatulanand.tillservice.web.TillException;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Price-embedded EAN-13 labels for loose and counter items (GS1 restricted prefix 2):
 * {@code 2 IIIIII PPPPP C} = prefix, 6-digit item code (the catalogue PLU, zero padded), price in pence, check digit.
 * Example: {@code 2002101003490} is PLU 2101 at £3.49.
 */
public record EmbeddedPriceBarcode(String barcode, String plu, int pricePence) {

    private static final Pattern EAN13_PREFIX_2 = Pattern.compile("2\\d{12}");

    public static boolean isCandidate(String barcode) {
        return barcode != null && EAN13_PREFIX_2.matcher(barcode).matches();
    }

    /** Empty unless the barcode is a prefix-2 EAN-13; throws INVALID_CHECK_DIGIT when its check digit is wrong. */
    public static Optional<EmbeddedPriceBarcode> parse(String barcode) {
        if (!isCandidate(barcode)) {
            return Optional.empty();
        }
        int expected = checkDigit(barcode.substring(0, 12));
        if (expected != barcode.charAt(12) - '0') {
            throw new TillException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CHECK_DIGIT",
                    "Barcode " + barcode + " has an invalid check digit. Rescan the label or key the item.");
        }
        String plu = barcode.substring(1, 7).replaceFirst("^0+(?=.)", "");
        return Optional.of(new EmbeddedPriceBarcode(barcode, plu, Integer.parseInt(barcode.substring(7, 12))));
    }

    /** GS1 mod-10 check digit for the first 12 digits of an EAN-13. */
    public static int checkDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = first12.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - sum % 10) % 10;
    }
}
