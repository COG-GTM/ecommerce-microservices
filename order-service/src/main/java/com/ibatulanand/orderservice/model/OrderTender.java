package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Tender type, label and amount only. Card data is never accepted or stored. */
@Entity
@Table(name = "t_order_tenders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderTender {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private TenderType type;
    private String label;
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    public OrderTender(TenderType type, String label, BigDecimal amount) {
        this.type = type;
        this.label = label;
        this.amount = amount;
    }
}
