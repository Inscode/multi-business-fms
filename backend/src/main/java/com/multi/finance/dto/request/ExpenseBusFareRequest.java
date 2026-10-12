package com.multi.finance.dto.request;
import lombok.Data;
import java.math.BigDecimal;
@Data public class ExpenseBusFareRequest {
    private String fromLocation;
    private String toLocation;
    private BigDecimal amount;
}
