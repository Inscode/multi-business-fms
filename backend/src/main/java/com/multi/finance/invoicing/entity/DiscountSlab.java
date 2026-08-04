package com.multi.finance.invoicing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "inv_discount_slabs")
@Getter @Setter
public class DiscountSlab {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    // null min = 0, null max = unbounded (≥ min)
    @Column(name = "min_value", precision = 14, scale = 2)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 14, scale = 2)
    private BigDecimal maxValue;

    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPct;

    // Display order within the brand's slab table
    @Column(name = "sort_order")
    private Integer sortOrder;
}
