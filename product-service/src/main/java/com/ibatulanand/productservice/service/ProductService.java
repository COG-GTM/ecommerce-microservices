package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.dto.ProductVariantDto;
import com.ibatulanand.productservice.exception.ProductNotFoundException;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    public ProductResponse createProduct(ProductRequest productRequest) {
        var salePrice = productRequest.getSalePrice() != null
                ? productRequest.getSalePrice()
                : productRequest.getPrice();
        var listPrice = productRequest.getListPrice() != null
                ? productRequest.getListPrice()
                : salePrice;

        Product product = Product.builder()
                .styleId(productRequest.getStyleId())
                .skuCode(productRequest.getSkuCode())
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .department(productRequest.getDepartment())
                .category(productRequest.getCategory())
                .colorName(productRequest.getColorName())
                .colorCode(productRequest.getColorCode())
                .size(productRequest.getSize())
                .listPrice(listPrice)
                .salePrice(salePrice)
                .clearancePercent(productRequest.getClearancePercent() != null
                        ? productRequest.getClearancePercent() : 0)
                .finalSale(productRequest.getFinalSale() != null
                        ? productRequest.getFinalSale() : false)
                .price(salePrice)
                .build();

        productRepository.save(product);
        log.info("Product {} is saved", product.getId());
        return mapToProductResponse(product);
    }

    public List<ProductResponse> getAllProducts() {
        return mapToProductResponses(productRepository.findAll());
    }

    public List<ProductResponse> getProducts(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            return getAllProducts();
        }
        return productRepository.findBySkuCode(skuCode)
                .map(p -> mapToProductResponses(List.of(p)))
                .orElseGet(Collections::emptyList);
    }

    public ProductResponse getProductBySkuCode(String skuCode) {
        Product product = productRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new ProductNotFoundException(skuCode));
        List<ProductVariantDto> variants = variantsFor(product.getStyleId());
        return mapToProductResponse(product, variants);
    }

    private List<ProductVariantDto> variantsFor(String styleId) {
        if (styleId == null) {
            return Collections.emptyList();
        }
        return productRepository.findByStyleId(styleId).stream()
                .map(this::mapToVariant)
                .toList();
    }

    private List<ProductResponse> mapToProductResponses(List<Product> products) {
        List<String> styleIds = products.stream()
                .map(Product::getStyleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, List<ProductVariantDto>> variantsByStyleId = styleIds.isEmpty()
                ? Collections.emptyMap()
                : productRepository.findByStyleIdIn(styleIds).stream()
                        .collect(Collectors.groupingBy(
                                Product::getStyleId,
                                Collectors.mapping(this::mapToVariant, Collectors.toList())));

        return products.stream()
                .map(p -> mapToProductResponse(p,
                        p.getStyleId() == null
                                ? Collections.<ProductVariantDto>emptyList()
                                : variantsByStyleId.getOrDefault(p.getStyleId(), Collections.emptyList())))
                .toList();
    }

    private ProductVariantDto mapToVariant(Product product) {
        return ProductVariantDto.builder()
                .skuCode(product.getSkuCode())
                .colorName(product.getColorName())
                .colorCode(product.getColorCode())
                .size(product.getSize())
                .listPrice(product.getListPrice())
                .salePrice(product.getSalePrice())
                .build();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return mapToProductResponse(product, variantsFor(product.getStyleId()));
    }

    private ProductResponse mapToProductResponse(Product product, List<ProductVariantDto> variants) {
        return ProductResponse.builder()
                .id(product.getId())
                .styleId(product.getStyleId())
                .skuCode(product.getSkuCode())
                .name(product.getName())
                .description(product.getDescription())
                .department(product.getDepartment())
                .category(product.getCategory())
                .colorName(product.getColorName())
                .colorCode(product.getColorCode())
                .size(product.getSize())
                .listPrice(product.getListPrice())
                .salePrice(product.getSalePrice())
                .clearancePercent(product.getClearancePercent())
                .finalSale(product.getFinalSale())
                .price(product.getPrice())
                .variants(variants)
                .build();
    }
}
