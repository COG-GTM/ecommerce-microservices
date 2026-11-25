package com.ibatulanand.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One payment line of a till sale; a split tender has several. */
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
    private String tenderType;
    private Integer amountPence;
    private Integer tenderedPence;
    private Integer changeDuePence;
    private String authCode;
}
