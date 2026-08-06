package com.multi.finance.dto.request;

import com.multi.finance.enums.BillAuditMarkType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BillAuditMarkRequest {
    @NotNull private Long sessionId;
    @NotNull private Long billId;
    /** Null clears the mark, putting the bill back on the working list. */
    private BillAuditMarkType markType;
    private String note;
}
