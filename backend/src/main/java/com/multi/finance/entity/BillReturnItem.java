package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_return_items")
public class BillReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_return_id", nullable = false)
    private BillReturn billReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ReturnProduct product;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "quantity_requested", nullable = false)
    private Integer quantityRequested;

    @Column(name = "quantity_returned")
    private Integer quantityReturned;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
}
