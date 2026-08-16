package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A lorry round, with what it is carrying. */
@Data
@Builder
public class DeliveryRunResponse {
    private Long id;
    private List<Long> routeAreaIds;
    /** "Bandarawela + Haputale" — the whole trip in one line. */
    private String areaName;
    private List<String> areaNames;
    private LocalDate plannedDate;
    /** The month it counts against — not always the month it went out. */
    private LocalDate runMonth;
    private String status;
    private String notes;

    private String openedBy;
    private LocalDateTime openedAt;
    private String closedBy;
    private LocalDateTime closedAt;

    /** What the admin checks the lorry against. */
    private int billCount;
    private int customerCount;
    private BigDecimal totalValue;

    /** Present on the detail view only. */
    private List<BillResponse> bills;
}
