package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Generic request for bulk status-change actions that only need a list of bill IDs. */
@Data
public class BulkBillIdsRequest {

    @NotEmpty(message = "At least one bill must be selected")
    private List<Long> billIds;
}
