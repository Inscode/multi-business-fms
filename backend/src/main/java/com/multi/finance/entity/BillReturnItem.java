package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_return_items")
public class BillReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_return_id", nullable = false)
    private BillReturn billReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ReturnProduct product;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "quantity_requested", nullable = false)
    private Integer quantityRequested;

    @Column(name = "quantity_returned")
    private Integer quantityReturned;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    /**
     * The invoice line these goods were sold on, when the return came off this bill.
     * Its presence is what removes the guesswork: the WSP and the slab % that were
     * actually charged are read from it rather than re-derived from live master data,
     * which may have moved on since.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_line_id")
    private com.multi.finance.invoicing.entity.InvoiceLine invoiceLine;

    /**
     * The invoicing item, so stock can be put back. The legacy {@code product} points
     * at the old return_products catalogue, which the invoicing stock ledger knows
     * nothing about.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inv_item_id")
    private com.multi.finance.invoicing.entity.Item invItem;

    // ── Credit working, snapshotted so it never re-derives ───────────────
    /** WSP × quantity, before any discount. */
    @Column(name = "gross_value", precision = 14, scale = 2)
    private BigDecimal grossValue;

    /**
     * Read off the original invoice line for a same-bill return; typed by the
     * accountant when the goods came off an older bill.
     */
    @Column(name = "slab_discount_pct", precision = 5, scale = 2)
    private BigDecimal slabDiscountPct;

    /** Applied after the slab discount, when the goods were sold for cash. */
    @Column(name = "cash_discount_pct", precision = 5, scale = 2)
    private BigDecimal cashDiscountPct;

    /** What the customer is credited for this line, after both discounts. */
    @Column(name = "credit_amount", precision = 14, scale = 2)
    private BigDecimal creditAmount;

    /** Someone typed over the computed figure — flagged to the admin. */
    @Column(name = "amount_edited", nullable = false)
    @Builder.Default
    private Boolean amountEdited = false;

    /** What the calculation produced, kept so the admin can see what was overridden. */
    @Column(name = "computed_credit_amount", precision = 14, scale = 2)
    private BigDecimal computedCreditAmount;
}
