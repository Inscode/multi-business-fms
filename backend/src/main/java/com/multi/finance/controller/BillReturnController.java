package com.multi.finance.controller;

import com.multi.finance.dto.request.ApproveReturnRequest;
import com.multi.finance.dto.request.CreateBillReturnRequest;
import com.multi.finance.dto.response.BillReturnResponse;
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