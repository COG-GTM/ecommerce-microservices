package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Mirrors {@code types.ts#Promotion}. Seeded with {@code FALL30} and {@code CARD10}. */
@Entity
@Table(name = "t_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {
    @Id
    private String code;
    private String description;
    @Column(precision = 5, scale = 2)
    private BigDecimal percentOff;
    @Column(precision = 12, scale = 2)
    private BigDecimal amountOff;
    private boolean active;
}
