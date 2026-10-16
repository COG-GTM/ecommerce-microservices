package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "t_order_line_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineItems {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String skuCode;
    /** Legacy column, always equal to {@link #unitPrice}. */
    private BigDecimal price;
    private Integer quantity;
    private String styleId;
    private String description;
    private String colorName;
    private String size;
    @Column(precision = 12, scale = 2)
    private BigDecimal listPrice;
    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(precision = 12, scale = 2)
    private BigDecimal markdownAmount;
    @Column(precision = 5, scale = 2)
    private BigDecimal clearancePercent;
    private boolean finalSale;
    @Column(precision = 12, scale = 2)
    private BigDecimal extendedPrice;
    @Enumerated(EnumType.STRING)
    private FulfillmentType fulfillmentType;
    private String fulfillmentStoreId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_line_item_id")
    private List<OrderLinePromotion> promotions = new ArrayList<>();
}
