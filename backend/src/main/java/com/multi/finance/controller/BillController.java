package com.multi.finance.controller;


import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.request.BulkAssignBillRequest;
import com.multi.finance.dto.request.BulkBillIdsRequest;
import com.multi.finance.dto.response.AgingReportResponse;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.dto.response.BillSequenceGapResponse;
import com.multi.finance.dto.response.DashboardResponse;
import com.multi.finance.dto.response.DashboardStatsResponse;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.service.impl.BillServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillServiceImpl billService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillResponse> createBill(
            @Valid @RequestBody BillRequest request) {
        return ResponseEntity.ok(billService.createBill(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getAllBills(
            @RequestParam(required = false) BusinessType business,
            @RequestParam(required = false) BillStatus status,
            @RequestParam(required = false, defaultValue = "true") boolean excludeCompleted,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(billService.getAllBills(business, status, excludeCompleted, from, to));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<List<BillResponse>> globalSearch(@RequestParam String q) {
        return ResponseEntity.ok(billService.globalSearch(q));
    }

    @GetMapping("/overdue-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<Map<String, Long>> getOverdueCount() {
        LocalDate cutoff = LocalDate.now().minusDays(60);
        return ResponseEntity.ok(Map.of("count", billService.countOverduePending(cutoff)));
    }


    @GetMapping("/linking")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getLinkingBills() {
        return ResponseEntity.ok(billService.getLinkingBills());
    }

    @GetMapping("/today/{business}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getTodaysBills(
            @PathVariable BusinessType business) {
        return ResponseEntity.ok(billService.getTodaysBills(business));
    }

    @GetMapping("/unconfirmed/{business}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getUnconfirmedBills(
            @PathVariable BusinessType business) {
        return ResponseEntity.ok(billService.getUnconfirmedBills(business));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillResponse> updateBill(
            @PathVariable Long id,
            @RequestBody BillRequest request) {
        return ResponseEntity.ok(billService.updateBill(id, request));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<BillResponse> assignBill(
            @PathVariable Long id,
            @Valid @RequestBody AssignBillRequest request) {
        return ResponseEntity.ok(billService.assignBill(id, request));
    }

    @PatchMapping("/bulk-assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> bulkAssignBills(
            @Valid @RequestBody BulkAssignBillRequest request) {
        return ResponseEntity.ok(billService.bulkAssignBills(request));
    }

    @PatchMapping("/{id}/shop-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<BillResponse> markShopReceived(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markShopReceived(id));
    }

    @PatchMapping("/bulk-shop-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> bulkMarkShopReceived(
            @Valid @RequestBody BulkBillIdsRequest request) {
        return ResponseEntity.ok(billService.bulkMarkShopReceived(request));
    }

    @PatchMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillResponse> markReceived(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markReceived(id));
    }

    @PatchMapping("/bulk-receive")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> bulkMarkReceived(
            @Valid @RequestBody BulkBillIdsRequest request) {
        return ResponseEntity.ok(billService.bulkMarkReceived(request));
    }

    @PatchMapping("/{id}/mark-stock-cleared")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillResponse> markStockCleared(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markStockCleared(id));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillResponse> markCompleted(@PathVariable Long id) {
        return ResponseEntity.ok(billService.markCompleted(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillResponse> cancelBill(@PathVariable Long id) {
        return ResponseEntity.ok(billService.cancelBill(id));
    }

    @GetMapping("/aging-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<AgingReportResponse> getAgingReport(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business) {
        return ResponseEntity.ok(billService.getAgingReport(business));
    }

    @GetMapping("/sequence-gaps")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BillSequenceGapResponse>> getSequenceGaps(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business) {
        return ResponseEntity.ok(billService.findSequenceGaps(business));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<BillResponse> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(billService.getBillById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
        billService.deleteBill(id);
        return ResponseEntity.noContent().build();
    }

}
