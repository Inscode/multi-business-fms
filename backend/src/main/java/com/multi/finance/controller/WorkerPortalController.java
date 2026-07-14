package com.multi.finance.controller;

import com.multi.finance.dto.request.WorkerGroupPaymentRequest;
import com.multi.finance.dto.request.WorkerPaymentRequest;
import com.multi.finance.dto.request.WorkerVisitRequest;
import com.multi.finance.dto.response.WorkerBillOverviewResponse;
import com.multi.finance.dto.response.WorkerBillResponse;
import com.multi.finance.dto.response.WorkerPaymentEntryResponse;
import com.multi.finance.dto.response.WorkerPaymentGroupResponse;
import com.multi.finance.dto.response.WorkerReminderOverviewResponse;
import com.multi.finance.dto.response.WorkerReturnOverviewResponse;
import com.multi.finance.service.impl.WorkerPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WORKER')")
public class WorkerPortalController {

    private final WorkerPortalService workerPortalService;

    @GetMapping("/bills")
    public ResponseEntity<List<WorkerBillResponse>> getAssignedBills() {
        return ResponseEntity.ok(workerPortalService.getAssignedBills());
    }

    @GetMapping("/bills/{billId}")
    public ResponseEntity<WorkerBillResponse> getBillDetail(@PathVariable Long billId) {
        return ResponseEntity.ok(workerPortalService.getBillDetail(billId));
    }

    @PostMapping("/bills/{billId}/visit")
    public ResponseEntity<Void> markVisit(
            @PathVariable Long billId,
            @Valid @RequestBody WorkerVisitRequest request) {
        workerPortalService.markVisit(billId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/payments")
    public ResponseEntity<WorkerPaymentEntryResponse> enterPayment(
            @Valid @RequestBody WorkerPaymentRequest request) {
        return ResponseEntity.ok(workerPortalService.enterPayment(request));
    }

    @PostMapping("/payments/group")
    public ResponseEntity<WorkerPaymentGroupResponse> enterGroupPayment(
            @Valid @RequestBody WorkerGroupPaymentRequest request) {
        return ResponseEntity.ok(workerPortalService.enterGroupPayment(request));
    }

    @PutMapping("/payments/{entryId}")
    public ResponseEntity<WorkerPaymentEntryResponse> editPayment(
            @PathVariable Long entryId,
            @Valid @RequestBody WorkerPaymentRequest request) {
        return ResponseEntity.ok(workerPortalService.editPayment(entryId, request));
    }

    @DeleteMapping("/payments/{entryId}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long entryId) {
        workerPortalService.deletePayment(entryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payments/today")
    public ResponseEntity<List<WorkerPaymentEntryResponse>> getTodayPayments() {
        return ResponseEntity.ok(workerPortalService.getTodayPayments());
    }

    @GetMapping("/bills/overview")
    public ResponseEntity<List<WorkerBillOverviewResponse>> getBillsOverview(
            @RequestParam(defaultValue = "pending") String view) {
        return ResponseEntity.ok(workerPortalService.getAllBillsOverview(view));
    }

    @GetMapping("/returns/overview")
    public ResponseEntity<List<WorkerReturnOverviewResponse>> getReturnsOverview() {
        return ResponseEntity.ok(workerPortalService.getPendingReturns());
    }

    @GetMapping("/reminders/overview")
    public ResponseEntity<List<WorkerReminderOverviewResponse>> getRemindersOverview() {
        return ResponseEntity.ok(workerPortalService.getPendingReminders());
    }
}
