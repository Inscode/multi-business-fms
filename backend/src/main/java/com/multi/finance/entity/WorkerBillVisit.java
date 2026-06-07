package com.multi.finance.entity;

import com.multi.finance.enums.WorkerVisitStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "worker_bill_visits",
       uniqueConstraints = @UniqueConstraint(columnNames = {"bill_id", "worker_id"}))
public class WorkerBillVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_status", nullable = false)
    @Builder.Default
    private WorkerVisitStatus visitStatus = WorkerVisitStatus.NOT_VISITED;

    /** Worker's note — required for REVISIT_REQUESTED (e.g. "Come next Friday") */
    @Column(name = "worker_note")
    private String workerNote;

    @Column(name = "visited_at")
    private LocalDateTime visitedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
