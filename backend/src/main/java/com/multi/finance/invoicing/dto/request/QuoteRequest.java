package com.multi.finance.invoicing.dto.request;

import com.multi.finance.invoicing.enums.InvoiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a draft invoice would come to, before anything is saved.
 *
 * Deliberately lighter than {@link InvoiceRequest}: a quote needs no customer, number or
 * date, so the form can price a half-finished invoice as lines are added.
 */
@Data
public class QuoteRequest {

    @NotNull InvoiceType invoiceType;

    BigDecimal plasticDiscountPct;
    BigDecimal plasticDiscountAmount;

    /** Admin promotional rate, so the preview shows what will actually be charged. */
    BigDecimal discountOverridePct;

    @Valid
    @NotNull
    List<InvoiceLineRequest> lines;
}
