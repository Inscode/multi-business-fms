package com.multi.finance.entity;

import com.multi.finance.enums.AdvanceBonusType;
import com.multi.finance.enums.WorkerFinanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "worker_advance_bonus")
public class WorkerAdvanceBonus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private SalaryRecipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdvanceBonusType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String month;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerFinanceStatus status;

    @Column(name = "recovered_amount", nullable = false)
    private BigDecimal recoveredAmount;

    @Column(name = "fully_recovered", nullable = false)
    private Boolean fullyRecovered;

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
}
