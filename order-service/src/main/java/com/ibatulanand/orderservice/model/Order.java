package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "t_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String orderNumber;

    private String storeId;
    private String registerId;
    private String associateId;
    private String status;
    private boolean taxExempt;
    private Instant createdAt;

    @Column(precision = 12, scale = 2)
    private BigDecimal merchandiseTotal;
    @Column(precision = 12, scale = 2)
    private BigDecimal servicesAndFees;
    @Column(precision = 12, scale = 2)
    private BigDecimal discountTotal;
    @Column(precision = 12, scale = 2)
    private BigDecimal taxableSubtotal;
    @Column(precision = 7, scale = 5)
    private BigDecimal taxRate;
    @Column(precision = 12, scale = 2)
    private BigDecimal salesTax;
    @Column(precision = 12, scale = 2)
    private BigDecimal total;
    @Column(precision = 12, scale = 2)
    private BigDecimal savedToday;
    @Column(precision = 12, scale = 2)
    private BigDecimal changeDue;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderLineItems> orderLineItemsList;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderPromotion> promotions;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderTender> tenders;
}
