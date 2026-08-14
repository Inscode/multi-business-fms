package com.multi.finance.entity;

import com.multi.finance.enums.BusinessType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An obligation that isn't represented by a GRN in this system — a cheque already
 * written against an older purchase, or an opening-stock liability. Without these
 * the cash-flow forecast only sees new purchases and looks far healthier than reality.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "supplier_payables")
public class SupplierPayable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessType business;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** When the money actually leaves — a cheque date, or an agreed pay-by date. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "cheque_number")
    private String chequeNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean settled = false;

    @Column(name = "settled_on")
    private LocalDate settledOn;

    @Column
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
