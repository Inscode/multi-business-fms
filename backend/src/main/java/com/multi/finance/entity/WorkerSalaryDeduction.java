package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "worker_salary_deductions")
public class WorkerSalaryDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private SalaryRecipient recipient;

    @Column(name = "deduction_month", nullable = false)
    private String deductionMonth;

    @Column(nullable = false)
    private BigDecimal amount;

    /** TAB_PURCHASE or ADVANCE_RECOVERY */
    @Column(name = "deduction_type", nullable = false)
    private String deductionType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
