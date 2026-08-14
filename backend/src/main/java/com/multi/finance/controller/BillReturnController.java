package com.multi.finance.controller;

import com.multi.finance.dto.request.ApproveReturnRequest;
import com.multi.finance.dto.request.CancelReturnRequest;
import com.multi.finance.dto.request.ConfirmGoodsRequest;
import com.multi.finance.dto.request.CreateBillReturnRequest;
import com.multi.finance.dto.response.BillReturnResponse;
import com.multi.finance.dto.response.BillReturnSummary;
import com.multi.finance.dto.response.ReturnableLineResponse;
import com.multi.finance.enums.ReturnStatus;
import com.multi.finance.service.impl.BillReturnServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bill-returns")
@RequiredArgsConstructor
public class BillReturnController {

    private final BillReturnServiceImpl billReturnService;

    @PostMapping("/bills/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillReturnResponse> create(
            @PathVariable Long billId,
            @RequestBody CreateBillReturnRequest req) {
        return ResponseEntity.ok(billReturnService.create(billId, req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillReturnResponse>> getAll(
            @RequestParam(required = false) ReturnStatus status) {
        return ResponseEntity.ok(billReturnService.getAll(status));
    }

    @GetMapping("/bills/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillReturnResponse>> getForBill(@PathVariable Long billId) {
        return ResponseEntity.ok(billReturnService.getForBill(billId));
    }

    /**
     * What the bill is worth once its returns are taken off, with damage and salable
     * shown apart. Drives the returns panel on the bill view.
     */
    @GetMapping("/bills/{billId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillReturnSummary> getSummary(@PathVariable Long billId) {
        return ResponseEntity.ok(billReturnService.getSummary(billId));
    }

    /**
     * The bill's own invoice lines, for returning against what was actually sold.
     * Empty for bills entered before invoicing existed.
     */
    @GetMapping("/bills/{billId}/returnable-lines")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<ReturnableLineResponse>> getReturnableLines(@PathVariable Long billId) {
        return ResponseEntity.ok(billReturnService.getReturnableLines(billId));
    }

    /**
     * The accountant confirming what physically came back. Payment on the bill stays
     * blocked until this is answered.
     */
    @PatchMapping("/{id}/confirm-goods")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillReturnResponse> confirmGoods(
            @PathVariable Long id,
            @RequestBody ConfirmGoodsRequest req) {
        return ResponseEntity.ok(billReturnService.confirmGoods(id, req));
    }

    /** The goods were claimed but never turned up — nothing comes off the bill. */
    @PatchMapping("/{id}/not-received")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<BillReturnResponse> markNotReceived(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(billReturnService.markNotReceived(id, reason));
    }

    /**
     * Reverses an approved return the accountant should not have entered. The credit
     * goes back onto the bill and the stock movement is undone.
     */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<BillReturnResponse> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) CancelReturnRequest req) {
        return ResponseEntity.ok(billReturnService.cancel(id, req));
    }

    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        return ResponseEntity.ok(Map.of("count", billReturnService.getPendingCount()));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<BillReturnResponse> approve(
            @PathVariable Long id,
            @RequestBody ApproveReturnRequest req) {
        return ResponseEntity.ok(billReturnService.approve(id, req));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<BillReturnResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(billReturnService.reject(id, reason));
    }

    @PostMapping("/fix-bill-amounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> fixBillAmounts() {
        int count = billReturnService.fixHistoricalBillAmounts();
        return ResponseEntity.ok(Map.of("fixed", count));
    }
}