package com.multi.finance.invoicing.dto.request;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class InvoiceRequest {
    @NotNull InvoiceMethod method;
    @NotNull InvoiceType invoiceType;
    @NotNull Long customerId;
    @NotNull LocalDate invoiceDate;

    /**
     * The agent's printed invoice number. Required on import — it is what the invoice is
     * numbered from there. Optional when typed in, where the number is picked instead.
     */
    String externalRef;

    /**
     * Which kind of bill this is: SYSTEM, MANUAL or MANUAL_BOOK. Drives the number's
     * prefix, exactly as in the bills section.
     */
    @NotNull(message = "Bill type is required")
    com.multi.finance.enums.BillSource billSource;

    /** The bare number — picked from the suggestion list, or the digits of the agent ref. */
    @NotBlank(message = "Bill number is required")
    String billNumber;

    /**
     * The finished number, when an admin has corrected it on import. Used verbatim in
     * place of the prefix + billNumber the system would otherwise derive.
     */
    String invoiceNoOverride;

    // Agent's printed net (for cross-check)
    BigDecimal agentPrintedNet;

    /** Name printed on the source invoice, when it differs from the customer's own name. */
    String billedName;

    /**
     * The customer the import resolved before the accountant touched it. Set by the import
     * only — the service compares it to the customer actually saved to decide whether the
     * change gets flagged to the admin, so it is never taken on trust from the client.
     */
    String originalCustomerName;

    // Plastic-only final discounts (both may be provided; only one will be applied — pct first, then fixed)
    BigDecimal plasticDiscountPct;
    BigDecimal plasticDiscountAmount;

    @Valid
    @NotNull
    List<InvoiceLineRequest> lines;
}
