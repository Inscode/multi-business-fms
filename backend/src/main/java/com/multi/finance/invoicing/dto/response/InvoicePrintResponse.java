package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class InvoicePrintResponse {

    // Print meta
    private String watermark;          // "ORIGINAL" or "DUPLICATE"

    // Company header (varies by invoice method)
    private String companyName;        // "GHANIM DISTRIBUTORS" / "GHANIM ENTERPRISES"
    private String companyAddress;
    private String companyCity;
    private String companyTel;
    private String distributorLine;    // "DISTRIBUTOR FOR Rainco (Pvt) Ltd"

    // Invoice identifiers
    private String invoiceNo;
    private String externalRef;        // Agent's invoice no (BDSL/xxxxx)
    private LocalDate invoiceDate;
    private InvoiceMethod method;
    private InvoiceType invoiceType;

    // Customer info
    private String customerCode;
    private String customerName;
    private String customerAddress;
    private String customerPhone;

    // Items grouped by brand
    private List<BrandGroup> brandGroups;

    // Invoice-level totals
    private BigDecimal grossTotal;
    private BigDecimal totalSlabDiscount;
    private BigDecimal cashDiscountPct;
    private BigDecimal cashDiscountAmount;
    private BigDecimal netTotal;

    // ── Nested types ──────────────────────────────────────────────────────────

    @Data
    public static class BrandGroup {
        private Long brandId;
        private String brandName;
        private List<PrintLine> lines;
        private BigDecimal brandTotalWsp;
        private BigDecimal brandDiscountPct;
        private BigDecimal brandDiscountAmount;
        private BigDecimal brandNetTotal;
    }

    @Data
    public static class PrintLine {
        private String itemCode;
        private String description;
        private Integer qty;
        private Integer freeIssueQty;  // null when no free issue applies
        private BigDecimal mrp;
        private BigDecimal marginPct;
        private BigDecimal wsp;
        private BigDecimal value;
    }
}
