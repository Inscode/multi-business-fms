package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_stock_links")
public class BillStockLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_bill_id", nullable = false)
    private Bill systemBill; // parent SYSTEM bill

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_bill_id", nullable = false)
    private Bill childBill; // DRAFT or MANUAL bill being matched

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by", nullable = false)
    private User linkedBy;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
