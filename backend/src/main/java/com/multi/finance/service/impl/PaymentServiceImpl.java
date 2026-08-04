package com.multi.finance.service.impl;


import com.multi.finance.dto.request.BulkPaymentRequest;
import com.multi.finance.dto.request.PaymentRequest;
import com.multi.finance.dto.response.PaymentGroupResponse;
import com.multi.finance.dto.response.PaymentResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.CollectionNote;
import com.multi.finance.entity.Payment;
import com.multi.finance.entity.PaymentGroup;
import com.multi.finance.entity.User;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.CollectionNoteStatus;
import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.enums.PaymentType;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.entity.Worker;
import com.multi.finance.repository.CollectionNoteRepository;
import com.multi.finance.repository.PaymentGroupRepository;
import com.multi.finance.repository.PaymentRepository;
import com.multi.finance.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final PaymentGroupRepository paymentGroupRepository;
    private final CollectionNoteRepository collectionNoteRepository;
    private final WorkerRepository workerRepository;

    @Transactional
    public PaymentResponse enterPayment(
            Long billId,
            PaymentRequest request
    ) {

        Bill bill = billRepository.findById(billId)
                .orElseThrow(() ->
                        new RuntimeException("Bill not found"));

        if (bill.getFullyPaid()) {
            throw new RuntimeException(
                    "Bill is already fully paid");
        }

        // SHOP_ACCOUNTANT can only enter payments for shop-visible bills
        User caller = getCurrentUser();
        if (caller.getRole() == UserRole.SHOP_ACCOUNTANT) {
            List<BillStatus> allowed = List.of(
                    BillStatus.ASSIGNED,
                    BillStatus.SHOP_RECEIVED,
                    BillStatus.SHOP_WORKER_ASSIGNED
            );
            if (!allowed.contains(bill.getStatus())) {
                throw new RuntimeException(
                        "Shop accountant can only enter payments for bills with status: ASSIGNED, SHOP_RECEIVED or SHOP_WORKER_ASSIGNED");
            }
        }

        // Balance is deducted immediately at entry time.
        // balanceRemaining already reflects all previously entered payments.
        if (request.getAmount().compareTo(bill.getBalanceRemaining()) > 0) {
            throw new RuntimeException("Payment amount exceeds remaining balance (Rs " +
                    bill.getBalanceRemaining().toPlainString() + ")");
        }

        if (request.getPaymentType() == PaymentType.CHEQUE) {
            if (request.getChequeNumber() == null ||
                    request.getBankName() == null ||
                    request.getChequeDate() == null) {
                throw new RuntimeException("Cheque details are required");
            }
        }

        boolean isPartial = request.getAmount().compareTo(bill.getBalanceRemaining()) < 0;

        CollectionNote collectionNote = null;
        if (request.getCollectionNoteId() != null) {
            collectionNote = collectionNoteRepository.findById(request.getCollectionNoteId())
                    .orElseThrow(() -> new RuntimeException("Collection note not found"));
            if (collectionNote.getStatus() == CollectionNoteStatus.MATCHED) {
                throw new RuntimeException("This collection note is already matched to a payment");
            }
        }

        Worker collectedByWorker = null;
        if (request.getCollectedByWorkerId() != null) {
            collectedByWorker = workerRepository.findById(request.getCollectedByWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
        }

        // An admin entering a payment is itself the confirmation — no second pass needed.
        boolean autoConfirm = caller.getRole() == UserRole.ADMIN;

        Payment payment = Payment.builder()
                .bill(bill)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .status(autoConfirm ? PaymentStatus.CONFIRMED : PaymentStatus.ENTERED)
                .confirmedBy(autoConfirm ? caller : null)
                .confirmedAt(autoConfirm ? LocalDateTime.now() : null)
                .isPartial(isPartial)
                .enteredBy(caller)
                .paymentDate(
                        request.getPaymentDate() != null
                                ? request.getPaymentDate()
                                : LocalDate.now()
                )
                .referenceNumber(request.getReferenceNumber())
                .bankName(request.getBankName())
                .branchName(request.getBranchName())
                .chequeNumber(request.getChequeNumber())
                .chequeDate(request.getChequeDate())
                .notes(request.getNotes())
                .collectionNote(collectionNote)
                .collectedByWorker(collectedByWorker)
                .collectorNote(request.getCollectorNote())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        // Mark the collection note as matched
        if (collectionNote != null) {
            collectionNote.setStatus(CollectionNoteStatus.MATCHED);
            collectionNoteRepository.save(collectionNote);
        }

        // Deduct balance immediately at entry — applies to direct payments and CollectionNote-linked
        // payments alike. Worker-confirmed CollectionNotes no longer pre-deduct at confirmation.
        applyBalanceDeduction(bill, payment.getAmount());

        return toResponse(payment, bill);
    }

    /**
     * Turns an admin-recorded CASH collection straight into a confirmed payment, so the
     * accountant does not have to re-enter it. The note is marked MATCHED by the caller.
     */
    @Transactional
    public PaymentResponse recordConfirmedCollection(CollectionNote note) {
        Bill bill = note.getBill();
        User admin = note.getCollectedBy();

        if (Boolean.TRUE.equals(bill.getFullyPaid())) {
            throw new RuntimeException("Bill " + bill.getBillNumber() + " is already fully paid");
        }
        if (note.getAmount().compareTo(bill.getBalanceRemaining()) > 0) {
            throw new RuntimeException("Collected amount exceeds remaining balance for bill "
                    + bill.getBillNumber() + " (Rs " + bill.getBalanceRemaining().toPlainString() + ")");
        }

        boolean isPartial = note.getAmount().compareTo(bill.getBalanceRemaining()) < 0;
        LocalDateTime now = LocalDateTime.now();

        Payment payment = Payment.builder()
                .bill(bill)
                .amount(note.getAmount())
                .paymentType(note.getPaymentType())
                .status(PaymentStatus.CONFIRMED)
                .isPartial(isPartial)
                .enteredBy(admin)
                .confirmedBy(admin)
                .confirmedAt(now)
                .paymentDate(note.getCollectedAt().toLocalDate())
                .notes(note.getNotes())
                .collectionNote(note)
                .createdAt(now)
                .updatedAt(now)
                .build();

        paymentRepository.save(payment);
        applyBalanceDeduction(bill, payment.getAmount());

        return toResponse(payment, bill);
    }

    @Transactional
    public PaymentResponse confirmPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            throw new RuntimeException("Payment is already confirmed");
        }
        if (payment.getStatus() == PaymentStatus.REJECTED) {
            throw new RuntimeException("Cannot confirm a rejected payment");
        }

        // Balance was already deducted at entry — confirmation is now just a status mark.
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setConfirmedBy(getCurrentUser());
        payment.setConfirmedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // This may have been the last unconfirmed payment — the bill can now complete.
        syncCompletionStatus(payment.getBill());

        return toResponse(payment, payment.getBill());
    }

    @Transactional
    public PaymentResponse rejectPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            throw new RuntimeException("Cannot reject an already confirmed payment. Use cheque return for confirmed payments.");
        }
        if (payment.getStatus() == PaymentStatus.REJECTED) {
            throw new RuntimeException("Payment is already rejected");
        }

        // Restore balance for all rejected payments — balance was deducted at entry for all types.
        restoreBalance(payment.getBill(), payment.getAmount());

        payment.setStatus(PaymentStatus.REJECTED);
        payment.setReturnReason(reason != null ? reason : "Rejected by admin");
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return toResponse(payment, payment.getBill());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByBill(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        return paymentRepository.findByBillId(billId)
                .stream()
                .map(p -> toResponse(p, bill))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPendingConfirmations() {
        return paymentRepository.findByStatus(PaymentStatus.ENTERED)
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getTodaysPayments() {
        return paymentRepository.findByPaymentDate(LocalDate.now())
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .toList();
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    @Transactional
    public PaymentResponse markChequeReturned(Long paymentId, String returnReason) {
        Payment payment = getPaymentById(paymentId);

        if (payment.getPaymentType() != PaymentType.CHEQUE) {
            throw new RuntimeException("Only cheque payments can be returned");
        }

        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed payments can be marked returned");
        }

        Bill bill = payment.getBill();
        BigDecimal restored = bill.getAmountPaid().subtract(payment.getAmount());
        BigDecimal newBalance = bill.getTotalAmount().subtract(restored);

        bill.setAmountPaid(restored);
        bill.setBalanceRemaining(newBalance);
        bill.setFullyPaid(false);
        bill.setStatus(BillStatus.STORE_RECEIVED);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);

        payment.setStatus(PaymentStatus.RETURNED);
        payment.setReturnReason(returnReason);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return toResponse(payment, bill);
    }

    @Transactional
    public void deletePayment(Long paymentId) {
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() != PaymentStatus.ENTERED) {
            throw new RuntimeException("Only ENTERED payments can be deleted");
        }
        // Restore balance — deducted at entry for all payment types.
        restoreBalance(payment.getBill(), payment.getAmount());
        paymentRepository.delete(payment);
    }

    private void applyBalanceDeduction(Bill bill, BigDecimal amount) {
        BigDecimal newBalance = bill.getBalanceRemaining().subtract(amount);
        bill.setBalanceRemaining(newBalance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newBalance);
        bill.setAmountPaid(bill.getAmountPaid().add(amount));
        bill.setFullyPaid(bill.getBalanceRemaining().compareTo(BigDecimal.ZERO) <= 0);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
        syncCompletionStatus(bill);
    }

    private void restoreBalance(Bill bill, BigDecimal amount) {
        BigDecimal newBalance = bill.getBalanceRemaining().add(amount);
        BigDecimal newPaid = bill.getAmountPaid().subtract(amount);
        bill.setBalanceRemaining(newBalance);
        bill.setAmountPaid(newPaid.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newPaid);
        bill.setFullyPaid(false);
        if (bill.getStatus() == BillStatus.COMPLETED
                || bill.getStatus() == BillStatus.AWAITING_CONFIRMATION) {
            bill.setStatus(BillStatus.STORE_RECEIVED);
        }
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    /**
     * A bill is only COMPLETED once it is fully paid AND every payment against it has been
     * confirmed by an admin. Entering the final payment alone is no longer enough — until
     * confirmation the bill sits in AWAITING_CONFIRMATION.
     */
    private void syncCompletionStatus(Bill bill) {
        if (bill.getStatus() == BillStatus.CANCELLED) return;

        boolean wasSettled = bill.getStatus() == BillStatus.COMPLETED
                || bill.getStatus() == BillStatus.AWAITING_CONFIRMATION;

        if (!Boolean.TRUE.equals(bill.getFullyPaid())) {
            if (wasSettled) {
                bill.setStatus(BillStatus.STORE_RECEIVED);
                bill.setUpdatedAt(LocalDateTime.now());
                billRepository.save(bill);
            }
            return;
        }

        boolean allConfirmed = paymentRepository.findByBillId(bill.getId()).stream()
                .filter(p -> p.getStatus() != PaymentStatus.REJECTED
                        && p.getStatus() != PaymentStatus.RETURNED)
                .allMatch(p -> p.getStatus() == PaymentStatus.CONFIRMED);

        BillStatus target = allConfirmed ? BillStatus.COMPLETED : BillStatus.AWAITING_CONFIRMATION;
        if (bill.getStatus() != target) {
            bill.setStatus(target);
            bill.setUpdatedAt(LocalDateTime.now());
            billRepository.save(bill);
        }
    }

    private Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments(PaymentStatus status, LocalDate from, LocalDate to) {
        boolean hasDates = from != null && to != null;
        if (hasDates) {
            if (status != null) {
                return paymentRepository.findByStatusWithBillBetween(status, from, to)
                        .stream()
                        .map(p -> toResponse(p, p.getBill()))
                        .toList();
            }
            return paymentRepository.findAllWithBillBetween(from, to)
                    .stream()
                    .map(p -> toResponse(p, p.getBill()))
                    .toList();
        }
        if (status != null) {
            return paymentRepository.findByStatusWithBill(status)
                    .stream()
                    .map(p -> toResponse(p, p.getBill()))
                    .toList();
        }
        return paymentRepository.findAllWithBill()
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .toList();
    }

    @Transactional
    public PaymentResponse updatePayment(Long paymentId, PaymentRequest request) {
        Payment payment = getPaymentById(paymentId);

        if (payment.getStatus() != PaymentStatus.ENTERED) {
            throw new RuntimeException("Only ENTERED payments can be edited");
        }

        // Adjust balance for amount change — applies to all payment types.
        BigDecimal diff = request.getAmount().subtract(payment.getAmount());
        Bill bill = payment.getBill();
        BigDecimal newBalance = bill.getBalanceRemaining().subtract(diff);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Updated amount exceeds remaining balance");
        }
        bill.setBalanceRemaining(newBalance);
        bill.setAmountPaid(bill.getAmountPaid().add(diff));
        bill.setFullyPaid(newBalance.compareTo(BigDecimal.ZERO) <= 0);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
        syncCompletionStatus(bill);

        payment.setAmount(request.getAmount());
        payment.setPaymentType(request.getPaymentType());
        payment.setPaymentDate(request.getPaymentDate() != null
                ? request.getPaymentDate() : LocalDate.now());
        payment.setReferenceNumber(request.getReferenceNumber());
        payment.setBankName(request.getBankName());
        payment.setBranchName(request.getBranchName());
        payment.setChequeNumber(request.getChequeNumber());
        payment.setChequeDate(request.getChequeDate());
        payment.setNotes(request.getNotes());
        payment.setCollectorNote(request.getCollectorNote());

        if (request.getCollectedByWorkerId() != null) {
            Worker worker = workerRepository.findById(request.getCollectedByWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
            payment.setCollectedByWorker(worker);
        } else {
            payment.setCollectedByWorker(null);
        }

        payment.setUpdatedAt(LocalDateTime.now());

        return toResponse(paymentRepository.save(payment), payment.getBill());
    }


    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyEnteredPayments() {
        User currentUser = getCurrentUser();
        return paymentRepository
                .findByEnteredByIdAndStatusOrderByCreatedAtDesc(
                        currentUser.getId(), PaymentStatus.ENTERED)
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .toList();
    }

    // Bulk / Combined Payment

    @Transactional
    public PaymentGroupResponse enterBulkPayment(BulkPaymentRequest request) {

        if (request.getPaymentType() == PaymentType.CHEQUE) {
            if (request.getChequeNumber() == null ||
                    request.getBankName() == null ||
                    request.getChequeDate() == null) {
                throw new RuntimeException("Cheque details are required for cheque payments");
            }
        }

        User currentUser = getCurrentUser();
        boolean autoConfirm = currentUser.getRole() == UserRole.ADMIN;
        LocalDate paymentDate = request.getPaymentDate() != null
                ? request.getPaymentDate() : LocalDate.now();

        BigDecimal totalAmount = request.getBills().stream()
                .map(BulkPaymentRequest.BillPaymentItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PaymentGroup group = PaymentGroup.builder()
                .paymentType(request.getPaymentType())
                .chequeNumber(request.getChequeNumber())
                .bankName(request.getBankName())
                .branchName(request.getBranchName())
                .referenceNumber(request.getReferenceNumber())
                .chequeDate(request.getChequeDate())
                .paymentDate(paymentDate)
                .totalAmount(totalAmount)
                .notes(request.getNotes())
                .status(autoConfirm ? PaymentStatus.CONFIRMED : PaymentStatus.ENTERED)
                .confirmedBy(autoConfirm ? currentUser : null)
                .confirmedAt(autoConfirm ? LocalDateTime.now() : null)
                .enteredBy(currentUser)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        paymentGroupRepository.save(group);

        List<Payment> payments = request.getBills().stream().map(item -> {
            Bill bill = billRepository.findById(item.getBillId())
                    .orElseThrow(() -> new RuntimeException("Bill not found: " + item.getBillId()));

            if (bill.getFullyPaid()) {
                throw new RuntimeException("Bill " + item.getBillId() + " is already fully paid");
            }
            if (item.getAmount().compareTo(bill.getBalanceRemaining()) > 0) {
                throw new RuntimeException("Amount exceeds balance for bill: " + bill.getBillNumber());
            }

            boolean isPartial = item.getAmount().compareTo(bill.getBalanceRemaining()) < 0;

            return Payment.builder()
                    .bill(bill)
                    .group(group)
                    .amount(item.getAmount())
                    .paymentType(request.getPaymentType())
                    .status(autoConfirm ? PaymentStatus.CONFIRMED : PaymentStatus.ENTERED)
                    .confirmedBy(autoConfirm ? currentUser : null)
                    .confirmedAt(autoConfirm ? LocalDateTime.now() : null)
                    .isPartial(isPartial)
                    .enteredBy(currentUser)
                    .paymentDate(paymentDate)
                    .chequeNumber(request.getChequeNumber())
                    .bankName(request.getBankName())
                    .branchName(request.getBranchName())
                    .referenceNumber(request.getReferenceNumber())
                    .chequeDate(request.getChequeDate())
                    .notes(request.getNotes())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }).toList();

        paymentRepository.saveAll(payments);

        // Deduct balance immediately for each bill in the bulk payment
        for (Payment p : payments) {
            applyBalanceDeduction(p.getBill(), p.getAmount());
        }

        return toGroupResponse(group, payments);
    }

    @Transactional
    public PaymentGroupResponse confirmGroup(Long groupId) {
        PaymentGroup group = paymentGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Payment group not found"));

        if (group.getStatus() == PaymentStatus.CONFIRMED) {
            throw new RuntimeException("Payment group is already confirmed");
        }

        User currentUser = getCurrentUser();
        List<Payment> payments = paymentRepository.findByGroupId(groupId);

        // Balance was already deducted at bulk entry time — confirmation is a status mark only.
        for (Payment payment : payments) {
            payment.setStatus(PaymentStatus.CONFIRMED);
            payment.setConfirmedBy(currentUser);
            payment.setConfirmedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }

        group.setStatus(PaymentStatus.CONFIRMED);
        group.setConfirmedBy(currentUser);
        group.setConfirmedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        paymentGroupRepository.save(group);

        for (Payment payment : payments) {
            syncCompletionStatus(payment.getBill());
        }

        return toGroupResponse(group, payments);
    }

    @Transactional
    public PaymentGroupResponse returnGroup(Long groupId, String returnReason) {
        PaymentGroup group = paymentGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Payment group not found"));

        if (group.getStatus() != PaymentStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed groups can be returned");
        }

        List<Payment> payments = paymentRepository.findByGroupId(groupId);

        for (Payment payment : payments) {
            Bill bill = payment.getBill();

            BigDecimal restored   = bill.getAmountPaid().subtract(payment.getAmount());
            BigDecimal newBalance = bill.getTotalAmount().subtract(restored);

            bill.setAmountPaid(restored);
            bill.setBalanceRemaining(newBalance);
            bill.setFullyPaid(false);
            bill.setStatus(BillStatus.STORE_RECEIVED);
            bill.setUpdatedAt(LocalDateTime.now());
            billRepository.save(bill);

            payment.setStatus(PaymentStatus.RETURNED);
            payment.setReturnReason(returnReason);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }

        group.setStatus(PaymentStatus.RETURNED);
        group.setReturnReason(returnReason);
        group.setUpdatedAt(LocalDateTime.now());
        paymentGroupRepository.save(group);

        return toGroupResponse(group, payments);
    }

    @Transactional(readOnly = true)
    public PaymentGroupResponse getGroupById(Long groupId) {
        PaymentGroup group = paymentGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Payment group not found"));
        List<Payment> payments = paymentRepository.findByGroupId(groupId);
        return toGroupResponse(group, payments);
    }

    @Transactional(readOnly = true)
    public List<PaymentGroupResponse> getAllGroups(PaymentStatus status) {
        List<PaymentGroup> groups = status != null
                ? paymentGroupRepository.findByStatusOrderByCreatedAtDesc(status)
                : paymentGroupRepository.findAllByOrderByCreatedAtDesc();

        if (groups.isEmpty()) return List.of();

        // Batch-load all payments for all groups in a single query to avoid N+1
        List<Long> groupIds = groups.stream().map(PaymentGroup::getId).collect(Collectors.toList());
        Map<Long, List<Payment>> paymentsByGroup = paymentRepository.findByGroupIdIn(groupIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getGroup().getId()));

        return groups.stream()
                .map(group -> toGroupResponse(group, paymentsByGroup.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    private PaymentGroupResponse toGroupResponse(PaymentGroup group, List<Payment> payments) {
        List<PaymentResponse> paymentResponses = payments.stream()
                .map(p -> toResponse(p, p.getBill()))
                .toList();

        return PaymentGroupResponse.builder()
                .id(group.getId())
                .paymentType(group.getPaymentType())
                .chequeNumber(group.getChequeNumber())
                .bankName(group.getBankName())
                .branchName(group.getBranchName())
                .referenceNumber(group.getReferenceNumber())
                .chequeDate(group.getChequeDate())
                .paymentDate(group.getPaymentDate())
                .totalAmount(group.getTotalAmount())
                .notes(group.getNotes())
                .status(group.getStatus())
                .enteredByName(group.getEnteredBy() != null ? group.getEnteredBy().getFullName() : null)
                .confirmedByName(group.getConfirmedBy() != null ? group.getConfirmedBy().getFullName() : null)
                .confirmedAt(group.getConfirmedAt())
                .returnReason(group.getReturnReason())
                .createdAt(group.getCreatedAt())
                .payments(paymentResponses)
                .build();
    }

    // ── Cheque queries ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentResponse> getFutureCheques(String customer) {
        return paymentRepository.findFutureCheques(LocalDate.now(), customer)
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> searchByChequeNumber(String chequeNumber) {
        return paymentRepository.findByChequeNumberContaining(chequeNumber)
                .stream()
                .map(p -> toResponse(p, p.getBill()))
                .collect(Collectors.toList());
    }

    private PaymentResponse toResponse(Payment payment, Bill bill) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .billId(bill.getId())
                .groupId(payment.getGroup() != null ? payment.getGroup().getId() : null)
                .billNumber(bill.getBillNumber())
                .billDate(bill.getBillDate())
                .isPartial(payment.getIsPartial())
                .customerName(bill.getCustomerName())
                .business(bill.getBusiness())
                .billTotal(bill.getTotalAmount())
                .area(bill.getArea())
                .amountPaid(bill.getAmountPaid())
                .balanceRemaining(bill.getBalanceRemaining())
                .fullyPaid(bill.getFullyPaid())
                .paymentAmount(payment.getAmount())
                .paymentType(payment.getPaymentType())
                .status(payment.getStatus())
                .chequeNumber(payment.getChequeNumber())
                .chequeDate(payment.getChequeDate())
                .bankName(payment.getBankName())
                .branchName(payment.getBranchName())
                .referenceNumber(payment.getReferenceNumber())
                .returnReason(payment.getReturnReason())
                .enteredByName(payment.getEnteredBy() != null ? payment.getEnteredBy().getFullName() : null)
                .confirmedByName(payment.getConfirmedBy() != null ? payment.getConfirmedBy().getFullName() : null)
                .confirmedAt(payment.getConfirmedAt())
                .paymentDate(payment.getPaymentDate())
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .collectionNoteId(payment.getCollectionNote() != null ? payment.getCollectionNote().getId() : null)
                .collectedByOwnerName(payment.getCollectionNote() != null ? payment.getCollectionNote().getCollectedBy().getFullName() : null)
                .collectedByOwnerAt(payment.getCollectionNote() != null ? payment.getCollectionNote().getCollectedAt() : null)
                .collectedByWorkerId(payment.getCollectedByWorker() != null ? payment.getCollectedByWorker().getId() : null)
                .collectedByWorkerName(payment.getCollectedByWorker() != null ? payment.getCollectedByWorker().getFullName() : null)
                .collectorNote(payment.getCollectorNote())
                .build();
    }
}
