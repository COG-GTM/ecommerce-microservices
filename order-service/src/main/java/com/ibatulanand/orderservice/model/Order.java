package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String channel;
    private String storeNumber;
    private String tillId;
    private String transactionId;
    private String tenderType;
    private Integer totalPence;
    /** Client-supplied key (e.g. an offline till sale id) so a replayed sale is stored once. */
    @Column(unique = true)
    private String idempotencyKey;
    @OneToMany(cascade = CascadeType.ALL)
    private List<OrderLineItems> orderLineItemsList;
}
