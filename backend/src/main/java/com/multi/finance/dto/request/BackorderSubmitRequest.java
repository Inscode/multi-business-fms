package com.multi.finance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BackorderSubmitRequest {
    @NotNull
    private Long billId;
    @NotEmpty
    private List<BackorderItemRequest> items;
    private String notes;
}
