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
import com.multi.finance.repository.CollectionNoteRepository;
import com.multi.finance.repository.PaymentGroupRepository;
import com.multi.finance.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final PaymentGroupRepository paymentGroupRepository;
    private final CollectionNoteRepository collectionNoteRepository;

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

        if (request.getAmount()
                .compareTo(bill.getBalanceRemaining()) > 0) {

            throw new RuntimeException(
                    "Payment amount exceeds remaining balance");
        }

        /*
         * Validate cheque details
         */
        if (request.getPaymentType() == PaymentType.CHEQUE) {

            if (request.getChequeNumber() == null ||
                    request.getBankName() == null ||
                    request.getChequeDate() == null) {

                throw new RuntimeException(
                        "Cheque details are required");
            }
        }

        boolean isPartial = request.getAmount()
                .compareTo(bill.getBalanceRemaining()) < 0;

        // Link to owner collection note if provided
        CollectionNote collectionNote = null;
        if (request.getCollectionNoteId() != null) {
            collectionNote = collectionNoteRepository.findById(request.getCollectionNoteId())
                    .orElseThrow(() -> new RuntimeException("Collection note not found"));
            if (collectionNote.getStatus() == CollectionNoteStatus.MATCHED) {
                throw new RuntimeException("This collection note is already matched to a payment");
            }
        }

        Payment payment = Payment.builder()
                .bill(bill)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .status(PaymentStatus.ENTERED)
                .isPartial(isPartial)
                .enteredBy(getCurrentUser())
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);

        // Mark the collection note as matched
        if (collectionNote != null) {
            collectionNote.setStatus(CollectionNoteStatus.MATCHED);
            collectionNoteRepository.save(collectionNote);
        }

        return toResponse(payment, bill);
    }
    @Transactional
    public PaymentResponse confirmPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getStatus().equals(PaymentStatus.CONFIRMED)) {
            throw new RuntimeException("Payment is already confirmed");
        }

        Bill bill = payment.getBill();

        // Update payment
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setConfirmedBy(getCurrentUser());
        payment.setConfirmedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        /*
         * NOW update official bill balances
         */
        BigDecimal newAmountPaid =
                bill.getAmountPaid().add(payment.getAmount());

        BigDecimal newBalance =
                bill.getTotalAmount().subtract(newAmountPaid);

        bill.setAmountPaid(newAmountPaid);

        bill.setBalanceRemaining(newBalance);

        bill.setFullyPaid(
                newBalance.compareTo(BigDecimal.ZERO) <= 0
        );

        if (Boolean.TRUE.equals(bill.getFullyPaid())) {
            bill.setStatus(BillStatus.COMPLETED);
        }

        bill.setUpdatedAt(LocalDateTime.now());

        billRepository.save(bill);

        return toResponse(payment, bill);
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

    private Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments(PaymentStatus status) {
        if (status != null) {
            return paymentRepository.findByStatus(status)
                    .stream()
                    .map(p -> toResponse(p, p.getBill()))
                    .toList();
        }
        return paymentRepository.findAll()
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
                .status(PaymentStatus.ENTERED)
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
                throw new RuntimeException("Amount exceeds balance for bill: " + item.getBillId());
            }

            boolean isPartial = item.getAmount().compareTo(bill.getBalanceRemaining()) < 0;

            return Payment.builder()
                    .bill(bill)
                    .group(group)
                    .amount(item.getAmount())
                    .paymentType(request.getPaymentType())
                    .status(PaymentStatus.ENTERED)
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

        for (Payment payment : payments) {
            Bill bill = payment.getBill();

            BigDecimal newAmountPaid = bill.getAmountPaid().add(payment.getAmount());
            BigDecimal newBalance    = bill.getTotalAmount().subtract(newAmountPaid);

            bill.setAmountPaid(newAmountPaid);
            bill.setBalanceRemaining(newBalance);
            bill.setFullyPaid(newBalance.compareTo(BigDecimal.ZERO) <= 0);

            if (Boolean.TRUE.equals(bill.getFullyPaid())) {
                bill.setStatus(BillStatus.COMPLETED);
            }
            bill.setUpdatedAt(LocalDateTime.now());
            billRepository.save(bill);

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

        return groups.stream().map(group -> {
            List<Payment> payments = paymentRepository.findByGroupId(group.getId());
            return toGroupResponse(group, payments);
        }).toList();
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

    private PaymentResponse toResponse(Payment payment, Bill bill) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .billId(bill.getId())
                .groupId(payment.getGroup() != null ? payment.getGroup().getId() : null)
                .billNumber(bill.getBillNumber())
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
                .build();
    }
}
