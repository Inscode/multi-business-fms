package com.multi.finance.invoicing.entity;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import com.multi.finance.entity.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inv_invoices",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_external_ref_method",
        columnNames = {"external_ref", "method"}
    ))
@Getter @Setter
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // System-generated, method-prefixed: GD-MX-000001 / GD-RC-000001 / GD-ST-000001
    @Column(name = "invoice_no", nullable = false, unique = true)
    private String invoiceNo;

    // Agent's own invoice number — only for RAINCO_ONLY / STATIONERY_ONLY
    @Column(name = "external_ref")
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceMethod method;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private InvoiceType invoiceType;

    // 5% for Rainco CASH invoices — stored for audit, sourced from system settings at save time
    @Column(name = "cash_discount_pct", precision = 5, scale = 2)
    private BigDecimal cashDiscountPct;

    // Plastic-only manual discounts (applied after Σ lines)
    @Column(name = "plastic_discount_pct", precision = 5, scale = 2)
    private BigDecimal plasticDiscountPct;

    @Column(name = "plastic_discount_amount", precision = 12, scale = 2)
    private BigDecimal plasticDiscountAmount;

    // Agent's printed net — for cross-check on RAINCO_ONLY / STATIONERY_ONLY
    @Column(name = "agent_printed_net", precision = 12, scale = 2)
    private BigDecimal agentPrintedNet;

    @Column(name = "printed_by")
    private String printedBy;

    // ── Who it was billed to, versus who the customer actually is ────────
    // Invoices are often raised under a different name than the real buyer, so the
    // printed name is kept alongside the customer the money is actually owed by.

    /** The name as it appeared on the source invoice (Ventura sheet / agent's copy). */
    @Column(name = "billed_name")
    private String billedName;

    /** The customer the system resolved before anyone edited it. */
    @Column(name = "original_customer_name")
    private String originalCustomerName;

    /** True when the accountant pointed this at a different customer than the one resolved. */
    @Column(name = "customer_changed", nullable = false)
    private boolean customerChanged = false;

    @Column(name = "customer_changed_by")
    private String customerChangedBy;

    // ── Admin review ─────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.multi.finance.invoicing.enums.InvoiceSource source =
            com.multi.finance.invoicing.enums.InvoiceSource.MANUAL;

    @Column(name = "created_by")
    private String createdBy;

    @Column(nullable = false)
    private boolean reviewed = false;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * The bill this invoice raised in the bills section, so payments can be collected.
     * Held as a plain id rather than an association: the bills module owns its own
     * entity graph and nothing there needs to know invoicing exists.
     */
    @Column(name = "bill_id")
    private Long billId;

    /**
     * True when the invoice attached to a bill that already existed rather than raising
     * one. Those bills were entered by hand without their stock being reduced, so the
     * invoice exists to move the stock and must not duplicate the money.
     */
    @Column(name = "bill_linked_existing", nullable = false)
    private boolean billLinkedExisting = false;

    /** Set when a line carries a typed free quantity — surfaced to the admin on review. */
    @Column(name = "free_issue_added_by")
    private String freeIssueAddedBy;

    @Column(name = "free_issue_added_at")
    private LocalDateTime freeIssueAddedAt;

    /**
     * A flat discount an admin set for this invoice, replacing the slab rate. Used for
     * promotions, which are not expressible as value bands. Held here for the record;
     * the rate itself is snapshotted onto each line like any other.
     */
    @Column(name = "discount_override_pct", precision = 5, scale = 2)
    private BigDecimal discountOverridePct;

    @Column(name = "discount_override_by", length = 100)
    private String discountOverrideBy;

    @Column(name = "discount_override_at")
    private LocalDateTime discountOverrideAt;

    @Column(name = "edited_by")
    private String editedBy;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    /** The import run this came in on, for checking against the agent's summary bill. */
    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_duplicate_print")
    private boolean duplicatePrint = false;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    /**
     * Voided, but kept.
     *
     * <p>The number was issued and the stock moved, so both need somewhere to live. An
     * invoice that is simply erased takes with it the record of a mistake that was made,
     * which is exactly what a reconciliation later goes looking for.
     */
    @Column(nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
