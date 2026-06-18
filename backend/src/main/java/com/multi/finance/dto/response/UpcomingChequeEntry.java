package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class UpcomingChequeEntry {
    private LocalDate chequeDate;
    private BigDecimal totalAmount;
    private int count;
}
