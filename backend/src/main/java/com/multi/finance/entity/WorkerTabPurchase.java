package com.multi.finance.entity;

import com.multi.finance.enums.WorkerFinanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "worker_tab_purchases")
public class WorkerTabPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private SalaryRecipient recipient;

    /** Legacy single-item columns — nullable since V26 (items now in worker_tab_purchase_items). */
    @Column
    private String description;

    @Column
    private BigDecimal quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false)
    private String month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerFinanceStatus status;

    @Column
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by_id", nullable = false)
    private User enteredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_reviewed_by")
    private User ownerReviewedBy;

    @Column(name = "owner_reviewed_at")
    private LocalDateTime ownerReviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_confirmed_by")
    private User adminConfirmedBy;

    @Column(name = "admin_confirmed_at")
    private LocalDateTime adminConfirmedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WorkerTabPurchaseItem> items;
}
