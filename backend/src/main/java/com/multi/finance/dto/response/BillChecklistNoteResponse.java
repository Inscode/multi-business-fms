package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BillChecklistNoteResponse {
    private Long id;
    private String itemName;
    private int count;
    private String note;
    private LocalDate entryDate;
    private String createdByName;
    private LocalDateTime createdAt;
}
