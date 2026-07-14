package com.multi.finance.service.impl;

import com.multi.finance.dto.response.WorkerBillResponse;
import com.multi.finance.dto.response.WorkerPaymentEntryResponse;
import com.multi.finance.dto.response.WorkerPaymentGroupResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.*;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerCollectionService {

    private final WorkerPaymentEntryRepository entryRepository;
    private final WorkerPaymentGroupRepository groupRepository;
    private final WorkerBillVisitRepository visitRepository;
    private final CollectionNoteRepository collectionNoteRepository;
    private final BillRepository billRepository;

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // ── Read (all roles with access) ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkerPaymentEntryResponse> getAllEntries() {
        return entryRepository.findAllWithDetails().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkerPaymentEntryResponse> getPendingEntries() {
        return entryRepository.findByStatusWithDetails(WorkerPaymentStatus.PENDING).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    // ── Confirm single entry (owner/admin) ───────────────────────────

    @Transactional
    public void confirmEntry(Long entryId) {
        User confirmer = getCurrentUser();
        WorkerPaymentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Entry not found"));

        if (entry.getStatus() != WorkerPaymentStatus.PENDING) {
            throw new RuntimeException("Only PENDING entries can be confirmed");
        }

        entry.setStatus(WorkerPaymentStatus.OWNER_CONFIRMED);
        entry.setConfirmedBy(confirmer);
        entry.setConfirmedAt(LocalDateTime.now());
        entryRepository.save(entry);

        Bill bill = billRepository.findById(entry.getBill().getId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        // Create CollectionNote — balance deducts when acc enters the formal payment
        CollectionNote note = CollectionNote.builder()
                .bill(bill)
                .amount(entry.getAmount())
                .paymentType(entry.getPaymentType())
                .status(CollectionNoteStatus.PENDING)
                .collectedBy(confirmer)
                .collectedAt(LocalDateTime.now())
                .notes("Worker: " + entry.getWorker().getFullName()
                        + (entry.getWorkerNote() != null ? " — " + entry.getWorkerNote() : ""))
                .chequeNumber(entry.getChequeNumber())
                .bankName(entry.getBankName())
                .branchName(entry.getBranchName())
                .sourceEntryId(entry.getId())
                .build();
        collectionNoteRepository.save(note);

        // Update visit status to DELIVERED_PAID
        updateVisitPaid(entry.getBill().getId(), entry.getWorker().getId());
    }

    /** Confirm all PENDING entries in a group at once */
    @Transactional
    public void confirmGroup(Long groupId) {
        User confirmer = getCurrentUser();
        WorkerPaymentGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Payment group not found"));

        if (group.getStatus() != WorkerPaymentStatus.PENDING) {
            throw new RuntimeException("Only PENDING groups can be confirmed");
        }

        group.setStatus(WorkerPaymentStatus.OWNER_CONFIRMED);
        groupRepository.save(group);

        List<WorkerPaymentEntry> entries = entryRepository.findByGroupId(groupId);

        for (WorkerPaymentEntry entry : entries) {
            if (entry.getStatus() == WorkerPaymentStatus.PENDING) {
                entry.setStatus(WorkerPaymentStatus.OWNER_CONFIRMED);
                entry.setConfirmedBy(confirmer);
                entry.setConfirmedAt(LocalDateTime.now());
                entryRepository.save(entry);

                Bill bill = billRepository.findById(entry.getBill().getId())
                        .orElseThrow(() -> new RuntimeException("Bill not found"));

                CollectionNote note = CollectionNote.builder()
                        .bill(bill)
                        .amount(entry.getAmount())
                        .paymentType(entry.getPaymentType())
                        .status(CollectionNoteStatus.PENDING)
                        .collectedBy(confirmer)
                        .collectedAt(LocalDateTime.now())
                        .notes("Worker: " + entry.getWorker().getFullName()
                                + " | Combined payment group #" + groupId
                                + (entry.getWorkerNote() != null ? " — " + entry.getWorkerNote() : ""))
                        .chequeNumber(entry.getChequeNumber())
                        .bankName(entry.getBankName())
                        .branchName(entry.getBranchName())
                        .sourceEntryId(entry.getId())
                        .build();
                collectionNoteRepository.save(note);

                updateVisitPaid(entry.getBill().getId(), entry.getWorker().getId());
            }
        }
    }

    @Transactional
    public void rejectEntry(Long entryId, String reason) {
        WorkerPaymentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        if (entry.getStatus() != WorkerPaymentStatus.PENDING) {
            throw new RuntimeException("Only PENDING entries can be rejected");
        }
        entry.setStatus(WorkerPaymentStatus.REJECTED);
        entry.setRejectedReason(reason);
        entryRepository.save(entry);
    }

    // ── Worker bills view for admin/acc ──────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkerBillResponse> getWorkerAssignedBills() {
        List<Bill> bills = billRepository.findByStatus(BillStatus.ASSIGNED);
        return bills.stream().map(b -> {
            List<WorkerBillVisit> visits = visitRepository.findByBillId(b.getId());
            String visitNote = visits.stream()
                    .filter(v -> v.getWorkerNote() != null)
                    .map(v -> v.getWorker().getFullName() + ": " + v.getWorkerNote())
                    .findFirst().orElse(null);
            WorkerVisitStatus status = visits.stream()
                    .map(WorkerBillVisit::getVisitStatus)
                    .max(java.util.Comparator.comparingInt(WorkerVisitStatus::ordinal))
                    .orElse(WorkerVisitStatus.NOT_VISITED);

            return WorkerBillResponse.builder()
                    .id(b.getId())
                    .billNumber(b.getBillNumber())
                    .customerName(b.getCustomerName())
                    .area(b.getArea())
                    .business(b.getBusiness().name())
                    .totalAmount(b.getTotalAmount())
                    .balanceRemaining(b.getBalanceRemaining())
                    .tempBalance(b.getBalanceRemaining())
                    .workerPendingTotal(java.math.BigDecimal.ZERO)
                    .billDate(b.getBillDate())
                    .workerName(b.getCurrentHolder() != null ? b.getCurrentHolder().getFullName() : null)
                    .visitStatus(status)
                    .visitNote(visitNote)
                    .managementNotes(List.of())
                    .workerPayments(List.of())
                    .build();
        }).collect(Collectors.toList());
    }

    /** Hard-delete a worker entry (admin only). Removes the linked CollectionNote if PENDING. */
    @Transactional
    public void hardDeleteEntry(Long entryId) {
        WorkerPaymentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Entry not found"));

        // Balance was never deducted at confirmation — only deducts when acc enters formal payment.
        // If the CollectionNote is already MATCHED (acc has entered payment), the balance was
        // deducted at that point and should not be reversed here (use payment reject/delete for that).
        collectionNoteRepository.deleteBySourceEntryId(entryId);

        entryRepository.delete(entry);
    }

    /** Returns PENDING worker entries for a specific bill (for bill-detail status display). */
    @Transactional(readOnly = true)
    public List<WorkerPaymentEntryResponse> getPendingEntriesForBill(Long billId) {
        return entryRepository.findByBillIdAndStatus(billId, WorkerPaymentStatus.PENDING)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private void applyBalanceDeduction(Bill bill, BigDecimal amount) {
        BigDecimal newBalance = bill.getBalanceRemaining().subtract(amount);
        bill.setBalanceRemaining(newBalance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newBalance);
        bill.setAmountPaid(bill.getAmountPaid().add(amount));
        bill.setFullyPaid(bill.getBalanceRemaining().compareTo(BigDecimal.ZERO) <= 0);
        if (Boolean.TRUE.equals(bill.getFullyPaid())) {
            bill.setStatus(BillStatus.COMPLETED);
        }
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    private void restoreBalance(Bill bill, BigDecimal amount) {
        BigDecimal newBalance = bill.getBalanceRemaining().add(amount);
        BigDecimal newPaid = bill.getAmountPaid().subtract(amount);
        bill.setBalanceRemaining(newBalance);
        bill.setAmountPaid(newPaid.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newPaid);
        bill.setFullyPaid(false);
        if (bill.getStatus() == BillStatus.COMPLETED) {
            bill.setStatus(BillStatus.STORE_RECEIVED);
        }
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    private void updateVisitPaid(Long billId, Long workerId) {
        visitRepository.findByBillIdAndWorkerId(billId, workerId).ifPresent(v -> {
            v.setVisitStatus(WorkerVisitStatus.DELIVERED_PAID);
            v.setUpdatedAt(LocalDateTime.now());
            visitRepository.save(v);
        });
    }

    private WorkerPaymentEntryResponse toResponse(WorkerPaymentEntry e) {
        return WorkerPaymentEntryResponse.builder()
                .id(e.getId())
                .billId(e.getBill().getId())
                .billNumber(e.getBill().getBillNumber())
                .customerName(e.getBill().getCustomerName())
                .groupId(e.getGroup() != null ? e.getGroup().getId() : null)
                .workerName(e.getWorker().getFullName())
                .amount(e.getAmount())
                .paymentType(e.getPaymentType())
                .chequeNumber(e.getChequeNumber())
                .bankName(e.getBankName())
                .branchName(e.getBranchName())
                .status(e.getStatus())
                .workerNote(e.getWorkerNote())
                .enteredAt(e.getEnteredAt())
                .confirmedAt(e.getConfirmedAt())
                .confirmedByName(e.getConfirmedBy() != null ? e.getConfirmedBy().getFullName() : null)
                .rejectedReason(e.getRejectedReason())
                .build();
    }
}
