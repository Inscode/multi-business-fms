package com.multi.finance.service;

import com.multi.finance.enums.BusinessType;
import com.multi.finance.invoicing.enums.CategoryType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Works out what a returned line is worth back to the customer.
 *
 * <pre>credit = (WSP × qty) − slab discount − cash discount</pre>
 *
 * <p>The two discounts come off because the customer never paid the gross: they paid
 * WSP less their slab, and less another 5% again if they bought for cash. Crediting the
 * gross would hand back more than was ever collected.
 *
 * <p>Where the percentages come from depends on the return:
 * <ul>
 *   <li><b>Same bill</b> — both are read off the original invoice line, so the credit
 *       is exactly the reverse of what was charged.</li>
 *   <li><b>Different bill</b> — the original line is not to hand, so the accountant
 *       types the discount and marks whether it was a cash sale.</li>
 * </ul>
 *
 * <p>PLASTIC is the exception: it is sold at flat WSP with no slab and no cash
 * discount, so it is credited at flat WSP. The figure stays editable, and an edit is
 * recorded rather than silently accepted.
 */
@Component
public class ReturnCreditCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** The discount structure never applies to plastic — it is sold at flat WSP. */
    public boolean isFlatRated(BusinessType business) {
        return business == BusinessType.PLASTIC;
    }

    /**
     * The 5% cash discount is a Rainco arrangement. Stationery and plastic are sold at
     * the same price whether the customer pays cash or takes credit, so crediting a cash
     * discount back on them would short the customer on their return.
     *
     * <p>Judged per line rather than per bill: a MIX invoice carries Rainco alongside
     * stationery and plastic, and only the Rainco part ever had the discount taken.
     */
    public boolean cashApplies(CategoryType lineCategory) {
        return lineCategory == CategoryType.RAINCO;
    }

    /**
     * @param business  the bill's business; PLASTIC is credited flat
     * @param wsp       unit wholesale price, snapshotted from the original line where known
     * @param qty       quantity coming back
     * @param slabPct   the line's discount %, or null/zero for none
     * @param cashPct   the cash discount %, applied only when the goods were sold for cash
     */
    public Credit compute(BusinessType business, BigDecimal wsp, int qty,
                          BigDecimal slabPct, BigDecimal cashPct) {
        BigDecimal gross = nz(wsp).multiply(BigDecimal.valueOf(Math.max(qty, 0)))
                .setScale(2, RoundingMode.HALF_UP);

        if (isFlatRated(business)) {
            return new Credit(gross, BigDecimal.ZERO, BigDecimal.ZERO, gross);
        }

        BigDecimal slab = pctOf(gross, slabPct);
        BigDecimal afterSlab = gross.subtract(slab);

        // Cash comes off what is left after the slab, matching how it was taken on the
        // way out — not off the gross, which would over-credit.
        BigDecimal cash = pctOf(afterSlab, cashPct);
        BigDecimal credit = afterSlab.subtract(cash);

        if (credit.compareTo(BigDecimal.ZERO) < 0) credit = BigDecimal.ZERO;
        return new Credit(gross, slab, cash, credit);
    }

    /**
     * @param gross      WSP × qty
     * @param slabAmount money taken off for the line's discount
     * @param cashAmount money taken off for the cash discount
     * @param credit     what the customer gets back
     */
    public record Credit(BigDecimal gross, BigDecimal slabAmount,
                         BigDecimal cashAmount, BigDecimal credit) {}

    private BigDecimal pctOf(BigDecimal base, BigDecimal pct) {
        if (pct == null || pct.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return base.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
