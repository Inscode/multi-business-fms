package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Confirmed collections for one date, split by payment method. */
@Data
@Builder
public class PaymentMixEntry {
    private LocalDate date;
    private BigDecimal cash;
    private BigDecimal cheque;
    private BigDecimal bankTransfer;
    private BigDecimal total;
}
