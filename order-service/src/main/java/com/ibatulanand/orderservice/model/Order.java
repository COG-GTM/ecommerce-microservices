package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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
    private String orderNumber;
    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderLineItems> orderLineItemsList;

    private String storeId;
    private String registerId;
    private String associateId;
    private String lane;

    private BigDecimal merchandiseTotal;
    private BigDecimal servicesAndFees;
    private BigDecimal discountTotal;
    private BigDecimal taxableSubtotal;
    private BigDecimal taxRate;
    private BigDecimal salesTax;
    private BigDecimal total;
    private BigDecimal savedToday;
    private boolean taxExempt;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Tender> tenders;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Promotion> promotions;
}
