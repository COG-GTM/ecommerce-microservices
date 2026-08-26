package com.ibatulanand.productservice.service;

import com.ibatulanand.productservice.dto.ProductRequest;
import com.ibatulanand.productservice.dto.ProductResponse;
import com.ibatulanand.productservice.model.Product;
import com.ibatulanand.productservice.repository.ProductRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ProductService {

    private final ProductRepository productRepository;
    private static final Logger LOG = Logger.getLogger(ProductService.class);

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void createProduct(ProductRequest productRequest) {
        Product product = new Product(
                productRequest.getName(),
                productRequest.getDescription(),
                productRequest.getPrice());
        productRepository.persist(product);
        LOG.infof("Product %s is saved", product.id);
    }

    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll().list();
        return products.stream().map(this::mapToProductResponse).toList();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return new ProductResponse(
                product.id == null ? null : product.id.toHexString(),
                product.getName(),
                product.getDescription(),
                product.getPrice());
    }
}
