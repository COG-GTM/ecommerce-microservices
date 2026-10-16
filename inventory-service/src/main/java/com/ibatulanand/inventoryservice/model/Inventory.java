package com.ibatulanand.inventoryservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_sku_store", columnNames = {"sku_code", "store_id"}))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sku_code", nullable = false)
    private String skuCode;
    @Column(name = "store_id", nullable = false, length = 16)
    private String storeId;
    @Column(nullable = false)
    private Integer onHand;
    private String floor;
    private String fixture;
    @Builder.Default
    @Column(nullable = false)
    private boolean shipEligible = true;
}
