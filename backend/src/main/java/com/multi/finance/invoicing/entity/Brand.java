package com.multi.finance.invoicing.entity;

import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.DiscountType;
import com.multi.finance.invoicing.enums.Principal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inv_brands")
@Getter @Setter
public class Brand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // e.g. "1304" — matches Ventura brand group code prefix
    @Column(name = "brand_code")
    private String brandCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Principal principal;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    // Default margin % applied to items in this brand (0 for Stationery, 18 for Rainco, null for Plastic)
    @Column(name = "default_margin_pct", precision = 5, scale = 2)
    private BigDecimal defaultMarginPct;

    private boolean active = true;

    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DiscountSlab> slabs = new ArrayList<>();
}
