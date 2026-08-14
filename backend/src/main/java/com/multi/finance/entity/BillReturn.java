package com.multi.finance.entity;

import com.multi.finance.enums.ReturnStatus;
import com.multi.finance.enums.ReturnType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_returns")
public class BillReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false)
    private ReturnType returnType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status;

    @Column(name = "items_total", nullable = false)
    private BigDecimal itemsTotal;

    @Column(name = "discount_percentage")
    private BigDecimal discountPercentage;

    @Column(name = "discount_fixed")
    private BigDecimal discountFixed;

    @Column(name = "calculated_return_amount", nullable = false)
    private BigDecimal calculatedReturnAmount;

    @Column(name = "predicted_value")
    private BigDecimal predictedValue;

    @Column(name = "approved_with")
    private String approvedWith;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_worker_id")
    private Worker responsibleWorker;

    @Column(name = "bill_amount_adjusted", nullable = false)
    @Builder.Default
    private Boolean billAmountAdjusted = false;

    /**
     * Returns approved before the returns rework wrote their deduction straight into
     * {@code bills.total_amount}. Counting them in {@code returns_total} as well would
     * take the money off twice, so they are flagged and left as they are.
     */
    @Column(name = "legacy_amount_adjusted", nullable = false)
    @Builder.Default
    private Boolean legacyAmountAdjusted = false;

    // ── Where the goods came from ────────────────────────────────────────
    /** Items picked off this bill's own invoice lines, rather than the item catalogue. */
    @Column(name = "from_same_bill", nullable = false)
    @Builder.Default
    private Boolean fromSameBill = false;

    /**
     * The goods were sold on a cash invoice. They were discounted an extra 5% at
     * purchase, so that much comes back off the credit.
     */
    @Column(name = "cash_sale", nullable = false)
    @Builder.Default
    private Boolean cashSale = false;

    @Column(name = "cash_discount_pct", precision = 5, scale = 2)
    private BigDecimal cashDiscountPct;

    // ── Accountant's goods-received gate ─────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "goods_receipt")
    private com.multi.finance.enums.GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_confirmed_by_id")
    private User goodsConfirmedBy;

    @Column(name = "goods_confirmed_at")
    private LocalDateTime goodsConfirmedAt;

    /** What the accountant saw — read by the admin on review. */
    @Column(name = "goods_confirmed_note", length = 500)
    private String goodsConfirmedNote;

    // ── Admin reversal ───────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** Stock has already been moved for this return; stops it moving twice. */
    @Column(name = "stock_applied", nullable = false)
    @Builder.Default
    private Boolean stockApplied = false;

    /** Someone typed over a computed line credit — surfaced to the admin on review. */
    @Column(name = "amount_edited", nullable = false)
    @Builder.Default
    private Boolean amountEdited = false;

    @Column(name = "amount_edited_by", length = 100)
    private String amountEditedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "billReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillReturnItem> items;
}