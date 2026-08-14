package com.multi.finance.invoicing.service;

import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Who is allowed free goods, and on what.
 *
 * <p>Free issue is a stationery arrangement and a narrow one: the shoe polish scheme,
 * and the K01047 umbrella thrown in on a stationery bill. Nothing else is ever given
 * away, so a free quantity anywhere else is a mistake worth refusing rather than
 * quietly writing stock off.
 *
 * <p>Shared by the typed invoice form and the Excel import so the two cannot drift —
 * the umbrella used to work only on import, which meant the same bill behaved
 * differently depending on how it got into the system.
 */
@Component
public class FreeIssuePolicy {

    /**
     * The umbrella given free against stationery invoices. Tried in order, so the
     * catalog can carry either code.
     *
     * <p>It is a Rainco-category item given away on a stationery bill, which is why it
     * needs naming explicitly: every category rule in the system would otherwise
     * refuse it.
     */
    public static final List<String> FREE_UMBRELLA_CODES = List.of("RC-K01047", "K01047");

    /** The brand the shoe polish sits under; its items carry the buy-N-get-M scheme. */
    private static final String POLISH_BRAND = "shoe polish";

    /** True for the K01047 umbrella, whatever code the catalog holds it under. */
    public boolean isFreeUmbrella(Item item) {
        if (item == null || item.getItemCode() == null) return false;
        String code = item.getItemCode().trim().toUpperCase(Locale.ROOT);
        return FREE_UMBRELLA_CODES.stream().anyMatch(c -> c.equalsIgnoreCase(code));
    }

    /** True for shoe polish, the only stationery goods carrying a free-issue scheme. */
    public boolean isPolishItem(Item item) {
        if (item == null || item.getBrand() == null || item.getBrand().getName() == null) {
            return false;
        }
        return item.getBrand().getName().trim().toLowerCase(Locale.ROOT).contains(POLISH_BRAND);
    }

    /**
     * Whether this line may carry a free quantity at all.
     *
     * <p>The quantity itself is never computed here — the rep says what they are giving
     * once the bill is totalled, and that figure is typed in. This only decides where
     * the question may be asked.
     */
    public boolean allowsFreeQty(InvoiceMethod method, Item item) {
        if (method != InvoiceMethod.STATIONERY_ONLY) return false;
        return isPolishItem(item) || isFreeUmbrella(item);
    }

    /** Why a free quantity was refused, in words the accountant can act on. */
    public String refusalReason(InvoiceMethod method, Item item) {
        if (method != InvoiceMethod.STATIONERY_ONLY) {
            return "Free issue is only given on stationery-only invoices. Remove the free "
                 + "quantity on " + code(item) + ", or change the invoice type to Stationery Only.";
        }
        return "Free issue is only given on shoe polish and the K01047 umbrella. "
             + code(item) + " cannot carry a free quantity.";
    }

    private String code(Item item) {
        return item == null ? "this line" : item.getItemCode();
    }
}
