package com.multi.finance.dto.response;

import com.multi.finance.enums.WorkerVisitStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class WorkerBillResponse {
    private Long id;
    private String billNumber;
    private String customerName;
    private String area;
    private String business;
    private BigDecimal totalAmount;
    private BigDecimal balanceRemaining;
    private BigDecimal tempBalance;           // balanceRemaining minus pending worker payments
    private BigDecimal workerPendingTotal;    // total worker has entered but not confirmed
    private LocalDate billDate;
    private String workerName;                // assigned worker

    // Delivery tracking
    private WorkerVisitStatus visitStatus;
    private String visitNote;

    // Management notes (from BillReminder)
    private List<String> managementNotes;

    // Worker's entered payments for this bill
    private List<WorkerPaymentEntryResponse> workerPayments;
}
