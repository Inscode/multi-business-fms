package com.multi.finance.entity;

import com.multi.finance.entity.User;
import com.multi.finance.enums.WorkerType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workers")
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "worker_type", nullable = false)
    private WorkerType workerType;

    @Column(name = "base_salary", nullable = false)
    private BigDecimal baseSalary;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "joined_date", nullable = false)
    private LocalDate joinedDate;

    private String notes;

    /** Linked login account — set when admin creates a WORKER user for this worker */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** When false, worker is excluded from time log tracking (e.g. temporary staff) */
    @Builder.Default
    @Column(name = "time_log_active", nullable = false)
    private Boolean timeLogActive = true;

    /** When true, worker appears in bill assignment dropdowns (field collection eligible) */
    @Builder.Default
    @Column(name = "bill_assignable", nullable = false)
    private Boolean billAssignable = true;

    /** Default hours worked per day — used by time-log monthly view when admin marks a normal day */
    @Column(name = "normal_working_hours")
    private Integer normalWorkingHours;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
