package com.multi.finance.entity;

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

    @Column(name = "worker_type", nullable = false)
    private String workerType;

    @Column(name = "base_salary", nullable = false)
    private BigDecimal baseSalary;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "joined_date", nullable = false)
    private LocalDate joinedDate;

    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

//"ACCOUNTANT", "DELIVERY", "SALES_REP", "SHOP"
