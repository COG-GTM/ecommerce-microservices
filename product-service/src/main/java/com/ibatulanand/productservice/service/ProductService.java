package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
                .finalSale(productRequest.getFinalSale())
                .build();

        productRepository.save(product);
        log.info("Product {} is saved", product.getId());
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(this::mapToProductResponse).toList();
    }

    public List<ProductResponse> getProductsBySkuCode(String skuCode) {
        return productRepository.findBySkuCode(skuCode).stream().map(this::mapToProductResponse).toList();
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
                .finalSale(product.getFinalSale())
                .build();
    }
}
