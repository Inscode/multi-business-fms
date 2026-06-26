package com.multi.finance.entity;

import com.multi.finance.enums.ReturnStatus;
import com.multi.finance.enums.ReturnType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_returns")
public class BillReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false)
    private ReturnType returnType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status;

    @Column(name = "items_total", nullable = false)
    private BigDecimal itemsTotal;

    @Column(name = "discount_percentage")
    private BigDecimal discountPercentage;

    @Column(name = "discount_fixed")
    private BigDecimal discountFixed;

    @Column(name = "calculated_return_amount", nullable = false)
    private BigDecimal calculatedReturnAmount;

    @Column(name = "predicted_value")
    private BigDecimal predictedValue;

    @Column(name = "approved_with")
    private String approvedWith;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_worker_id")
    private Worker responsibleWorker;

    @Column(name = "bill_amount_adjusted", nullable = false)
    @Builder.Default
    private Boolean billAmountAdjusted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "billReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillReturnItem> items;
}