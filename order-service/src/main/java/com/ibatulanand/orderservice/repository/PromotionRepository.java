package com.ibatulanand.orderservice.repository;

import com.ibatulanand.orderservice.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository extends JpaRepository<Promotion, String> {
}
