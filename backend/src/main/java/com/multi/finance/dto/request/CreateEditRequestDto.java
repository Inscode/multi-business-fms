package com.multi.finance.dto.request;

import com.multi.finance.enums.EditRequestType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEditRequestDto {

    @NotNull
    private EditRequestType type;

    @NotNull
    private Long targetId;

    @NotNull
    private String targetRef;

    @NotNull
    private String requestedChanges;

    private String reason;

    /**
     * The bill photographed, required when the request changes an amount.
     *
     * <p>Checked on the server as well as in the dialog: an amount edit is approved by
     * somebody who cannot see the paper, and a rule enforced only in the browser is a
     * rule that holds until the first person posts around it.
     */
    private String proofImageUrl;
}