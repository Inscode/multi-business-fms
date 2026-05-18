package com.multi.finance.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shop_handovers")
public class ShopHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false, unique = true)
    private Bill bill;

    // Shop accountant assigns to this worker
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_worker_id")
    private Worker assignedWorker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_received_by")
    private User storeReceivedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "shop_received_at")
    private LocalDateTime shopReceivedAt;

    @Column(name = "store_received_at")
    private LocalDateTime storeReceivedAt;

    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}