package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, String> {
}
