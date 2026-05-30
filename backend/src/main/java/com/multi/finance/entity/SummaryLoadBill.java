package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "summary_load_bills")
public class SummaryLoadBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
            name = "summary_load_bill_items",
            joinColumns = @JoinColumn(name = "summary_load_bill_id"),
            inverseJoinColumns = @JoinColumn(name = "system_bill_id")
    )
    private Set<Bill> systemBills; // SYSTEM bills in this load

    @Column(name = "load_date", nullable = false)
    private LocalDate loadDate;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status; // PENDING, APPROVED, REJECTED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @OneToMany(mappedBy = "summaryLoadBill", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SummaryLoadBillItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
