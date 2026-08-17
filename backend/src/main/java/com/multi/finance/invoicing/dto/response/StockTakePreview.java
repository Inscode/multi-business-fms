package com.multi.finance.invoicing.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * What a stock take would do, worked out before anything is written.
 *
 * <p>Shown first because the counted figure overwrites the system's, and there is no
 * balance left afterwards to reveal a typo. A row that reads 5 where it should read 50
 * is invisible once applied — the only place to catch it is here, against the figure the
 * system currently holds.
 */
@Data
@Builder
public class StockTakePreview {

    private String reference;
    private int lineCount;

    /** Lines that would change something. */
    private int changedCount;
    /** Lines whose count already matches — nothing to write. */
    private int matchedCount;
    /** Codes that matched no item. Blocking: a miscount is better than a wrong item. */
    private int notFoundCount;

    /** Net movement across everything, sellable units. */
    private int netUnitChange;

    private List<Row> rows;

    /**
     * Items the count never mentioned, with what the system still holds for them.
     *
     * <p>Listed rather than assumed either way, so leaving them is a decision somebody
     * made rather than one that happened by omission.
     */
    private List<Row> uncounted;

    @Data
    @Builder
    public static class Row {
        private Long itemId;
        private String itemCode;
        private String description;
        private String brand;
        private String category;

        private Integer systemQty;
        private Integer countedQty;
        /** counted − system. What the adjustment will be. */
        private Integer delta;

        private Integer systemDamageQty;
        private Integer countedDamageQty;
        private Integer damageDelta;

        /** MATCHED, INCREASE, DECREASE, NOT_FOUND or DUPLICATE. */
        private String status;

        /**
         * Said plainly where a row deserves a second look — a code that matched nothing,
         * the same code counted twice, or a swing large enough to be a typo.
         */
        private String warning;
    }
}
