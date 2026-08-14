package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InvoiceResponse {
    private Long id;
    private String invoiceNo;
    private String externalRef;
    private InvoiceMethod method;
    private LocalDate invoiceDate;
    private Long customerId;
    private String customerName;
    private String customerAddress;
    private InvoiceType invoiceType;

    // Financials computed by DiscountEngine
    private BigDecimal grossTotal;             // Σ line values before any discount
    private BigDecimal totalSlabDiscount;      // Σ slab discounts across all brand groups
    /** Set when an admin replaced the slab rate for this invoice. */
    private BigDecimal discountOverridePct;
    private String discountOverrideBy;

    private BigDecimal cashDiscountPct;
    private BigDecimal cashDiscountAmount;
    private BigDecimal plasticDiscountPct;
    private BigDecimal plasticDiscountAmount;
    private BigDecimal netTotal;               // final payable

    private BigDecimal agentPrintedNet;
    private BigDecimal variance;               // agentPrintedNet - netTotal (null if not applicable)
    private String printedBy;
    private LocalDateTime createdAt;
    private boolean duplicatePrint;

    /** The bill raised in the bills section, where payments are collected. */
    private Long billId;

    /** Attached to a bill that already existed — this invoice only moved the stock. */
    private boolean billLinkedExisting;

    private List<InvoiceLineResponse> lines;
}
