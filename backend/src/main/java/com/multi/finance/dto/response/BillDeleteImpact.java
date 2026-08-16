package com.multi.finance.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * What deleting a bill would destroy.
 *
 * <p>Read before asking, so the warning names actual records rather than saying
 * "this cannot be undone" and leaving the person to guess what they are losing.
 */
@Data
@Builder
public class BillDeleteImpact {
    private Long billId;
    private String billNumber;
    private String customerName;
    private BigDecimal totalAmount;

    private int confirmedPayments;
    private BigDecimal confirmedAmount;
    private int unconfirmedPayments;
    private int workerEntries;
    private int collectionNotes;
    private int approvedReturns;
    private int openReturns;
    private boolean onDeliveryRun;
    private String deliveryRunLabel;

    /** Plain sentences for the dialog, worst first. */
    private List<String> warnings;

    /** True when nothing of consequence is attached. */
    private boolean clean;
}
