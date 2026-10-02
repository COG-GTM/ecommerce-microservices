package com.ibatulanand.inventoryservice.repository;

import com.ibatulanand.inventoryservice.model.StoreDistance;
import com.ibatulanand.inventoryservice.model.StoreDistanceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreDistanceRepository extends JpaRepository<StoreDistance, StoreDistanceId> {
    List<StoreDistance> findByFromStoreIdAndDistanceMilesLessThanEqualOrderByDistanceMilesAsc(
            String fromStoreId, Double maxMiles);
}
