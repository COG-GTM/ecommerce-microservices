package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "t_orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_number")
    private String orderNumber;
    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "t_orders_order_line_items_list",
            joinColumns = @JoinColumn(name = "order_id"),
            inverseJoinColumns = @JoinColumn(name = "order_line_items_list_id"),
            // Reproduces the pre-migration generated schema constraint name.
            uniqueConstraints = @UniqueConstraint(
                    name = "UK_nqpoocsk2utvq7va8bgth1mj9",
                    columnNames = "order_line_items_list_id"))
    private List<OrderLineItems> orderLineItemsList;

    public Order() {
    }

    public Order(Long id, String orderNumber, List<OrderLineItems> orderLineItemsList) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.orderLineItemsList = orderLineItemsList;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public List<OrderLineItems> getOrderLineItemsList() {
        return orderLineItemsList;
    }

    public void setOrderLineItemsList(List<OrderLineItems> orderLineItemsList) {
        this.orderLineItemsList = orderLineItemsList;
    }
}
