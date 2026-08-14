package com.multi.finance.entity;

import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BillType;
import com.multi.finance.enums.BusinessType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_number")
    private String billNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "business", nullable = false)
    private BusinessType business;

    @Column(name = "division", nullable = false)
    private String division;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_source", nullable = false)
    private BillSource billSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type", nullable = false)
    private BillType billType;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "area")
    private String area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_holder_id")
    private Worker currentHolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "bill_date" ,nullable = false)
    private LocalDate billDate;

    private String notes;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Column(name = "balance_remaining", nullable = false)
    private BigDecimal balanceRemaining;

    /**
     * Everything the customer has sent back on this bill, damage and salable together.
     * Held apart from {@link #totalAmount} so the invoiced figure survives a return and
     * an admin reversal is a recompute rather than a compensating entry.
     * Always go through {@code BillBalance} rather than reading this directly.
     */
    @Column(name = "returns_total", nullable = false)
    @Builder.Default
    private BigDecimal returnsTotal = BigDecimal.ZERO;

    @Column(name = "fully_paid", nullable = false)
    private Boolean fullyPaid;

    /** Manually marked as collection-only — skip delivery classification regardless of bill age. */
    @Column(name = "collection_only", nullable = false)
    @Builder.Default
    private Boolean collectionOnly = false;

    /**
     * True for SYSTEM bills that will be reconciled via End-of-Month linking to DRAFT/MANUAL bills.
     * When true: excluded from Summary Load + Stock Item Entry; only appears in Linking tab.
     * Stock is covered by the linked children — no separate stock movement needed.
     */
    @Column(name = "will_be_linked", nullable = false)
    @Builder.Default
    private Boolean willBeLinked = false;

    /** Set by admin once the system linking bill's qty matches all linked children — final sign-off. */
    @Column(name = "stock_reconciled", nullable = false)
    @Builder.Default
    private Boolean stockReconciled = false;

    /** Set by admin once the savings amount (children total − system amount) has been collected. */
    @Column(name = "savings_collected", nullable = false)
    @Builder.Default
    private Boolean savingsCollected = false;

    @Column(name = "stock_cleared", nullable = false)
    @Builder.Default
    private Boolean stockCleared = false;

    /**
     * Kept off the aging report. The balance is still owed and still on the bill —
     * this only says it is not chaseable debt worth reporting, so the report does not
     * overstate what can actually be collected.
     */
    @Column(name = "excluded_from_aging", nullable = false)
    @Builder.Default
    private Boolean excludedFromAging = false;

    @Column(name = "aging_exclusion_reason", length = 300)
    private String agingExclusionReason;

    @Column(name = "aging_excluded_by", length = 100)
    private String agingExcludedBy;

    @Column(name = "aging_excluded_at")
    private LocalDateTime agingExcludedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
