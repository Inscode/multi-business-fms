package com.multi.finance.entity;

import com.multi.finance.enums.HolidayType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "company_holidays")
public class CompanyHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false)
    private HolidayType holidayType;

    @Column(name = "applies_to_all", nullable = false)
    private Boolean appliesToAll;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "company_holiday_workers",
               joinColumns = @JoinColumn(name = "holiday_id"),
               inverseJoinColumns = @JoinColumn(name = "worker_id"))
    @Builder.Default
    private Set<Worker> specificWorkers = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
