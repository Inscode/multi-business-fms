package com.multi.finance.controller;


import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.request.BulkAssignBillRequest;
import com.multi.finance.dto.request.BulkBillIdsRequest;
import com.multi.finance.dto.response.AgingExportResponse;
import com.multi.finance.dto.response.AgingReportResponse;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.dto.response.BillSequenceGapResponse;
import com.multi.finance.dto.response.DashboardResponse;
import com.multi.finance.dto.response.DashboardStatsResponse;
import com.multi.finance.dto.response.SkipReviewResponse;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BillType;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.service.impl.AgingExportService;
import com.multi.finance.service.impl.BillServiceImpl;
import com.multi.finance.service.impl.WorkerPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

import java.util.List;
import java.util.Collections;

@RestController
@RequestMapping("/api/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillServiceImpl billService;
    private final WorkerPortalService workerPortalService;
    private final AgingExportService agingExportService;

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

    @GetMapping("/next-numbers")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<com.multi.finance.service.impl.BillServiceImpl.BillNumberOption>> getNextBillNumbers(
            @RequestParam com.multi.finance.enums.BusinessType business,
            @RequestParam com.multi.finance.enums.BillSource billSource) {
        return ResponseEntity.ok(billService.getNextBillNumbers(business, billSource));
    }

    @GetMapping("/skip-reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SkipReviewResponse>> getPendingSkips() {
        return ResponseEntity.ok(billService.getPendingSkips());
    }

    @PatchMapping("/skip-reviews/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveSkip(@PathVariable Long id) {
        billService.approveSkip(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/skip-reviews/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectSkip(@PathVariable Long id) {
        billService.rejectSkip(id);
        return ResponseEntity.ok().build();
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

    /**
     * Keeps a bill off the aging report, or puts it back. Admin only — the report is
     * what collection is run from, so what appears on it is not a clerical decision.
     */
    @PatchMapping("/{id}/aging-visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillResponse> setAgingVisibility(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            java.security.Principal principal) {
        boolean excluded = Boolean.TRUE.equals(body.get("excluded"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(
                billService.setAgingVisibility(id, excluded, reason, principal.getName()));
    }

    /** The bills currently hidden, so an exclusion cannot be forgotten about. */
    @GetMapping("/aging-report/excluded")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<BillResponse>> getAgingExcluded(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business) {
        return ResponseEntity.ok(billService.getAgingExcludedBills(business));
    }

    @GetMapping("/aging-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<AgingReportResponse> getAgingReport(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business) {
        return ResponseEntity.ok(billService.getAgingReport(business));
    }

    /** Printable aging report — business required; area accepts several, comma separated. */
    @GetMapping("/aging-report/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<AgingExportResponse> getAgingExport(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) BillType billType,
            @RequestParam(required = false, defaultValue = "AGE") AgingExportService.SortMode sort) {
        return ResponseEntity.ok(agingExportService.getExport(business, area, billType, sort));
    }

    @GetMapping("/aging-report/export.xlsx")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<byte[]> getAgingExcel(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) BillType billType,
            @RequestParam(required = false, defaultValue = "AGE") AgingExportService.SortMode sort)
            throws java.io.IOException {

        byte[] body = agingExportService.getExcel(business, area, billType, sort);
        String name = "aging-" + business.name().toLowerCase()
                + (area != null && !area.isBlank() ? "-" + area.toLowerCase().replace(' ', '-') : "")
                + (billType != null ? "-" + billType.name().toLowerCase() : "")
                + "-" + java.time.LocalDate.now() + ".xlsx";

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(body);
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

    @PatchMapping("/{id}/toggle-collection-only")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<Void> toggleCollectionOnly(@PathVariable Long id) {
        workerPortalService.toggleCollectionOnly(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
        billService.deleteBill(id);
        return ResponseEntity.noContent().build();
    }

    // ── Bill Review ───────────────────────────────────────────────────────────

    @GetMapping("/review/unreviewed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BillResponse>> getUnreviewedBills() {
        return ResponseEntity.ok(billService.getUnreviewedBills());
    }

    @GetMapping("/review/unreviewed-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getUnreviewedCount() {
        return ResponseEntity.ok(Map.of("count", billService.getUnreviewedCount()));
    }

    @PostMapping("/review/mark")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> markBillsReviewed(@RequestBody Map<String, List<Long>> body) {
        billService.markBillsReviewed(body.getOrDefault("billIds", Collections.emptyList()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/review/mark-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> markAllReviewed() {
        billService.markAllBillsReviewed();
        return ResponseEntity.noContent().build();
    }

}
