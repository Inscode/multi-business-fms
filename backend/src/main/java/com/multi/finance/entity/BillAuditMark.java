package com.multi.finance.entity;

import com.multi.finance.enums.BillAuditMarkType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A single bill's outcome within a reconciliation sweep. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_audit_marks",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_bill_audit_mark", columnNames = {"session_id", "bill_id"}))
public class BillAuditMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private BillAuditSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(name = "mark_type", nullable = false)
    private BillAuditMarkType markType;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marked_by_id")
    private User markedBy;

    @Column(name = "marked_at", nullable = false)
    private LocalDateTime markedAt;
}
