package com.multi.finance.invoicing.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** What a draft invoice would come to, broken down so the discount is visible per brand. */
@Data
@Builder
public class QuoteResponse {

    private List<BrandGroup> brandGroups;

    private BigDecimal grossTotal;
    private BigDecimal totalSlabDiscount;
    private BigDecimal cashDiscountPct;
    private BigDecimal cashDiscountAmount;
    private BigDecimal plasticDiscount;
    private BigDecimal netTotal;

    /** Every discount together — what the customer is saving on this invoice. */
    private BigDecimal totalDiscount;

    /** Free units on the invoice. They carry no value but do leave stock. */
    private Integer totalFreeQty;

    /**
     * How much more this brand group needs before the next slab applies. Null when it is
     * already on the top slab — the point is to show a better rate within reach.
     */
    @Data
    @Builder
    public static class BrandGroup {
        private Long brandId;
        private String brandName;
        private BigDecimal gross;
        private BigDecimal discountPct;
        private BigDecimal discountAmount;
        private BigDecimal net;
        private BigDecimal nextSlabAt;
        private BigDecimal nextSlabPct;
        private BigDecimal amountToNextSlab;
    }
}
