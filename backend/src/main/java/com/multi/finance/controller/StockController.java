package com.multi.finance.controller;

import com.multi.finance.dto.request.CreateLinkingSystemBillRequest;
import com.multi.finance.dto.request.CreateStockBillRequest;
import com.multi.finance.dto.request.CreateSummaryLoadBillRequest;
import com.multi.finance.dto.request.EnterReferenceItemsRequest;
import com.multi.finance.dto.request.IndividualStockReductionRequest;
import com.multi.finance.dto.request.LinkBillsRequest;
import com.multi.finance.dto.response.BillStockItemResponse;
import com.multi.finance.dto.response.BillStockStatusResponse;
import com.multi.finance.dto.response.StockBillResponse;
import com.multi.finance.dto.response.StockReductionPendingResponse;
import com.multi.finance.dto.response.StockReductionStatusResponse;
import com.multi.finance.dto.response.SummaryLoadBillResponse;
import com.multi.finance.entity.ReturnProduct;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.service.impl.StockServiceImpl;
import com.multi.finance.repository.ReturnProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockServiceImpl stockService;
    private final ReturnProductRepository returnProductRepository;

    // ── Products ──────────────────────────────────────────────────

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<ReturnProduct>> getRaincoProducts() {
        return ResponseEntity.ok(returnProductRepository.findByBusinessAndActiveTrue(BusinessType.RAINCO));
    }

    // ── Bill lists for dropdowns ──────────────────────────────────

    @GetMapping("/bills/unassigned")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<StockBillResponse>> getUnassignedSystemBills() {
        return ResponseEntity.ok(stockService.getUnassignedSystemBills());
    }

    @GetMapping("/bills/unlinked")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<StockBillResponse>> getUnlinkedBills() {
        return ResponseEntity.ok(stockService.getUnlinkedDraftManualBills());
    }

    @GetMapping("/bills/available-for-linking")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<StockBillResponse>> getAvailableSystemBillsForLinking() {
        return ResponseEntity.ok(stockService.getAvailableSystemBillsForLinking());
    }

    /** Bills not yet reduced (neither summary nor individually) — for Stock Item Entry */
    @GetMapping("/bills/not-reduced")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<StockBillResponse>> getBillsNotYetReduced() {
        return ResponseEntity.ok(stockService.getBillsNotYetReduced());
    }

    // ── Per-bill stock status & items ────────────────────────────

    @GetMapping("/bills/{billId}/stock-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<BillStockStatusResponse> getBillStockStatus(@PathVariable Long billId) {
        return ResponseEntity.ok(stockService.getBillStockStatus(billId));
    }

    @GetMapping("/bills/{billId}/items")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<List<BillStockItemResponse>> getBillItems(@PathVariable Long billId) {
        return ResponseEntity.ok(stockService.getBillItems(billId));
    }

    /**
     * Enter reference items on a SYSTEM linking bill for reconciliation.
     * Stores BillStockItems WITHOUT creating stock movements — stock was already
     * reduced when the linked DRAFT/MANUAL bills were processed.
     */
    @PostMapping("/bills/{billId}/reference-items")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<Void> enterReferenceItems(
            @PathVariable Long billId,
            @RequestBody EnterReferenceItemsRequest request) {
        stockService.enterReferenceItemsForSystemBill(billId, request.getItems());
        return ResponseEntity.noContent().build();
    }

    // ── Admin: edit / delete entered stock items ─────────────────

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStockItem(@PathVariable Long id) {
        stockService.deleteStockItem(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/items/{id}/quantity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillStockItemResponse> updateStockItemQuantity(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long qty = body.get("quantity");
        return ResponseEntity.ok(stockService.updateStockItemQuantity(id, qty));
    }

    // ── Stock reduction status tracking ──────────────────────────

    @GetMapping("/reduction-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<List<StockReductionStatusResponse>> getStockReductionStatus() {
        return ResponseEntity.ok(stockService.getStockReductionStatus());
    }

    // ── Create bills (legacy — creates new bill + items) ─────────

    @PostMapping("/bills")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<String> createStockBill(@Valid @RequestBody CreateStockBillRequest request) {
        return ResponseEntity.ok("Bill created: " + stockService.createStockBill(request).getId());
    }

    // ── Individual stock reduction for existing bill ──────────────

    @PostMapping("/bills/reduce")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<Void> reduceStockForBill(@Valid @RequestBody IndividualStockReductionRequest request) {
        stockService.reduceStockForBill(request);
        return ResponseEntity.noContent().build();
    }

    // ── Individual reduction pending requests ─────────────────────

    @GetMapping("/reductions/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StockReductionPendingResponse>> getPendingIndividualReductions() {
        return ResponseEntity.ok(stockService.getPendingIndividualReductions());
    }

    @PatchMapping("/reductions/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveIndividualReduction(@PathVariable Long id) {
        stockService.approveIndividualReduction(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/reductions/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectIndividualReduction(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        stockService.rejectIndividualReduction(id, reason);
        return ResponseEntity.noContent().build();
    }

    // ── Savings collected mark ────────────────────────────────────

    @PatchMapping("/bills/{billId}/savings-collected")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> markSavingsCollected(
            @PathVariable Long billId,
            @RequestBody Map<String, Boolean> body) {
        boolean collected = Boolean.TRUE.equals(body.get("collected"));
        stockService.markSavingsCollected(billId, collected);
        return ResponseEntity.noContent().build();
    }

    // ── Summary load bills ────────────────────────────────────────

    @GetMapping("/summary-load")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<List<SummaryLoadBillResponse>> getAllSummaryLoadBills() {
        return ResponseEntity.ok(stockService.getAllSummaryLoadBills());
    }

    @PostMapping("/summary-load")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<SummaryLoadBillResponse> createSummaryLoadBill(
            @Valid @RequestBody CreateSummaryLoadBillRequest request) {
        return ResponseEntity.ok(stockService.createSummaryLoadBill(request));
    }

    @PatchMapping("/summary-load/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveSummaryLoadBill(@PathVariable Long id) {
        stockService.approveSummaryLoadBill(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/summary-load/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectSummaryLoadBill(@PathVariable Long id) {
        stockService.rejectSummaryLoadBill(id);
        return ResponseEntity.noContent().build();
    }

    // ── Linking system bill creation ──────────────────────────────

    /** Create a SYSTEM bill pre-flagged as willBeLinked=true (stock covered by children) */
    @PostMapping("/bills/linking-system-bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<StockBillResponse> createLinkingSystemBill(
            @Valid @RequestBody CreateLinkingSystemBillRequest request) {
        return ResponseEntity.ok(stockService.createLinkingSystemBill(request));
    }

    // ── End of month linking ──────────────────────────────────────

    /** All unlinked DRAFT/MANUAL bills — for the pending-linking dashboard */
    @GetMapping("/bills/unlinked-dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<StockBillResponse>> getUnlinkedDashboard() {
        return ResponseEntity.ok(stockService.getUnlinkedDraftManualDashboard());
    }

    @PostMapping("/bills/link")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<Void> linkBills(@Valid @RequestBody LinkBillsRequest request) {
        stockService.linkBills(request);
        return ResponseEntity.noContent().build();
    }

    // ── Reconciliation ───────────────────────────────────────────

    /** Admin marks a SYSTEM linking bill as stock-reconciled (final sign-off). */
    @PatchMapping("/bills/{billId}/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> markStockReconciled(@PathVariable Long billId) {
        stockService.markStockReconciled(billId);
        return ResponseEntity.noContent().build();
    }

    // ── Stock balances ────────────────────────────────────────────

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<Map<Long, Long>> getShadowStockBalance() {
        return ResponseEntity.ok(stockService.getShadowStockBalance(BusinessType.RAINCO));
    }

    @GetMapping("/damage-balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER')")
    public ResponseEntity<Map<Long, Long>> getDamageStockBalance() {
        return ResponseEntity.ok(stockService.getDamageStockBalance(BusinessType.RAINCO));
    }
}
