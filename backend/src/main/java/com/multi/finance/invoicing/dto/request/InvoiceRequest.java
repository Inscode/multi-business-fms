package com.multi.finance.invoicing.dto.request;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import jakarta.validation.Valid;
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

    // For RAINCO_ONLY / STATIONERY_ONLY — the agent's printed invoice number
    String externalRef;

    // Agent's printed net (for cross-check)
    BigDecimal agentPrintedNet;

    // Plastic-only final discounts (both may be provided; only one will be applied — pct first, then fixed)
    BigDecimal plasticDiscountPct;
    BigDecimal plasticDiscountAmount;

    @Valid
    @NotNull
    List<InvoiceLineRequest> lines;
}
