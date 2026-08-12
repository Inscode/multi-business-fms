package com.multi.finance.invoicing.service;

import java.util.Set;
import com.multi.finance.invoicing.entity.Brand;
import com.multi.finance.invoicing.entity.DiscountSlab;
import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.enums.InvoiceType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Pure, stateless discount calculation engine.
 *
 * How it works:
 *  1. For each brand-group, sum the gross value of all lines in that group.
 *  2. Look up that total in the brand's slab table → get discount %.
 *  3. Apply the discount % to each line in the group (so line-level snapshot is preserved).
 *  4. After all slab discounts: if Rainco CASH → apply 5% cash discount on Σ(post-slab Rainco lines).
 *  5. If Plastic invoice: apply manual pct or fixed discount on Σ(Plastic gross).
 *
 * All arithmetic uses HALF_UP, scale 2 to match Ventura outputs.
 */
public class DiscountEngineService {

    // --- Slab lookup ---

    /**
     * Returns the discount % (0 if no slab matches) for a given brand-group total.
     * Slabs must be ordered by sort_order ASC (service layer must ensure this).
     */
    public BigDecimal findSlabDiscountPct(List<DiscountSlab> orderedSlabs, BigDecimal groupTotal) {
        for (DiscountSlab slab : orderedSlabs) {
            boolean aboveMin = slab.getMinValue() == null || groupTotal.compareTo(slab.getMinValue()) >= 0;
            boolean belowMax = slab.getMaxValue() == null || groupTotal.compareTo(slab.getMaxValue()) <= 0;
            if (aboveMin && belowMax) {
                return slab.getDiscountPct();
            }
        }
        return BigDecimal.ZERO;
    }

    // --- WSP calculation ---

    /**
     * Compute the wholesale selling price for one item.
     *  - Rainco / Stationery: wsp = mrp * (1 - marginPct/100)
     *  - Plastic (wholesalePrice set directly): returns wholesalePrice as-is
     */
    public BigDecimal computeWsp(Item item) {
        if (item.getWholesalePrice() != null) {
            return item.getWholesalePrice().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal marginPct = item.getMarginPct() != null
                ? item.getMarginPct()
                : (item.getBrand().getDefaultMarginPct() != null ? item.getBrand().getDefaultMarginPct() : BigDecimal.ZERO);
        BigDecimal factor = BigDecimal.ONE.subtract(marginPct.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
        return item.getMrp().multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    // --- Invoice-level computation ---

    public static class LineCalc {
        public final BigDecimal wsp;
        public final BigDecimal value;          // wsp * qty
        public final BigDecimal appliedPct;     // slab discount % snapshotted here

        public LineCalc(BigDecimal wsp, BigDecimal value, BigDecimal appliedPct) {
            this.wsp = wsp;
            this.value = value;
            this.appliedPct = appliedPct;
        }
    }

    public static class InvoiceTotals {
        public BigDecimal grossTotal       = BigDecimal.ZERO;
        public BigDecimal totalSlabDiscount = BigDecimal.ZERO;
        public BigDecimal cashDiscountAmount = BigDecimal.ZERO;
        public BigDecimal plasticDiscount   = BigDecimal.ZERO;
        public BigDecimal netTotal          = BigDecimal.ZERO;
    }

    /**
     * Given per-brand-group slab discounts (keyed by brandId → slabPct already resolved),
     * line values, and invoice-level parameters, compute overall totals.
     *
     * @param brandGroupValues   brandId → gross total value for that group
     * @param brandSlabPcts      brandId → resolved slab discount % for that group
     * @param invoiceType        CASH or CREDIT
     * @param raincoGross        gross total of all Rainco lines (needed for cash discount calc)
     * @param rainCoCashDiscPct  5% (or whatever is configured) — null means no cash discount
     * @param plasticDiscountPct null if not applicable
     * @param plasticDiscountAmt null if not applicable; only one of the two applied (pct first)
     * @param plasticGross       gross plastic lines total (for pct calc)
     */
    /**
     * Discounts compound: each one comes off what is left after the one before it,
     * never off the starting gross.
     *
     * <p>So a Rainco cash discount is taken on the Rainco value <em>after</em> its slab
     * discount, and a plastic discount on the plastic value after its own slab. Charging
     * both against the gross would hand back more than the sum of the two rates, and the
     * further apart the rates the wider that gap grows.
     *
     * <p>The group keys are passed in rather than the pre-summed totals, so the engine
     * works out both the gross and the after-slab base itself. Callers cannot then get
     * the order wrong, which is what happened when each of them summed its own base.
     *
     * @param raincoGroupKeys  brand-group keys belonging to Rainco; the cash discount
     *                         applies to these and nothing else
     * @param plasticGroupKey  the key plastic lines are collapsed under, or null
     */
    public InvoiceTotals computeInvoiceTotals(
            Map<Long, BigDecimal> brandGroupValues,
            Map<Long, BigDecimal> brandSlabPcts,
            InvoiceType invoiceType,
            Set<Long> raincoGroupKeys,
            Long plasticGroupKey,
            BigDecimal rainCoCashDiscPct,
            BigDecimal plasticDiscountPct,
            BigDecimal plasticDiscountAmt
    ) {
        InvoiceTotals t = new InvoiceTotals();

        Set<Long> raincoKeys = raincoGroupKeys == null ? Set.of() : raincoGroupKeys;

        // 1 & 2. Gross, and the slab discount per group. The after-slab value of each
        // group is kept, because that is what any later discount works from.
        BigDecimal raincoAfterSlab  = BigDecimal.ZERO;
        BigDecimal plasticAfterSlab = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> e : brandGroupValues.entrySet()) {
            BigDecimal value = e.getValue() == null ? BigDecimal.ZERO : e.getValue();
            t.grossTotal = t.grossTotal.add(value);

            BigDecimal pct = brandSlabPcts.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal disc = value.multiply(pct)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            t.totalSlabDiscount = t.totalSlabDiscount.add(disc);

            BigDecimal afterSlab = value.subtract(disc);
            if (raincoKeys.contains(e.getKey())) {
                raincoAfterSlab = raincoAfterSlab.add(afterSlab);
            }
            if (plasticGroupKey != null && plasticGroupKey.equals(e.getKey())) {
                plasticAfterSlab = plasticAfterSlab.add(afterSlab);
            }
        }

        // 3. Rainco cash discount — on the Rainco value net of its slab discount.
        if (invoiceType == InvoiceType.CASH && rainCoCashDiscPct != null
                && raincoAfterSlab.compareTo(BigDecimal.ZERO) > 0) {
            t.cashDiscountAmount = raincoAfterSlab
                    .multiply(rainCoCashDiscPct)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }

        // 4. Plastic discount, likewise on the plastic value after its own slab.
        // A percentage is preferred over a fixed amount; a fixed amount is what it says.
        if (plasticAfterSlab.compareTo(BigDecimal.ZERO) > 0) {
            if (plasticDiscountPct != null && plasticDiscountPct.compareTo(BigDecimal.ZERO) > 0) {
                t.plasticDiscount = plasticAfterSlab
                        .multiply(plasticDiscountPct)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else if (plasticDiscountAmt != null && plasticDiscountAmt.compareTo(BigDecimal.ZERO) > 0) {
                t.plasticDiscount = plasticDiscountAmt.setScale(2, RoundingMode.HALF_UP);
            }
        }

        // 5. Net total
        t.netTotal = t.grossTotal
                .subtract(t.totalSlabDiscount)
                .subtract(t.cashDiscountAmount)
                .subtract(t.plasticDiscount)
                .setScale(2, RoundingMode.HALF_UP);

        return t;
    }
}
