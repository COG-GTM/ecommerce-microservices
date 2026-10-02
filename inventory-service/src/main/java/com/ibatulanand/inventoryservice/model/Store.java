package com.ibatulanand.inventoryservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "t_store")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Store {
    @Id
    @Column(name = "store_id")
    private String storeId;
    private String name;
}
