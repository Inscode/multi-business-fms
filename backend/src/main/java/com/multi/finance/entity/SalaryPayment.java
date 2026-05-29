package com.multi.finance.entity;

import com.multi.finance.enums.SalaryPaymentStatus;
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
@Table(name = "salary_payments")
public class SalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private SalaryRecipient recipient;

    @Column(nullable = false)
    private String month;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalaryPaymentStatus status;

    @Column(name = "is_over_salary")
    private Boolean overSalary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by_id", nullable = false)
    private User enteredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}