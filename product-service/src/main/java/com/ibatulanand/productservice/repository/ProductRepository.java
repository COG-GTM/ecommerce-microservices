package com.ibatulanand.productservice.repository;

import com.ibatulanand.productservice.model.Product;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductRepository implements PanacheMongoRepository<Product> {
}
