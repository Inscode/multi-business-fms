package com.multi.finance.service.impl;


import com.multi.finance.service.BillBalance;
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
    private final com.multi.finance.repository.BillReturnRepository billReturnRepository;

    /** Returns nobody has confirmed or reviewed yet; these hold up a payment. */
    private static final List<com.multi.finance.enums.ReturnStatus> UNSETTLED_RETURNS =
            List.of(com.multi.finance.enums.ReturnStatus.PENDING,
                    com.multi.finance.enums.ReturnStatus.GOODS_CONFIRMED);

    /**
     * Refuses a payment while a return on the bill is still unanswered.
     *
     * <p>This is the check that stops returns being missed. A return entered but never
     * confirmed means goods may be sitting with the customer uncredited, or credited
     * for stock that never came back — either way the amount being collected is wrong.
     * Putting the block at payment entry catches it at the one moment somebody is
     * certain to be looking at the bill.
     */
    private void guardOpenReturns(Bill bill) {
        List<com.multi.finance.entity.BillReturn> open =
                billReturnRepository.findByBillIdAndStatusIn(bill.getId(), UNSETTLED_RETURNS);
        if (open.isEmpty()) return;

        boolean awaitingGoods = open.stream()
                .anyMatch(r -> r.getStatus() == com.multi.finance.enums.ReturnStatus.PENDING);
        throw new RuntimeException(awaitingGoods
                ? "This bill has " + open.size() + " return(s) with no goods confirmation. "
                + "Confirm what came back before entering a payment — the amount owed depends on it."
                : "This bill has " + open.size() + " confirmed return(s) still awaiting admin review. "
                + "The payable amount is not final until they are reviewed.");
    }

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

        guardOpenReturns(bill);
        guardSettledElsewhere(bill);

        // A second instrument handed over in the same visit rides on the first
        // photograph: both are written on the same page of the bill under one signature,
        // and a second picture of it would be the same evidence filed twice.
        ReceiptSource inherited = null;
        if (blankToNull(request.getReceiptImageUrl()) == null && receiptRequiredOf(caller)) {
            inherited = findExistingReceipt(bill, caller);
        }
        if (inherited == null) {
            guardReceiptImage(caller, request.getReceiptImageUrl());
        }
        Payment sharedReceipt = inherited == null ? null : inherited.payment();

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
                .receiptImageUrl(inherited != null
                        ? inherited.imageUrl()
                        : blankToNull(request.getReceiptImageUrl()))
                .receiptSharedFrom(sharedReceipt)
                .receiptUploadedAt(blankToNull(request.getReceiptImageUrl()) != null
                        ? LocalDateTime.now() : null)
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
                // The photo follows the money: attached when the collection was marked,
                // it belongs on the payment that collection became, not stranded on the
                // note where nobody reviewing payments would look for it.
                .receiptImageUrl(note.getReceiptImageUrl())
                .receiptUploadedAt(note.getReceiptUploadedAt())
                .createdAt(now)
                .updatedAt(now)
                .build();

        paymentRepository.save(payment);
        applyBalanceDeduction(bill, payment.getAmount());

        return toResponse(payment, bill);
    }

    /**
     * Reverses the payment auto-created from an admin cash collection, so the note can be
     * deleted when it was entered wrongly. Unlike deletePayment this accepts a CONFIRMED
     * payment — self-confirmed collections are always confirmed at creation, and the admin
     * deleting the note is the same authority that confirmed it.
     */
    @Transactional
    public void reversePaymentForCollectionNote(Long collectionNoteId) {
        paymentRepository.findByCollectionNoteId(collectionNoteId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.RETURNED
                    || payment.getStatus() == PaymentStatus.REJECTED) {
                // Balance was already restored when it was returned/rejected
                paymentRepository.delete(payment);
                return;
            }
            Bill bill = payment.getBill();
            restoreBalance(bill, payment.getAmount());
            paymentRepository.delete(payment);
            syncCompletionStatus(bill);
        });
    }

    @Transactional
    public PaymentResponse confirmPayment(Long paymentId) {
        return confirmPayment(paymentId, null);
    }

    /**
     * @param confirmImageUrl the admin's own photograph, optional — kept apart from the
     *                        accountant's so the two records are never conflated
     */
    @Transactional
    public PaymentResponse confirmPayment(Long paymentId, String confirmImageUrl) {
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
        if (blankToNull(confirmImageUrl) != null) {
            payment.setConfirmImageUrl(blankToNull(confirmImageUrl));
            payment.setConfirmUploadedAt(LocalDateTime.now());
        }
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
        BillBalance.reversePayment(bill, payment.getAmount());
        bill.setStatus(BillStatus.STORE_RECEIVED);
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
        BillBalance.applyPayment(bill, amount);
        billRepository.save(bill);
        syncCompletionStatus(bill);
    }

    private void restoreBalance(Bill bill, BigDecimal amount) {
        BillBalance.reversePayment(bill, amount);
        BillBalance.reopenIfClosed(bill, BillStatus.STORE_RECEIVED);
        billRepository.save(bill);
    }

    /**
     * A bill is only COMPLETED once it is fully paid AND every payment against it has been
     * confirmed by an admin. Entering the final payment alone is no longer enough — until
     * confirmation the bill sits in AWAITING_CONFIRMATION.
     */
    /**
     * Requires a photograph of the bill from anyone entering a collection they took.
     *
     * <p>An accountant recording a payment is recording money that already changed
     * hands, out in the field, with nobody else present. The photograph is the only
     * thing tying the figure they typed to the paper the customer signed, so it is not
     * optional for them.
     *
     * <p>An admin is not asked for it: they are usually holding the paperwork, and are
     * the person the evidence would be shown to. They may still attach one.
     */
    private void guardReceiptImage(User caller, String imageUrl) {
        if (blankToNull(imageUrl) != null) return;
        if (!receiptRequiredOf(caller)) return;
        throw new RuntimeException(
                "Attach a photo of the bill before entering this payment — it is the "
              + "record of what was collected.");
    }

    /**
     * Refuses a payment on a bill whose money is collected on another one.
     *
     * <p>Such a bill keeps its balance — the debt is real, it is simply owed on the
     * hand-written bill for the same sale — so nothing about its own figures stops a
     * payment being entered here. Taking the money against this one would settle a
     * balance the reports have already excluded and leave the bill that is actually
     * being chased still showing the full amount.
     */
    private void guardSettledElsewhere(Bill bill) {
        if (bill.getSettledOn() == null) return;
        throw new RuntimeException(
                "Bill " + bill.getBillNumber() + " is collected on "
              + bill.getSettledOn().getBillNumber() + ". Enter the payment against that "
              + "bill instead.");
    }

    private static boolean receiptRequiredOf(User caller) {
        return caller.getRole() == UserRole.ACCOUNTANT
            || caller.getRole() == UserRole.MAIN_ACCOUNTANT
            || caller.getRole() == UserRole.SHOP_ACCOUNTANT;
    }

    /**
     * How long a photograph can cover a second payment on the same bill.
     *
     * <p>Three hours. A customer settling with two cheques does it in one handover, and
     * both go on the same page of the bill under one signature — but the accountant is
     * out on a round, and the gap between writing the first and sitting down to enter
     * the second is measured in interruptions rather than minutes. An hour is tight
     * enough to fail on an ordinary afternoon.
     *
     * <p>It stops well short of a day because a cheque handed over tomorrow is a
     * different event on a different page, and no photograph of today's bill is evidence
     * of it. The same-day rule below closes the rest of the gap.
     */
    private static final int SHARED_RECEIPT_HOURS = 3;

    /**
     * A photograph already taken for this bill that a new payment can rest on.
     *
     * @param imageUrl what to copy onto the payment
     * @param payment  the earlier payment it came from, if that is the source
     * @param reason   what to tell the person entering, in their words not the system's
     */
    private record ReceiptSource(String imageUrl, Payment payment, String reason) {}

    /**
     * Where a payment's photograph can come from other than the person entering it.
     *
     * <p>Two cases, and both are about not asking twice for the same picture:
     *
     * <ul>
     *   <li>The admin or owner already photographed the bill when marking the collection.
     *       That note is sitting there waiting to be entered, so the accountant entering
     *       it is looking at evidence that already exists.
     *   <li>A second instrument handed over in the same visit, covered below.
     * </ul>
     */
    private ReceiptSource findExistingReceipt(Bill bill, User caller) {
        // The collection note first: it is the stronger evidence of the two, taken by
        // whoever actually received the money.
        CollectionNote note = collectionNoteRepository
                .findByBillIdAndStatus(bill.getId(), CollectionNoteStatus.PENDING).stream()
                .filter(n -> n.getReceiptImageUrl() != null && !n.getReceiptImageUrl().isBlank())
                .findFirst()
                .orElse(null);
        if (note != null) {
            return new ReceiptSource(note.getReceiptImageUrl(), null,
                    "Already photographed when this collection was marked.");
        }

        Payment earlier = findShareableReceipt(bill, caller);
        if (earlier != null) {
            return new ReceiptSource(earlier.getReceiptImageUrl(), earlier,
                    "Covered by the photo on the Rs " + earlier.getAmount().toPlainString()
                  + " payment you entered earlier — both are on the same page of the bill.");
        }
        return null;
    }

    /**
     * The photograph a second payment can rest on, if there is one.
     *
     * <p>Deliberately narrow. It must be the same bill, entered by the same person,
     * within {@value #SHARED_RECEIPT_HOURS} hours, on the same calendar day, and the
     * earlier payment must carry a photograph of its own rather than an inherited one.
     * Loosen any of those and the exception stops describing one handover: a different
     * accountant did not see what this one saw, a chained inheritance walks a morning
     * photo into the evening, and a payment either side of midnight is two days' work
     * however few hours separate them.
     */
    private Payment findShareableReceipt(Bill bill, User caller) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(SHARED_RECEIPT_HOURS);
        return paymentRepository
                .findRecentWithReceiptForBill(bill.getId(), caller.getId(), since).stream()
                .filter(p -> p.getCreatedAt() != null)
                .filter(p -> p.getCreatedAt().toLocalDate().equals(now.toLocalDate()))
                .findFirst()
                .orElse(null);
    }

    /**
     * What the entry screen needs to know before it asks for a photograph.
     *
     * <p>Read before the form is filled in, so an accountant entering the second of two
     * cheques is told the first photograph already covers it rather than being stopped
     * at the end for a picture of a page they have put away.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> receiptRequirement(Long billId) {
        User caller = getCurrentUser();
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("required", receiptRequiredOf(caller));
        out.put("windowHours", SHARED_RECEIPT_HOURS);

        ReceiptSource source = receiptRequiredOf(caller) ? findExistingReceipt(bill, caller) : null;
        out.put("canShare", source != null);
        if (source != null) {
            out.put("sharedImageUrl", source.imageUrl());
            out.put("sharedReason", source.reason());
            if (source.payment() != null) {
                out.put("sharedFromPaymentId", source.payment().getId());
                out.put("sharedFromAmount", source.payment().getAmount());
                out.put("sharedFromAt", source.payment().getCreatedAt());
            }
        }
        return out;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Moves the bill in or out of a settled status to match its balance.
     *
     * <p>Public because a return can settle a bill just as a payment can: crediting
     * goods back can clear the last of what was owed, and without this the bill would
     * sit at CREATED with nothing left to collect.
     */
    public void syncCompletionStatus(Bill bill) {
        if (bill.getStatus() == BillStatus.CANCELLED) return;

        boolean wasSettled = bill.getStatus() == BillStatus.COMPLETED
                || bill.getStatus() == BillStatus.AWAITING_CONFIRMATION;

        if (!Boolean.TRUE.equals(bill.getFullyPaid())) {
            if (wasSettled) {
                bill.setStatus(BillStatus.STORE_RECEIVED);
                bill.setUpdatedAt(LocalDateTime.now());
                billRepository.save(bill);
                cascadeToSettledBills(bill, BillStatus.STORE_RECEIVED);
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
        cascadeToSettledBills(bill, target);
    }

    /**
     * Closes the bills whose money is collected on this one.
     *
     * <p>A system bill pointed at a hand-written bill will never be paid on its own —
     * the cash comes in against the other one. Without this it would sit open forever
     * on a debt that was in fact collected.
     */
    private void cascadeToSettledBills(Bill payer, BillStatus target) {
        List<Bill> settled = billRepository.findBySettledOnId(payer.getId());
        for (Bill b : settled) {
            if (b.getStatus() == BillStatus.CANCELLED || b.getStatus() == target) continue;
            b.setStatus(target);
            b.setUpdatedAt(LocalDateTime.now());
            billRepository.save(b);
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
        if (bill.getBalanceRemaining().subtract(diff).compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Updated amount exceeds remaining balance");
        }
        BillBalance.applyPayment(bill, diff);
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
            guardOpenReturns(bill);
            guardSettledElsewhere(bill);
            if (item.getAmount().compareTo(bill.getBalanceRemaining()) > 0) {
                throw new RuntimeException("Amount exceeds balance for bill: " + bill.getBillNumber());
            }

            boolean isPartial = item.getAmount().compareTo(bill.getBalanceRemaining()) < 0;

            // Each bill needs its own photograph: they are separate pieces of paper,
            // signed separately, and one cheque covering three of them does not make one
            // picture evidence for all three.
            ReceiptSource inherited = null;
            if (blankToNull(item.getReceiptImageUrl()) == null && receiptRequiredOf(currentUser)) {
                inherited = findExistingReceipt(bill, currentUser);
                if (inherited == null) {
                    throw new RuntimeException(
                            "Attach a photo of bill " + bill.getBillNumber()
                          + " — each bill in a combined payment needs its own, since each "
                          + "was signed separately.");
                }
            }

            return Payment.builder()
                    .bill(bill)
                    .group(group)
                    .receiptImageUrl(inherited != null
                            ? inherited.imageUrl()
                            : blankToNull(item.getReceiptImageUrl()))
                    .receiptUploadedAt(
                            inherited != null || blankToNull(item.getReceiptImageUrl()) != null
                                    ? LocalDateTime.now() : null)
                    .receiptSharedFrom(inherited == null ? null : inherited.payment())
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

            BillBalance.reversePayment(bill, payment.getAmount());
            bill.setStatus(BillStatus.STORE_RECEIVED);
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
                // Both roles see both images: the accountant's evidence and the admin's
                // check are only useful if each can see what the other recorded.
                .receiptImageUrl(payment.getReceiptImageUrl())
                .receiptSharedFromPaymentId(payment.getReceiptSharedFrom() != null
                        ? payment.getReceiptSharedFrom().getId() : null)
                .receiptUploadedAt(payment.getReceiptUploadedAt())
                .confirmImageUrl(payment.getConfirmImageUrl())
                .confirmUploadedAt(payment.getConfirmUploadedAt())
                .build();
    }
}
