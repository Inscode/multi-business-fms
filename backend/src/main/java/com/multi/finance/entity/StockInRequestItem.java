package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stock_in_request_items")
public class StockInRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_in_request_id", nullable = false)
    private StockInRequest stockInRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ReturnProduct product;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
