package com.multi.finance.invoicing.dto.request;

import com.multi.finance.invoicing.enums.CategoryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GrnRequest {
    /** Drives which items may be received on this note. */
    @NotNull CategoryType category;
    String supplierName;
    @NotNull LocalDate receivedDate;
    /** Credit period in days — drives when this note falls due for payment. */
    Integer paymentTermsDays;

    /** False for opening stock — recorded for inventory, owes the principal nothing. */
    Boolean paymentRequired;

    String notes;

    @Valid @NotEmpty List<GrnLineRequest> lines;
}
