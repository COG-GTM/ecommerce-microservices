package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.dto.ProductVariantDto;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.model.ProductVariant;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    public void createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .styleId(productRequest.getStyleId())
                .skuCode(productRequest.getSkuCode())
                .department(productRequest.getDepartment())
                .category(productRequest.getCategory())
                .colorName(productRequest.getColorName())
                .colorCode(productRequest.getColorCode())
                .size(productRequest.getSize())
                .listPrice(productRequest.getListPrice())
                .salePrice(productRequest.getSalePrice())
                .clearancePercent(productRequest.getClearancePercent())
                .finalSale(productRequest.isFinalSale())
                .variants(mapToVariants(productRequest.getVariants()))
                .build();

        productRepository.save(product);
        log.info("Product {} is saved", product.getId());
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::mapToProductResponse).toList();
    }

    public Optional<ProductResponse> getProductBySkuCode(String skuCode) {
        return productRepository.findBySkuCode(skuCode)
                .or(() -> productRepository.findByVariantsSkuCode(skuCode))
                .map(product -> mapToProductResponse(resolveVariant(product, skuCode)));
    }

    /**
     * Returns the style with the top-level item attributes overlaid from the variant matching
     * the scanned sku, so the POS gets a single item-level view of a style-color-size.
     */
    private Product resolveVariant(Product product, String skuCode) {
        if (skuCode.equals(product.getSkuCode()) || product.getVariants() == null) {
            return product;
        }
        return product.getVariants().stream()
                .filter(variant -> skuCode.equals(variant.getSkuCode()))
                .findFirst()
                .map(variant -> Product.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .description(product.getDescription())
                        .price(product.getPrice())
                        .styleId(product.getStyleId())
                        .skuCode(variant.getSkuCode())
                        .department(product.getDepartment())
                        .category(product.getCategory())
                        .colorName(variant.getColorName())
                        .colorCode(variant.getColorCode())
                        .size(variant.getSize())
                        .listPrice(variant.getListPrice())
                        .salePrice(variant.getSalePrice())
                        .clearancePercent(variant.getClearancePercent())
                        .finalSale(variant.isFinalSale())
                        .variants(product.getVariants())
                        .build())
                .orElse(product);
    }

    private List<ProductVariant> mapToVariants(List<ProductVariantDto> variantDtos) {
        if (variantDtos == null) {
            return null;
        }
        return variantDtos.stream()
                .map(variantDto -> ProductVariant.builder()
                        .skuCode(variantDto.getSkuCode())
                        .colorName(variantDto.getColorName())
                        .colorCode(variantDto.getColorCode())
                        .size(variantDto.getSize())
                        .listPrice(variantDto.getListPrice())
                        .salePrice(variantDto.getSalePrice())
                        .clearancePercent(variantDto.getClearancePercent())
                        .finalSale(variantDto.isFinalSale())
                        .build())
                .toList();
    }

    private List<ProductVariantDto> mapToVariantDtos(List<ProductVariant> variants) {
        if (variants == null) {
            return null;
        }
        return variants.stream()
                .map(variant -> ProductVariantDto.builder()
                        .skuCode(variant.getSkuCode())
                        .colorName(variant.getColorName())
                        .colorCode(variant.getColorCode())
                        .size(variant.getSize())
                        .listPrice(variant.getListPrice())
                        .salePrice(variant.getSalePrice())
                        .clearancePercent(variant.getClearancePercent())
                        .finalSale(variant.isFinalSale())
                        .build())
                .toList();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .styleId(product.getStyleId())
                .skuCode(product.getSkuCode())
                .department(product.getDepartment())
                .category(product.getCategory())
                .colorName(product.getColorName())
                .colorCode(product.getColorCode())
                .size(product.getSize())
                .listPrice(product.getListPrice())
                .salePrice(product.getSalePrice())
                .clearancePercent(product.getClearancePercent())
                .finalSale(product.isFinalSale())
                .variants(mapToVariantDtos(product.getVariants()))
                .build();
    }
}
