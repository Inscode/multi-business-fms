package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ChequeDetailEntry {
    private String customerName;
    private String billNumber;
    private String bankName;
    private String chequeNumber;
    private BigDecimal amount;
    private String status;
}
