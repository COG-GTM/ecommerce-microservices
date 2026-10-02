package com.ibatulanand.productservice.repository;

import com.ibatulanand.productservice.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findBySkuCode(String skuCode);
}
