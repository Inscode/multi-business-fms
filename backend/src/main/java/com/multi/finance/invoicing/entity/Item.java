package com.multi.finance.invoicing.entity;

import com.multi.finance.invoicing.enums.CategoryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "inv_items")
@Getter @Setter
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_code", nullable = false, unique = true)
    private String itemCode;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    // MRP (recommended retail price) — null for Plastic
    @Column(precision = 12, scale = 2)
    private BigDecimal mrp;

    // margin % override per item; falls back to brand.defaultMarginPct
    @Column(name = "margin_pct", precision = 5, scale = 2)
    private BigDecimal marginPct;

    // Flat wholesale price — used directly for Plastic, null for Rainco/Stationery
    @Column(name = "wholesale_price", precision = 12, scale = 2)
    private BigDecimal wholesalePrice;

    private boolean active = true;

    // Current stock level (units)
    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty = 0;

    // Free-issue scheme: buy freeIssueBuyQty → receive freeIssueFreeQty extra for free
    @Column(name = "free_issue_buy_qty")
    private Integer freeIssueBuyQty;

    @Column(name = "free_issue_free_qty")
    private Integer freeIssueFreeQty;
}
