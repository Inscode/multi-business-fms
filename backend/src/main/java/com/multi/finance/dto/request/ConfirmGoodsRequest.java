package com.multi.finance.dto.request;

import com.multi.finance.enums.GoodsReceipt;
import lombok.Data;

import java.util.List;

/**
 * The accountant's answer to "did the goods actually come back?", taken before a
 * payment can be entered against the bill.
 */
@Data
public class ConfirmGoodsRequest {
    private GoodsReceipt receipt;
    /** Required when the receipt is PARTIAL or NONE — what was missing, in their words. */
    private String note;
    /** Per-line quantities actually received; the shortfall is derived from these. */
    private List<ReceivedItemDto> items;
}
