package com.ibatulanand.productservice.pricing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the legacy price bridge's fixed-width file (layout in docs/morrisons-till/legacy/README.md).
 *
 * <pre>
 * H yyyyMMdd store(4) source(20)
 * D barcode(13) skuCode(20) pricePence(7, zero padded) effectiveDate(8)
 * T detailCount(7, zero padded)
 * </pre>
 */
public final class LegacyPriceFileParser {

    static final int DETAIL_LENGTH = 49;

    private LegacyPriceFileParser() {
    }

    public static List<PriceRecord> parse(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        List<PriceRecord> records = new ArrayList<>();
        boolean header = false;
        Integer trailerCount = null;
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            if (trailerCount != null) {
                throw invalid(lineNumber, "record after trailer");
            }
            switch (line.charAt(0)) {
                case 'H' -> {
                    if (header || lineNumber != 1) {
                        throw invalid(lineNumber, "header must be the first record");
                    }
                    header = true;
                }
                case 'D' -> {
                    if (!header) {
                        throw invalid(lineNumber, "detail before header");
                    }
                    records.add(detail(line, lineNumber));
                }
                case 'T' -> trailerCount = number(line, 1, 8, lineNumber, "trailer count");
                default -> throw invalid(lineNumber, "unknown record type '" + line.charAt(0) + "'");
            }
        }
        if (!header) {
            throw invalid(0, "missing header");
        }
        if (trailerCount == null) {
            throw invalid(lineNumber, "missing trailer");
        }
        if (trailerCount != records.size()) {
            throw invalid(lineNumber, "trailer count " + trailerCount + " but " + records.size() + " detail records");
        }
        return List.copyOf(records);
    }

    private static PriceRecord detail(String line, int lineNumber) {
        if (line.length() < DETAIL_LENGTH) {
            throw invalid(lineNumber, "detail record is " + line.length() + " chars, expected " + DETAIL_LENGTH);
        }
        String barcode = line.substring(1, 14).trim();
        String skuCode = line.substring(14, 34).trim();
        if (barcode.isEmpty()) {
            throw invalid(lineNumber, "blank barcode");
        }
        int pricePence = number(line, 34, 41, lineNumber, "price");
        return new PriceRecord(barcode, skuCode, pricePence);
    }

    private static int number(String line, int from, int to, int lineNumber, String field) {
        if (line.length() < to) {
            throw invalid(lineNumber, field + " is truncated");
        }
        String value = line.substring(from, to);
        if (!value.chars().allMatch(Character::isDigit)) {
            throw invalid(lineNumber, field + " '" + value + "' is not numeric");
        }
        return Integer.parseInt(value);
    }

    private static IllegalArgumentException invalid(int lineNumber, String reason) {
        return new IllegalArgumentException("Legacy price file line " + lineNumber + ": " + reason);
    }
}
