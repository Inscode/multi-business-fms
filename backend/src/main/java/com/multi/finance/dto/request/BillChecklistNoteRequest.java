package com.multi.finance.dto.request;

import lombok.Data;
import java.time.LocalDate;

@Data
public class BillChecklistNoteRequest {
    private String itemName;
    private Integer count;
    private String note;
    private LocalDate entryDate; // nullable → defaults to today in service
}
