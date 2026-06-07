package com.multi.finance.controller;

import com.multi.finance.dto.response.WorkerBillResponse;
import com.multi.finance.dto.response.WorkerPaymentEntryResponse;
import com.multi.finance.service.impl.WorkerCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/worker-collections")
@RequiredArgsConstructor
public class WorkerCollectionController {

    private final WorkerCollectionService workerCollectionService;

    /** All entries — owner/admin/acc/main_acc can view */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<WorkerPaymentEntryResponse>> getAll() {
        return ResponseEntity.ok(workerCollectionService.getAllEntries());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<WorkerPaymentEntryResponse>> getPending() {
        return ResponseEntity.ok(workerCollectionService.getPendingEntries());
    }

    /** All ASSIGNED bills with worker visit substatus — for admin/acc monitoring */
    @GetMapping("/bills")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<WorkerBillResponse>> getWorkerBills() {
        return ResponseEntity.ok(workerCollectionService.getWorkerAssignedBills());
    }

    /** Confirm a single entry — owner/admin only */
    @PostMapping("/{entryId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> confirm(@PathVariable Long entryId) {
        workerCollectionService.confirmEntry(entryId);
        return ResponseEntity.noContent().build();
    }

    /** Confirm all entries in a combined group */
    @PostMapping("/group/{groupId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> confirmGroup(@PathVariable Long groupId) {
        workerCollectionService.confirmGroup(groupId);
        return ResponseEntity.noContent().build();
    }

    /** Reject an entry */
    @PostMapping("/{entryId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> reject(@PathVariable Long entryId,
                                       @RequestBody Map<String, String> body) {
        workerCollectionService.rejectEntry(entryId, body.getOrDefault("reason", ""));
        return ResponseEntity.noContent().build();
    }
}
