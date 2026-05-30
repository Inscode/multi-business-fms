package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "summary_load_bill_products")
public class SummaryLoadBillItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "summary_load_bill_id", nullable = false)
    private SummaryLoadBill summaryLoadBill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ReturnProduct product;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
