package com.ibatulanand.inventoryservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_inventory", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_sku_store", columnNames = {"sku_code", "store_id"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sku_code")
    private String skuCode;
    @Column(name = "store_id")
    private String storeId;
    private Integer onHand;
    private boolean shipFromStoreEligible;
    private String floor;
    private String fixture;
}
