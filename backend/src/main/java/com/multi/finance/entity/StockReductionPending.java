package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_reduction_pending")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReductionPending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @OneToMany(mappedBy = "reduction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockReductionPendingItem> items = new ArrayList<>();

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
