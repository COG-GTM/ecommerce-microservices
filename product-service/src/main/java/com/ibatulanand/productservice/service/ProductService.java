package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.dto.ProductVariantResponse;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.model.ProductVariant;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private static final Pattern DASHED_SKU = Pattern.compile("^(\\d{6})-(\\d{3})-([A-Z0-9]{1,6})$");
    private static final Pattern DASHLESS_SKU = Pattern.compile("^(\\d{6})(\\d{3})([A-Z0-9]{1,6})$");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal CLEARANCE_TOLERANCE = new BigDecimal("0.01");

    private final ProductRepository productRepository;

    public void createProduct(ProductRequest productRequest) {
        List<ProductRequest.Variant> requestVariants =
                Optional.ofNullable(productRequest.getVariants()).orElse(List.of());
        List<FieldError> errors = new ArrayList<>();
        List<ProductVariant> variants = new ArrayList<>();
        Set<String> seenSkus = new HashSet<>();
        String styleId = trimToNull(productRequest.getStyleId());

        for (int i = 0; i < requestVariants.size(); i++) {
            ProductVariant variant = validateVariant(requestVariants.get(i), "variants[" + i + "]", errors);
            if (variant == null) {
                continue;
            }
            String variantStyle = variant.getSkuCode().substring(0, 6);
            if (styleId == null) {
                styleId = variantStyle;
            } else if (!styleId.equals(variantStyle)) {
                errors.add(new FieldError("variants[" + i + "].skuCode",
                        "skuCode " + variant.getSkuCode() + " does not belong to style " + styleId));
            }
            if (!seenSkus.add(variant.getSkuCode())) {
                errors.add(new FieldError("variants[" + i + "].skuCode",
                        "skuCode " + variant.getSkuCode() + " appears more than once"));
            }
            variants.add(variant);
        }
        if (!errors.isEmpty()) {
            throw new ProductValidationException(errors);
        }

        BigDecimal price = variants.isEmpty()
                ? money(productRequest.getPrice())
                : variants.get(0).getListPrice();

        Product product = Product.builder()
                .styleId(styleId)
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .department(productRequest.getDepartment())
                .category(productRequest.getCategory())
                .price(price)
                .variants(variants)
                .build();

        try {
            productRepository.save(product);
        } catch (DuplicateKeyException e) {
            throw new DuplicateSkuException(
                    variants.stream().map(ProductVariant::getSkuCode).toList(), e);
        }
        log.info("Product {} is saved", product.getId());
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::mapToListResponse).toList();
    }

    public ProductResponse getProductBySku(String rawSkuCode) {
        String skuCode = normalizeSkuCode(rawSkuCode);
        Product product = productRepository.findByVariantsSkuCode(skuCode)
                .orElseThrow(() -> new SkuNotFoundException(skuCode));
        ProductVariant scanned = product.getVariants().stream()
                .filter(v -> skuCode.equals(v.getSkuCode()))
                .findFirst()
                .orElseThrow(() -> new SkuNotFoundException(skuCode));
        return mapToSkuResponse(product, scanned);
    }

    /**
     * Normalizes a scanned hangtag to the canonical {@code <style>-<color>-<size>} form.
     * Accepts it with or without dashes, in any case, with surrounding whitespace.
     * Values that don't look like a hangtag are returned trimmed and upper-cased.
     */
    public static String normalizeSkuCode(String rawSkuCode) {
        if (rawSkuCode == null) {
            return "";
        }
        String compact = rawSkuCode.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (DASHED_SKU.matcher(compact).matches()) {
            return compact;
        }
        Matcher dashless = DASHLESS_SKU.matcher(compact.replace("-", ""));
        if (dashless.matches()) {
            return dashless.group(1) + "-" + dashless.group(2) + "-" + dashless.group(3);
        }
        return compact;
    }

    private ProductVariant validateVariant(ProductRequest.Variant request, String path, List<FieldError> errors) {
        int errorsBefore = errors.size();
        String skuCode = normalizeSkuCode(request.getSkuCode());
        if (!DASHED_SKU.matcher(skuCode).matches()) {
            errors.add(new FieldError(path + ".skuCode",
                    "skuCode must be a hangtag in the form <style>-<color>-<size>, e.g. 268341-016-L"));
        }

        BigDecimal listPrice = request.getListPrice();
        BigDecimal salePrice = request.getSalePrice() != null ? request.getSalePrice() : listPrice;
        int clearancePercent = request.getClearancePercent() != null ? request.getClearancePercent() : 0;

        if (listPrice == null || listPrice.signum() < 0) {
            errors.add(new FieldError(path + ".listPrice", "listPrice is required and must not be negative"));
        } else if (salePrice.signum() < 0) {
            errors.add(new FieldError(path + ".salePrice", "salePrice must not be negative"));
        } else if (salePrice.compareTo(listPrice) > 0) {
            errors.add(new FieldError(path + ".salePrice",
                    "salePrice " + salePrice + " must not exceed listPrice " + listPrice));
        }

        if (clearancePercent < 0 || clearancePercent > 100) {
            errors.add(new FieldError(path + ".clearancePercent",
                    "clearancePercent must be between 0 and 100"));
        } else if (clearancePercent > 0 && listPrice != null && listPrice.signum() >= 0) {
            BigDecimal expected = listPrice.multiply(HUNDRED.subtract(BigDecimal.valueOf(clearancePercent)))
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);
            if (salePrice.subtract(expected).abs().compareTo(CLEARANCE_TOLERANCE) > 0) {
                errors.add(new FieldError(path + ".salePrice",
                        "salePrice " + salePrice + " does not match listPrice " + listPrice + " less "
                                + clearancePercent + "% clearance (expected "
                                + expected.setScale(2, RoundingMode.HALF_UP) + " ± 0.01)"));
            }
        }

        if (errors.size() > errorsBefore) {
            return null;
        }
        return ProductVariant.builder()
                .skuCode(skuCode)
                .colorName(request.getColorName())
                .colorCode(request.getColorCode())
                .size(request.getSize())
                .listPrice(money(listPrice))
                .salePrice(money(salePrice))
                .clearancePercent(clearancePercent)
                .finalSale(Boolean.TRUE.equals(request.getFinalSale()))
                .build();
    }

    private ProductResponse mapToSkuResponse(Product product, ProductVariant scanned) {
        return ProductResponse.builder()
                .styleId(product.getStyleId())
                .skuCode(scanned.getSkuCode())
                .name(product.getName())
                .description(product.getDescription())
                .department(product.getDepartment())
                .category(product.getCategory())
                .colorName(scanned.getColorName())
                .colorCode(scanned.getColorCode())
                .size(scanned.getSize())
                .listPrice(money(scanned.getListPrice()))
                .salePrice(money(scanned.getSalePrice()))
                .clearancePercent(scanned.getClearancePercent())
                .finalSale(scanned.getFinalSale())
                .variants(otherVariants(product, scanned))
                .build();
    }

    private ProductResponse mapToListResponse(Product product) {
        List<ProductVariant> variants = Optional.ofNullable(product.getVariants()).orElse(List.of());
        ProductResponse response = variants.isEmpty()
                ? ProductResponse.builder()
                        .styleId(product.getStyleId())
                        .name(product.getName())
                        .description(product.getDescription())
                        .department(product.getDepartment())
                        .category(product.getCategory())
                        .build()
                : mapToSkuResponse(product, variants.get(0));
        response.setId(product.getId());
        response.setPrice(money(product.getPrice()));
        return response;
    }

    private List<ProductVariantResponse> otherVariants(Product product, ProductVariant scanned) {
        return product.getVariants().stream()
                .filter(v -> !v.getSkuCode().equals(scanned.getSkuCode()))
                .map(v -> ProductVariantResponse.builder()
                        .skuCode(v.getSkuCode())
                        .colorName(v.getColorName())
                        .colorCode(v.getColorCode())
                        .size(v.getSize())
                        .listPrice(money(v.getListPrice()))
                        .salePrice(money(v.getSalePrice()))
                        .build())
                .toList();
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record FieldError(String field, String message) {
    }

    @Getter
    public static class SkuNotFoundException extends RuntimeException {
        private final String skuCode;

        public SkuNotFoundException(String skuCode) {
            super("No product variant has skuCode " + skuCode);
            this.skuCode = skuCode;
        }
    }

    @Getter
    public static class ProductValidationException extends RuntimeException {
        private final List<FieldError> errors;

        public ProductValidationException(List<FieldError> errors) {
            super("Product request failed validation");
            this.errors = List.copyOf(errors);
        }
    }

    @Getter
    public static class DuplicateSkuException extends RuntimeException {
        private final List<String> skuCodes;

        public DuplicateSkuException(List<String> skuCodes, Throwable cause) {
            super("A variant skuCode already exists: " + skuCodes, cause);
            this.skuCodes = List.copyOf(skuCodes);
        }
    }
}
