package com.multi.finance.invoicing.dto.response;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceSummaryResponse {
    private Long id;
    private String invoiceNo;
    private String externalRef;
    private InvoiceMethod method;
    private LocalDate invoiceDate;
    private String customerName;
    private InvoiceType invoiceType;
    private BigDecimal grossTotal;
    private BigDecimal totalDiscount;
    private BigDecimal cashDiscountAmount;
    private BigDecimal netTotal;
    private boolean duplicatePrint;
}
