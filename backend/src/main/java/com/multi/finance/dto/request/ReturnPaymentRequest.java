package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReturnPaymentRequest {
    @NotBlank(message = "Return reason is required")
    private String returnReason;
}
