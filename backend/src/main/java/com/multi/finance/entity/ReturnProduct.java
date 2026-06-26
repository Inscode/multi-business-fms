package com.multi.finance.entity;

import com.multi.finance.enums.BusinessType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "return_products")
public class ReturnProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessType business;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "is_stock_product", nullable = false)
    @Builder.Default
    private Boolean isStockProduct = true;

    @Column(name = "is_return_product", nullable = false)
    @Builder.Default
    private Boolean isReturnProduct = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}