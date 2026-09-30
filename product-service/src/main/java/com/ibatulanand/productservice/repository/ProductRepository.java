package com.ibatulanand.productservice.repository;

import com.ibatulanand.productservice.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    boolean existsBySkuCode(String skuCode);

    List<Product> findBySkuCodeIn(Collection<String> skuCodes);
}
