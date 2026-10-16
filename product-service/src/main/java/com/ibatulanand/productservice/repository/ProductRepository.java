package com.ibatulanand.productservice.repository;

import com.ibatulanand.productservice.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findByVariantsSkuCode(String skuCode);

    Optional<Product> findByStyleId(String styleId);
}
