package com.multi.finance.service.impl;

import com.multi.finance.dto.request.SalaryPaymentRequest;
import com.multi.finance.dto.request.SalaryRecipientRequest;
import com.multi.finance.dto.response.SalaryPaymentResponse;
import com.multi.finance.dto.response.SalaryRecipientResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.SalaryPaymentStatus;
import com.multi.finance.repository.*;
import com.multi.finance.repository.PettyCashDeductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalaryServiceImpl {

    private static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("5000");

    private final SalaryRecipientRepository recipientRepository;
    private final SalaryPaymentRepository paymentRepository;
    private final WorkerRepository workerRepository;
    private final UserRepository userRepository;
    private final PettyCashDeductionRepository pettyCashDeductionRepository;

    @Transactional
    public SalaryRecipientResponse createRecipient(SalaryRecipientRequest req) {
        Worker worker = req.getLinkedWorkerId() != null
                ? workerRepository.findById(req.getLinkedWorkerId()).orElse(null) : null;
        User user = req.getLinkedUserId() != null
                ? userRepository.findById(req.getLinkedUserId()).orElse(null) : null;

        SalaryRecipient recipient = SalaryRecipient.builder()
                .name(req.getName())
                .designation(req.getDesignation())
                .monthlySalary(req.getMonthlySalary())
                .linkedWorker(worker)
                .linkedUser(user)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        return toRecipientResponse(recipientRepository.save(recipient));
    }

    @Transactional
    public SalaryRecipientResponse updateRecipient(Long id, SalaryRecipientRequest req) {
        SalaryRecipient recipient = recipientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));
        if (req.getName() != null) recipient.setName(req.getName());
        if (req.getDesignation() != null) recipient.setDesignation(req.getDesignation());
        if (req.getMonthlySalary() != null) recipient.setMonthlySalary(req.getMonthlySalary());
        return toRecipientResponse(recipientRepository.save(recipient));
    }

    @Transactional
    public void setRecipientActive(Long id, boolean active) {
        SalaryRecipient recipient = recipientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recipient not found"));
        recipient.setActive(active);
        recipientRepository.save(recipient);
    }

    @Transactional(readOnly = true)
    public List<SalaryRecipientResponse> getActiveRecipients() {
        return recipientRepository.findByActiveTrueOrderByDesignationAscNameAsc()
                .stream().map(this::toRecipientResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SalaryRecipientResponse> getAllRecipients() {
        return recipientRepository.findAllByOrderByDesignationAscNameAsc()
                .stream().map(this::toRecipientResponse).toList();
    }

    @Transactional
    public SalaryPaymentResponse createPayment(SalaryPaymentRequest req) {
        SalaryRecipient recipient = recipientRepository.findById(req.getRecipientId())
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        BigDecimal alreadyPaid = paymentRepository.sumPaidForMonth(recipient.getId(), req.getMonth());
        BigDecimal totalAfter  = alreadyPaid.add(req.getAmount());
        boolean overSalary     = totalAfter.compareTo(recipient.getMonthlySalary()) > 0;

        SalaryPaymentStatus status = req.getAmount().compareTo(APPROVAL_THRESHOLD) > 0
                ? SalaryPaymentStatus.PENDING_APPROVAL
                : SalaryPaymentStatus.RECORDED;

        SalaryPayment payment = SalaryPayment.builder()
                .recipient(recipient)
                .month(req.getMonth())
                .amount(req.getAmount())
                .paymentDate(req.getPaymentDate())
                .notes(req.getNotes())
                .status(status)
                .overSalary(overSalary)
                .enteredBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build();

        SalaryPayment saved = paymentRepository.save(payment);
        // Deduct from petty cash immediately for RECORDED payments (< threshold, no approval needed)
        if (saved.getStatus() == SalaryPaymentStatus.RECORDED) {
            createPettyCashDeduction("SALARY", saved.getId(), recipient.getName(),
                    saved.getAmount(), "Salary - " + recipient.getName() + " (" + req.getMonth() + ")");
        }
        return toPaymentResponse(saved, alreadyPaid, recipient.getMonthlySalary());
    }

    @Transactional(readOnly = true)
    public List<SalaryPaymentResponse> getAllPayments(String month) {
        List<SalaryPayment> payments = month != null
                ? paymentRepository.findByMonthOrderByCreatedAtDesc(month)
                : paymentRepository.findAllByOrderByCreatedAtDesc();

        List<SalaryPayment> ascending = payments.stream()
                .sorted(Comparator.comparing(SalaryPayment::getCreatedAt))
                .toList();

        Map<String, BigDecimal> runningTotals = new HashMap<>();
        Map<Long, BigDecimal> paidBeforeMap = new LinkedHashMap<>();

        for (SalaryPayment p : ascending) {
            String key = p.getRecipient().getId() + ":" + p.getMonth();
            BigDecimal before = runningTotals.getOrDefault(key, BigDecimal.ZERO);
            paidBeforeMap.put(p.getId(), before);
            if (isCountable(p.getStatus())) {
                runningTotals.put(key, before.add(p.getAmount()));
            }
        }

        return payments.stream().map(p ->
                toPaymentResponse(p, paidBeforeMap.getOrDefault(p.getId(), BigDecimal.ZERO),
                        p.getRecipient().getMonthlySalary())
        ).toList();
    }

    @Transactional(readOnly = true)
    public List<SalaryPaymentResponse> getMyPayments() {
        User me = getCurrentUser();
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(p -> p.getEnteredBy().getId().equals(me.getId()))
                .map(p -> toPaymentResponseRestricted(p))
                .toList();
    }

    @Transactional
    public SalaryPaymentResponse approve(Long id) {
        SalaryPayment payment = findPaymentById(id);
        if (payment.getStatus() != SalaryPaymentStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Payment is not pending approval");
        }
        BigDecimal alreadyPaid = paymentRepository.sumPaidForMonth(
                payment.getRecipient().getId(), payment.getMonth());
        BigDecimal totalAfter  = alreadyPaid.add(payment.getAmount());
        payment.setOverSalary(totalAfter.compareTo(payment.getRecipient().getMonthlySalary()) > 0);
        payment.setStatus(SalaryPaymentStatus.APPROVED);
        payment.setReviewedBy(getCurrentUser());
        payment.setReviewedAt(LocalDateTime.now());
        SalaryPayment approved = paymentRepository.save(payment);
        // Deduct from petty cash when approval granted (was PENDING_APPROVAL)
        createPettyCashDeduction("SALARY", approved.getId(), approved.getRecipient().getName(),
                approved.getAmount(), "Salary - " + approved.getRecipient().getName() + " (" + approved.getMonth() + ")");
        return toPaymentResponse(approved, alreadyPaid, payment.getRecipient().getMonthlySalary());
    }

    @Transactional
    public SalaryPaymentResponse reject(Long id, String reason) {
        SalaryPayment payment = findPaymentById(id);
        if (payment.getStatus() != SalaryPaymentStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Payment is not pending approval");
        }
        payment.setStatus(SalaryPaymentStatus.REJECTED);
        payment.setRejectionReason(reason);
        payment.setReviewedBy(getCurrentUser());
        payment.setReviewedAt(LocalDateTime.now());
        BigDecimal paid = paymentRepository.sumPaidForMonth(payment.getRecipient().getId(), payment.getMonth());
        return toPaymentResponse(paymentRepository.save(payment), paid, payment.getRecipient().getMonthlySalary());
    }

    @Transactional(readOnly = true)
    public List<SalaryPaymentResponse> getPaymentsByDateRange(LocalDate from, LocalDate to) {
        List<SalaryPayment> payments = paymentRepository.findByPaymentDateBetweenOrderByPaymentDateDesc(from, to);

        List<SalaryPayment> ascending = payments.stream()
                .sorted(Comparator.comparing(SalaryPayment::getCreatedAt))
                .toList();

        Map<String, BigDecimal> runningTotals = new HashMap<>();
        Map<Long, BigDecimal> paidBeforeMap = new LinkedHashMap<>();

        for (SalaryPayment p : ascending) {
            String key = p.getRecipient().getId() + ":" + p.getMonth();
            BigDecimal before = runningTotals.getOrDefault(key, BigDecimal.ZERO);
            paidBeforeMap.put(p.getId(), before);
            if (isCountable(p.getStatus())) {
                runningTotals.put(key, before.add(p.getAmount()));
            }
        }

        return payments.stream().map(p ->
                toPaymentResponse(p, paidBeforeMap.getOrDefault(p.getId(), BigDecimal.ZERO),
                        p.getRecipient().getMonthlySalary())
        ).toList();
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return paymentRepository.countByStatus(SalaryPaymentStatus.PENDING_APPROVAL);
    }

    private void createPettyCashDeduction(String refType, Long refId, String recipientName,
                                          java.math.BigDecimal amount, String description) {
        if (pettyCashDeductionRepository.existsByReferenceTypeAndReferenceId(refType, refId)) return;
        pettyCashDeductionRepository.save(PettyCashDeduction.builder()
                .referenceType(refType)
                .referenceId(refId)
                .recipientName(recipientName)
                .amount(amount)
                .description(description)
                .deductedBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private SalaryPayment findPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Salary payment not found"));
    }

    private boolean isCountable(SalaryPaymentStatus status) {
        return status == SalaryPaymentStatus.RECORDED || status == SalaryPaymentStatus.APPROVED;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private SalaryRecipientResponse toRecipientResponse(SalaryRecipient r) {
        return SalaryRecipientResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .designation(r.getDesignation())
                .monthlySalary(r.getMonthlySalary())
                .active(r.getActive())
                .build();
    }

    private SalaryPaymentResponse toPaymentResponse(SalaryPayment p, BigDecimal alreadyPaid, BigDecimal monthlySalary) {
        BigDecimal totalPaid = alreadyPaid.add(isCountable(p.getStatus()) ? p.getAmount() : BigDecimal.ZERO);
        BigDecimal remaining = monthlySalary.subtract(totalPaid);
        return SalaryPaymentResponse.builder()
                .id(p.getId())
                .recipientId(p.getRecipient().getId())
                .recipientName(p.getRecipient().getName())
                .designation(p.getRecipient().getDesignation())
                .month(p.getMonth())
                .amount(p.getAmount())
                .paymentDate(p.getPaymentDate())
                .notes(p.getNotes())
                .status(p.getStatus())
                .overSalary(p.getOverSalary())
                .monthlySalary(monthlySalary)
                .alreadyPaid(alreadyPaid)
                .remainingAfter(remaining)
                .enteredByName(p.getEnteredBy().getFullName())
                .reviewedByName(p.getReviewedBy() != null ? p.getReviewedBy().getFullName() : null)
                .reviewedAt(p.getReviewedAt())
                .rejectionReason(p.getRejectionReason())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private SalaryPaymentResponse toPaymentResponseRestricted(SalaryPayment p) {
        return SalaryPaymentResponse.builder()
                .id(p.getId())
                .recipientId(p.getRecipient().getId())
                .recipientName(p.getRecipient().getName())
                .designation(p.getRecipient().getDesignation())
                .month(p.getMonth())
                .amount(p.getAmount())
                .paymentDate(p.getPaymentDate())
                .notes(p.getNotes())
                .status(p.getStatus())
                .enteredByName(p.getEnteredBy().getFullName())
                .reviewedByName(p.getReviewedBy() != null ? p.getReviewedBy().getFullName() : null)
                .reviewedAt(p.getReviewedAt())
                .rejectionReason(p.getRejectionReason())
                .createdAt(p.getCreatedAt())
                .build();
    }
}