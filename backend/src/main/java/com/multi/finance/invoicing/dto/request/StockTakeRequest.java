package com.multi.finance.invoicing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * A physical count, to be written over whatever the system currently believes.
 *
 * <p>Sent whole rather than item by item. A count is one event — everything was on the
 * shelves at the same moment — and applying it in pieces leaves the stock half old and
 * half new for as long as it takes, which is exactly when somebody raises an invoice.
 */
@Data
public class StockTakeRequest {

    /**
     * What this count was, in words. Written onto every movement it creates.
     *
     * <p>Required, because a year from now a hundred items all jumping on the same day
     * is inexplicable without it, and the movement log is the only place to say so.
     */
    @NotBlank
    private String reference;

    /** The day the shelves were actually counted, which may not be today. */
    private LocalDate countedOn;

    private List<Line> lines;

    /**
     * What to do with items nobody counted.
     *
     * <p>Defaults to leaving them alone. Treating "not on the sheet" as "none in the
     * building" is how stock quietly disappears — the usual reason an item is missing
     * from a count is that the sheet ran out, not that the shelf did.
     */
    private boolean zeroUncounted = false;

    @Data
    public static class Line {
        /** Either is enough; the code is what a paste from a count sheet will carry. */
        private Long itemId;
        private String itemCode;

        /** Sellable units on the shelf. */
        private Integer countedQty;

        /**
         * Damaged units, held separately.
         *
         * <p>Null means "not counted" and leaves the damage bucket alone, which is not
         * the same as counting zero. These must never be sold, so folding them into the
         * sellable figure would put goods on an invoice that cannot be shipped.
         */
        private Integer countedDamageQty;
    }
}
