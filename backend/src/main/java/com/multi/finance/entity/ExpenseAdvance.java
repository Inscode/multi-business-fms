package com.multi.finance.entity;

import com.multi.finance.enums.BusinessType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense_advances")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpenseAdvance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private BusinessType business;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "delivery_run_id")
    private DeliveryRun deliveryRun;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "collector_id")
    private Worker collector;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "returned_amount")
    private BigDecimal returnedAmount;

    @Column(nullable = false)
    @Builder.Default
    private String status = "OPEN";

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "issued_by_id", nullable = false)
    private User issuedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
