package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceSource;
import com.multi.finance.invoicing.enums.InvoiceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvoiceSummaryResponse {
    private Long id;
    private String invoiceNo;
    private String externalRef;
    private InvoiceMethod method;
    private LocalDate invoiceDate;
    private String customerName;
    private InvoiceType invoiceType;
    private BigDecimal grossTotal;
    private BigDecimal totalDiscount;
    private BigDecimal cashDiscountAmount;

    /** Set when an admin replaced the slab rate — surfaced on review. */
    private BigDecimal discountOverridePct;
    private String discountOverrideBy;
    private BigDecimal netTotal;
    private boolean duplicatePrint;

    /**
     * K01047 umbrellas given free on this bill, zero when none. Tracked because the
     * umbrella carries no value and so leaves no trace in the totals — the only
     * record that it went out is the stock it took with it.
     */
    private Integer freeUmbrellaQty;

    /** Free goods of any kind on this invoice — polish scheme and umbrella together. */
    private Integer totalFreeQty;

    /** The bill raised in the bills section, where payments are collected. */
    private Long billId;

    /** Attached to a bill that already existed — this invoice only moved the stock. */
    private boolean billLinkedExisting;

    // ── Review ───────────────────────────────────────────────────────
    /** Name on the source invoice, when it differs from the customer's own. */
    private String billedName;
    private String originalCustomerName;
    private boolean customerChanged;
    private String customerChangedBy;
    private InvoiceSource source;
    private String createdBy;
    private LocalDateTime createdAt;
    /** Set when a line carries a typed free quantity — flagged on review. */
    private String freeIssueAddedBy;
    private LocalDateTime freeIssueAddedAt;
    private String editedBy;
    private LocalDateTime editedAt;
    /** Voided but kept — its number was issued and its goods moved. */
    private boolean cancelled;
    private String cancelReason;
    private String cancelledBy;

    private boolean reviewed;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
}
