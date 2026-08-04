package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String phone;

    @Column
    private String area;

    // Wholesale/invoicing fields — the Ventura import resolves customers by code first
    @Column(name = "customer_code", unique = true)
    private String customerCode;

    @Column
    private String address;

    @Column
    private String tier;

    @Column(name = "shop_type")
    private String shopType;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
