package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** The share of one order-level promotion allocated to one line. */
@Entity
@Table(name = "t_order_line_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderLinePromotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String promotionCode;
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    public OrderLinePromotion(String promotionCode, BigDecimal amount) {
        this.promotionCode = promotionCode;
        this.amount = amount;
    }
}
