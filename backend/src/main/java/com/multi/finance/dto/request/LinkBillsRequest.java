package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class LinkBillsRequest {

    @NotEmpty(message = "At least one draft/manual bill must be selected")
    private List<Long> draftManualBillIds; // IDs of DRAFT/MANUAL bills to link

    // Either select existing system bill OR create new one
    private Long systemBillId; // Null if creating new

    private String systemBillNumber; // For creating new system bill

    private String notes;
}
