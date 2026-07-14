package com.multi.finance.service.impl;

import com.multi.finance.dto.request.WorkerGroupPaymentRequest;
import com.multi.finance.dto.request.WorkerPaymentRequest;
import com.multi.finance.dto.request.WorkerVisitRequest;
import com.multi.finance.dto.response.WorkerBillOverviewResponse;
import com.multi.finance.dto.response.WorkerBillResponse;
import com.multi.finance.dto.response.WorkerPaymentEntryResponse;
import com.multi.finance.dto.response.WorkerPaymentGroupResponse;
import com.multi.finance.dto.response.WorkerReminderOverviewResponse;
import com.multi.finance.dto.response.WorkerReturnOverviewResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.*;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerPortalService {

    private final WorkerRepository workerRepository;
    private final BillRepository billRepository;
    private final WorkerBillVisitRepository visitRepository;
    private final WorkerPaymentEntryRepository entryRepository;
    private final WorkerPaymentGroupRepository groupRepository;
    private final BillReminderRepository reminderRepository;
    private final BillReturnRepository billReturnRepository;

    // ── Helper ──────────────────────────────────────────────────────

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private Worker getWorkerForCurrentUser() {
        User user = getCurrentUser();
        return workerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("No worker profile linked to this account"));
    }

    // ── Bills ────────────────────────────────────────────────────────

    /**
     * All currently ASSIGNED bills visible to the worker.
     * Worker sees every ASSIGNED bill regardless of which worker is listed as holder.
     */
    @Transactional(readOnly = true)
    public List<WorkerBillResponse> getAssignedBills() {
        Worker worker = getWorkerForCurrentUser();
        List<Bill> bills = billRepository.findByStatus(BillStatus.ASSIGNED);

        List<Long> billIds = bills.stream().map(Bill::getId).collect(Collectors.toList());

        // Batch load visit statuses for this worker
        Map<Long, WorkerBillVisit> visitMap = visitRepository
                .findByBillIdsAndWorkerId(billIds, worker.getId())
                .stream().collect(Collectors.toMap(v -> v.getBill().getId(), v -> v));

        // Batch load pending payment totals
        Map<Long, BigDecimal> pendingMap = new HashMap<>();
        for (Long billId : billIds) {
            pendingMap.put(billId, entryRepository.sumPendingForBill(billId));
        }

        // Batch load management notes (PENDING reminders)
        Map<Long, List<String>> notesMap = new HashMap<>();
        for (Long billId : billIds) {
            List<String> notes = reminderRepository.findByBillId(billId).stream()
                    .filter(r -> r.getStatus() == ReminderStatus.PENDING && r.getNote() != null)
                    .map(BillReminder::getNote)
                    .collect(Collectors.toList());
            notesMap.put(billId, notes);
        }

        return bills.stream().map(b -> {
            WorkerBillVisit visit = visitMap.get(b.getId());
            BigDecimal pending = pendingMap.getOrDefault(b.getId(), BigDecimal.ZERO);
            BigDecimal tempBalance = b.getBalanceRemaining().subtract(pending);

            return WorkerBillResponse.builder()
                    .id(b.getId())
                    .billNumber(b.getBillNumber())
                    .customerName(b.getCustomerName())
                    .area(b.getArea())
                    .business(b.getBusiness().name())
                    .totalAmount(b.getTotalAmount())
                    .balanceRemaining(b.getBalanceRemaining())
                    .tempBalance(tempBalance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : tempBalance)
                    .workerPendingTotal(pending)
                    .billDate(b.getBillDate())
                    .workerName(b.getCurrentHolder() != null ? b.getCurrentHolder().getFullName() : null)
                    .visitStatus(visit != null ? visit.getVisitStatus() : WorkerVisitStatus.NOT_VISITED)
                    .visitNote(visit != null ? visit.getWorkerNote() : null)
                    .managementNotes(notesMap.getOrDefault(b.getId(), List.of()))
                    .workerPayments(List.of()) // lite list view — full detail loaded separately
                    .collectionOnly(Boolean.TRUE.equals(b.getCollectionOnly()))
                    .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().toString() : null)
                    .build();
        }).sorted(Comparator.comparing(r -> r.getVisitStatus().ordinal()))
                .collect(Collectors.toList());
    }

    /** Full bill detail including worker's entered payments */
    @Transactional(readOnly = true)
    public WorkerBillResponse getBillDetail(Long billId) {
        Worker worker = getWorkerForCurrentUser();
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        WorkerBillVisit visit = visitRepository.findByBillIdAndWorkerId(billId, worker.getId()).orElse(null);
        BigDecimal pending = entryRepository.sumPendingForBill(billId);
        BigDecimal tempBalance = bill.getBalanceRemaining().subtract(pending);

        List<String> notes = reminderRepository.findByBillId(billId).stream()
                .filter(r -> r.getStatus() == ReminderStatus.PENDING && r.getNote() != null)
                .map(BillReminder::getNote)
                .collect(Collectors.toList());

        List<WorkerPaymentEntryResponse> payments = entryRepository.findByBillId(billId).stream()
                .filter(e -> e.getWorker().getId().equals(worker.getId()))
                .map(this::toEntryResponse)
                .collect(Collectors.toList());

        return WorkerBillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .customerName(bill.getCustomerName())
                .area(bill.getArea())
                .business(bill.getBusiness().name())
                .totalAmount(bill.getTotalAmount())
                .balanceRemaining(bill.getBalanceRemaining())
                .tempBalance(tempBalance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : tempBalance)
                .workerPendingTotal(pending)
                .billDate(bill.getBillDate())
                .workerName(bill.getCurrentHolder() != null ? bill.getCurrentHolder().getFullName() : null)
                .visitStatus(visit != null ? visit.getVisitStatus() : WorkerVisitStatus.NOT_VISITED)
                .visitNote(visit != null ? visit.getWorkerNote() : null)
                .managementNotes(notes)
                .workerPayments(payments)
                .collectionOnly(Boolean.TRUE.equals(bill.getCollectionOnly()))
                .createdAt(bill.getCreatedAt() != null ? bill.getCreatedAt().toString() : null)
                .build();
    }

    // ── Visit / Delivery Status ───────────────────────────────────────

    @Transactional
    public void markVisit(Long billId, WorkerVisitRequest request) {
        Worker worker = getWorkerForCurrentUser();

        if (request.getVisitStatus() == WorkerVisitStatus.REVISIT_REQUESTED
                && (request.getWorkerNote() == null || request.getWorkerNote().isBlank())) {
            throw new RuntimeException("A reason is required when requesting a revisit (e.g. 'Come next Friday')");
        }

        WorkerBillVisit visit = visitRepository.findByBillIdAndWorkerId(billId, worker.getId())
                .orElse(WorkerBillVisit.builder()
                        .bill(billRepository.findById(billId).orElseThrow())
                        .worker(worker)
                        .build());

        visit.setVisitStatus(request.getVisitStatus());
        visit.setWorkerNote(request.getWorkerNote());
        visit.setVisitedAt(LocalDateTime.now());
        visit.setUpdatedAt(LocalDateTime.now());
        visitRepository.save(visit);
    }

    // ── Payments ─────────────────────────────────────────────────────

    @Transactional
    public WorkerPaymentEntryResponse enterPayment(WorkerPaymentRequest request) {
        Worker worker = getWorkerForCurrentUser();
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        if (request.getPaymentType() == PaymentType.CHEQUE
                && (request.getChequeNumber() == null || request.getChequeNumber().isBlank())) {
            throw new RuntimeException("Cheque number is required for cheque payments");
        }

        WorkerPaymentEntry entry = WorkerPaymentEntry.builder()
                .bill(bill)
                .worker(worker)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .chequeNumber(request.getChequeNumber())
                .bankName(request.getBankName())
                .branchName(request.getBranchName())
                .workerNote(request.getWorkerNote())
                .status(WorkerPaymentStatus.PENDING)
                .enteredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        entry = entryRepository.save(entry);

        // Auto-update visit status if not already set to delivered
        visitRepository.findByBillIdAndWorkerId(bill.getId(), worker.getId()).ifPresent(v -> {
            if (v.getVisitStatus() == WorkerVisitStatus.NOT_VISITED
                    || v.getVisitStatus() == WorkerVisitStatus.REVISIT_REQUESTED) {
                v.setVisitStatus(WorkerVisitStatus.DELIVERED_PENDING);
                v.setUpdatedAt(LocalDateTime.now());
                visitRepository.save(v);
            }
        });

        return toEntryResponse(entry);
    }

    @Transactional
    public WorkerPaymentGroupResponse enterGroupPayment(WorkerGroupPaymentRequest request) {
        Worker worker = getWorkerForCurrentUser();

        if (request.getPaymentType() == PaymentType.CHEQUE
                && (request.getChequeNumber() == null || request.getChequeNumber().isBlank())) {
            throw new RuntimeException("Cheque number is required");
        }

        BigDecimal total = request.getBills().stream()
                .map(WorkerGroupPaymentRequest.BillAmount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        WorkerPaymentGroup group = WorkerPaymentGroup.builder()
                .worker(worker)
                .paymentType(request.getPaymentType())
                .chequeNumber(request.getChequeNumber())
                .bankName(request.getBankName())
                .branchName(request.getBranchName())
                .totalAmount(total)
                .workerNote(request.getWorkerNote())
                .status(WorkerPaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        group = groupRepository.save(group);

        List<WorkerPaymentEntry> entries = new ArrayList<>();
        for (WorkerGroupPaymentRequest.BillAmount ba : request.getBills()) {
            Bill bill = billRepository.findById(ba.getBillId())
                    .orElseThrow(() -> new RuntimeException("Bill not found: " + ba.getBillId()));

            WorkerPaymentEntry entry = WorkerPaymentEntry.builder()
                    .bill(bill)
                    .worker(worker)
                    .group(group)
                    .amount(ba.getAmount())
                    .paymentType(request.getPaymentType())
                    .chequeNumber(request.getChequeNumber())
                    .bankName(request.getBankName())
                    .branchName(request.getBranchName())
                    .workerNote(request.getWorkerNote())
                    .status(WorkerPaymentStatus.PENDING)
                    .enteredAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build();
            entries.add(entryRepository.save(entry));

            // Auto-update visit status
            visitRepository.findByBillIdAndWorkerId(bill.getId(), worker.getId()).ifPresent(v -> {
                if (v.getVisitStatus() == WorkerVisitStatus.NOT_VISITED
                        || v.getVisitStatus() == WorkerVisitStatus.REVISIT_REQUESTED) {
                    v.setVisitStatus(WorkerVisitStatus.DELIVERED_PENDING);
                    v.setUpdatedAt(LocalDateTime.now());
                    visitRepository.save(v);
                }
            });
        }

        return toGroupResponse(group, entries);
    }

    @Transactional
    public WorkerPaymentEntryResponse editPayment(Long entryId, WorkerPaymentRequest request) {
        Worker worker = getWorkerForCurrentUser();
        WorkerPaymentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Payment entry not found"));

        if (!entry.getWorker().getId().equals(worker.getId())) {
            throw new RuntimeException("You can only edit your own payment entries");
        }
        if (entry.getStatus() != WorkerPaymentStatus.PENDING) {
            throw new RuntimeException("Only pending entries can be edited");
        }

        entry.setAmount(request.getAmount());
        entry.setPaymentType(request.getPaymentType());
        entry.setChequeNumber(request.getChequeNumber());
        entry.setBankName(request.getBankName());
        entry.setBranchName(request.getBranchName());
        entry.setWorkerNote(request.getWorkerNote());
        return toEntryResponse(entryRepository.save(entry));
    }

    @Transactional
    public void deletePayment(Long entryId) {
        Worker worker = getWorkerForCurrentUser();
        WorkerPaymentEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Payment entry not found"));
        if (!entry.getWorker().getId().equals(worker.getId())) {
            throw new RuntimeException("You can only delete your own payment entries");
        }
        if (entry.getStatus() != WorkerPaymentStatus.PENDING) {
            throw new RuntimeException("Only pending entries can be deleted");
        }
        entryRepository.delete(entry);
    }

    /** Today's payments entered by this worker */
    @Transactional(readOnly = true)
    public List<WorkerPaymentEntryResponse> getTodayPayments() {
        Worker worker = getWorkerForCurrentUser();
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);
        return entryRepository
                .findByWorkerIdAndEnteredAtBetweenOrderByEnteredAtDesc(worker.getId(), start, end)
                .stream().map(this::toEntryResponse).collect(Collectors.toList());
    }

    // ── Overview Queries (all bills/returns/reminders) ────────────────

    private static final List<BillStatus> PENDING_STATUSES = List.of(
            BillStatus.CREATED, BillStatus.ASSIGNED, BillStatus.SHOP_WORKER_ASSIGNED,
            BillStatus.STORE_RECEIVED, BillStatus.SHOP_RECEIVED);

    @Transactional(readOnly = true)
    public List<WorkerBillOverviewResponse> getAllBillsOverview(String view) {
        List<Bill> bills;
        if ("completed".equalsIgnoreCase(view)) {
            LocalDate cutoff = LocalDate.now().minusDays(60);
            bills = billRepository.findByStatusAndBillDateBetweenOrderByCreatedAtDesc(
                    BillStatus.COMPLETED, cutoff, LocalDate.now());
        } else {
            bills = billRepository.findByStatusInOrderByCreatedAtDesc(PENDING_STATUSES);
        }
        return bills.stream().map(b -> WorkerBillOverviewResponse.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .customerName(b.getCustomerName())
                .area(b.getArea())
                .status(b.getStatus().name())
                .business(b.getBusiness().name())
                .billType(b.getBillType().name())
                .billDate(b.getBillDate().toString())
                .totalAmount(b.getTotalAmount())
                .balanceRemaining(b.getBalanceRemaining())
                .workerName(b.getCurrentHolder() != null ? b.getCurrentHolder().getFullName() : null)
                .collectionOnly(Boolean.TRUE.equals(b.getCollectionOnly()))
                .createdAt(b.getCreatedAt() != null ? b.getCreatedAt().toString() : null)
                .build()).collect(Collectors.toList());
    }

    @Transactional
    public void toggleCollectionOnly(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        bill.setCollectionOnly(!Boolean.TRUE.equals(bill.getCollectionOnly()));
        billRepository.save(bill);
    }

    @Transactional(readOnly = true)
    public List<WorkerReturnOverviewResponse> getPendingReturns() {
        return billReturnRepository.findByStatusOrderBySubmittedAtDesc(ReturnStatus.PENDING)
                .stream().map(r -> WorkerReturnOverviewResponse.builder()
                        .id(r.getId())
                        .billId(r.getBill().getId())
                        .billNumber(r.getBill().getBillNumber())
                        .customerName(r.getBill().getCustomerName())
                        .area(r.getBill().getArea())
                        .returnType(r.getReturnType().name())
                        .status(r.getStatus().name())
                        .itemsTotal(r.getItemsTotal())
                        .calculatedReturnAmount(r.getCalculatedReturnAmount())
                        .notes(r.getNotes())
                        .responsibleWorkerName(r.getResponsibleWorker() != null ? r.getResponsibleWorker().getFullName() : null)
                        .submittedAt(r.getSubmittedAt().toString())
                        .build()).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkerReminderOverviewResponse> getPendingReminders() {
        return reminderRepository.findByStatus(ReminderStatus.PENDING)
                .stream().map(r -> WorkerReminderOverviewResponse.builder()
                        .id(r.getId())
                        .billId(r.getBill().getId())
                        .billNumber(r.getBill().getBillNumber())
                        .customerName(r.getBill().getCustomerName())
                        .area(r.getBill().getArea())
                        .reminderDate(r.getReminderDate().toString())
                        .period(r.getPeriod().name())
                        .note(r.getNote())
                        .status(r.getStatus().name())
                        .createdAt(r.getCreatedAt().toString())
                        .build()).collect(Collectors.toList());
    }

    // ── Mappers ───────────────────────────────────────────────────────

    private WorkerPaymentEntryResponse toEntryResponse(WorkerPaymentEntry e) {
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

    private WorkerPaymentGroupResponse toGroupResponse(WorkerPaymentGroup g, List<WorkerPaymentEntry> entries) {
        return WorkerPaymentGroupResponse.builder()
                .id(g.getId())
                .workerName(g.getWorker().getFullName())
                .paymentType(g.getPaymentType())
                .chequeNumber(g.getChequeNumber())
                .bankName(g.getBankName())
                .branchName(g.getBranchName())
                .totalAmount(g.getTotalAmount())
                .status(g.getStatus())
                .workerNote(g.getWorkerNote())
                .createdAt(g.getCreatedAt())
                .entries(entries.stream().map(this::toEntryResponse).collect(Collectors.toList()))
                .build();
    }
}
