package com.multi.finance.invoicing.entity;

import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.GrnStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Stock receipt. An accountant (or admin) records what arrived; stock only moves
 * once an admin approves — so an unapproved GRN never inflates inventory.
 *
 * A GRN is category-wise: every line must be an item of the GRN's category.
 */
@Entity
@Table(name = "inv_grns")
@Getter @Setter
public class GoodsReceivedNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grn_no", nullable = false, unique = true)
    private String grnNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType category;

    /** Free text — suppliers are not a managed entity in the FMS invoicing module. */
    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    /**
     * Supplier discount for this note, snapshotted from the category's rate at creation
     * so a later rate change never rewrites an existing note.
     */
    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal discountPct = java.math.BigDecimal.ZERO;

    /** Credit period agreed with the principal — typed by the admin on the note. */
    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    /** receivedDate + paymentTermsDays, stored so a later terms change never moves it. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * False for opening-stock notes — goods already on the shelf that carry no debt
     * to the principal, so they must stay out of the cash-flow forecast.
     */
    @Column(name = "payment_required", nullable = false)
    private Boolean paymentRequired = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrnStatus status = GrnStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    private String notes;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "grn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GrnLine> lines = new ArrayList<>();
}
