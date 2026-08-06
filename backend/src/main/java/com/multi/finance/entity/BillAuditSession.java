package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One month-end reconciliation sweep: the operator works through every pending
 * bill in scope, marking each. Progress is saved so a sweep can be paused,
 * resumed, and shared between users.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_audit_sessions")
public class BillAuditSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** First day of the month being reconciled. */
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    /** Optional scope — null means every business / area. */
    @Column(name = "business_scope")
    private String businessScope;

    @Column(name = "area_scope")
    private String areaScope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by_id")
    private User openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
