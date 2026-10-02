package com.ibatulanand.inventoryservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_store_distance")
@IdClass(StoreDistanceId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreDistance {
    @Id
    private String fromStoreId;
    @Id
    private String toStoreId;
    @Column(nullable = false)
    private Double distanceMiles;
}
