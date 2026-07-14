package com.multi.finance.controller;

import com.multi.finance.dto.response.WorkerBillResponse;
import com.multi.finance.dto.response.WorkerPaymentEntryResponse;
import com.multi.finance.service.impl.CollectionNoteServiceImpl;
import com.multi.finance.service.impl.WorkerCollectionService;
import com.multi.finance.dto.response.CollectionNoteResponse;
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
    private final CollectionNoteServiceImpl collectionNoteService;

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

    /** Hard-delete a worker entry (admin/owner only) — also restores balance if confirmed */
    @DeleteMapping("/{entryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> hardDelete(@PathVariable Long entryId) {
        workerCollectionService.hardDeleteEntry(entryId);
        return ResponseEntity.noContent().build();
    }

    /** PENDING worker entries for a specific bill — used by bill-detail to show status */
    @GetMapping("/pending-for-bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<WorkerPaymentEntryResponse>> getPendingForBill(@PathVariable Long billId) {
        return ResponseEntity.ok(workerCollectionService.getPendingEntriesForBill(billId));
    }

    /** Collection notes for a specific bill — pending notes show the acc what to record */
    @GetMapping("/collection-notes-for-bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<CollectionNoteResponse>> getCollectionNotesForBill(@PathVariable Long billId) {
        return ResponseEntity.ok(collectionNoteService.getByBillId(billId));
    }
}
