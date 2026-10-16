package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "t_order_service_fees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderServiceFee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String description;
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    public OrderServiceFee(String code, String description, BigDecimal amount) {
        this.code = code;
        this.description = description;
        this.amount = amount;
    }
}
