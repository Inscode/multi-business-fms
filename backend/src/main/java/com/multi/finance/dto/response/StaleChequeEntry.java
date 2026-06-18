package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A cheque whose date has passed but the payment is still unconfirmed — needs follow-up (possible bounce). */
@Data
@Builder
public class StaleChequeEntry {
    private LocalDate chequeDate;
    private BigDecimal amount;
    private String customerName;
    private String billNumber;
}
