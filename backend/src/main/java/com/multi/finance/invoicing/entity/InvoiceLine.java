package com.multi.finance.invoicing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "inv_invoice_lines")
@Getter @Setter
public class InvoiceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    // Denormalised brand reference for engine grouping without joining through item
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false)
    private Integer qty;

    // Snapshots at save time — never recalculated from live master data
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "margin_pct", precision = 5, scale = 2)
    private BigDecimal marginPct;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal wsp;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal value; // wsp × qty

    // Slab discount % applied to this line's brand group at save time — audit snapshot
    @Column(name = "applied_discount_pct", precision = 5, scale = 2)
    private BigDecimal appliedDiscountPct;

    // Sort order within invoice (preserves brand-group ordering for PDF)
    @Column(name = "sort_order")
    private Integer sortOrder;
}
