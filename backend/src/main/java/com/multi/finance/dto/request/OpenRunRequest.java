package com.multi.finance.dto.request;

import lombok.Data;

import java.time.LocalDate;

/** Opens a lorry round for bills to be entered into. */
@Data
public class OpenRunRequest {
    /** One or more rounds this trip covers. */
    private java.util.List<Long> routeAreaIds;
    private LocalDate plannedDate;
    /** The month this round counts against; defaults to the planned date's month. */
    private LocalDate runMonth;

    private String notes;
}
