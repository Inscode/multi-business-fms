package com.multi.finance.dto.request;

import lombok.Data;

/** Admin reversing a return that should not have been entered. */
@Data
public class CancelReturnRequest {
    private String reason;
}
