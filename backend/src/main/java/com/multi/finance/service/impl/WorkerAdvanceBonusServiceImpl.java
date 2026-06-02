package com.multi.finance.service.impl;

import com.multi.finance.dto.request.WorkerAdvanceBonusRequest;
import com.multi.finance.dto.response.WorkerAdvanceBonusResponse;
import com.multi.finance.entity.PettyCashDeduction;
import com.multi.finance.entity.SalaryRecipient;
import com.multi.finance.entity.User;
import com.multi.finance.entity.WorkerAdvanceBonus;
import com.multi.finance.entity.WorkerSalaryDeduction;
import com.multi.finance.enums.AdvanceBonusType;
import com.multi.finance.enums.WorkerFinanceStatus;
import com.multi.finance.repository.PettyCashDeductionRepository;
import com.multi.finance.repository.SalaryRecipientRepository;
import com.multi.finance.repository.WorkerAdvanceBonusRepository;
import com.multi.finance.repository.WorkerSalaryDeductionRepository;
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
public class WorkerAdvanceBonusServiceImpl {

    private final WorkerAdvanceBonusRepository repository;
    private final SalaryRecipientRepository recipientRepository;
    private final PettyCashDeductionRepository pettyCashDeductionRepository;
    private final WorkerSalaryDeductionRepository salaryDeductionRepository;

    @Transactional
    public WorkerAdvanceBonusResponse create(WorkerAdvanceBonusRequest req) {
        SalaryRecipient recipient = recipientRepository.findById(req.getRecipientId())
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        WorkerAdvanceBonus entry = WorkerAdvanceBonus.builder()
                .recipient(recipient)
                .type(req.getType())
                .amount(req.getAmount())
                .reason(req.getReason())
                .month(req.getMonth())
                .status(WorkerFinanceStatus.PENDING_OWNER)
                .recoveredAmount(BigDecimal.ZERO)
                .fullyRecovered(false)
                .notes(req.getNotes())
                .enteredBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(repository.save(entry));
    }

    @Transactional(readOnly = true)
    public List<WorkerAdvanceBonusResponse> getAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerAdvanceBonusResponse> getPendingOwner() {
        return repository.findByStatusOrderByCreatedAtDesc(WorkerFinanceStatus.PENDING_OWNER)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerAdvanceBonusResponse> getPendingAdmin() {
        return repository.findByStatusOrderByCreatedAtDesc(WorkerFinanceStatus.OWNER_APPROVED)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerAdvanceBonusResponse> getForRecipient(Long recipientId) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerAdvanceBonusResponse> getOutstandingAdvances(Long recipientId) {
        return repository.findByRecipientIdAndTypeAndFullyRecoveredFalseAndStatus(
                        recipientId, AdvanceBonusType.ADVANCE, WorkerFinanceStatus.PAID)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkerAdvanceBonusResponse ownerApprove(Long id) {
        WorkerAdvanceBonus entry = findById(id);
        if (entry.getStatus() != WorkerFinanceStatus.PENDING_OWNER) {
            throw new RuntimeException("Entry is not pending owner approval");
        }
        entry.setStatus(WorkerFinanceStatus.OWNER_APPROVED);
        entry.setOwnerReviewedBy(getCurrentUser());
        entry.setOwnerReviewedAt(LocalDateTime.now());
        return toResponse(repository.save(entry));
    }

    @Transactional
    public WorkerAdvanceBonusResponse ownerReject(Long id, String reason) {
        WorkerAdvanceBonus entry = findById(id);
        if (entry.getStatus() != WorkerFinanceStatus.PENDING_OWNER) {
            throw new RuntimeException("Entry is not pending owner approval");
        }
        entry.setStatus(WorkerFinanceStatus.REJECTED);
        entry.setRejectionReason(reason);
        entry.setOwnerReviewedBy(getCurrentUser());
        entry.setOwnerReviewedAt(LocalDateTime.now());
        return toResponse(repository.save(entry));
    }

    @Transactional
    public WorkerAdvanceBonusResponse adminConfirm(Long id) {
        WorkerAdvanceBonus entry = findById(id);
        if (entry.getStatus() != WorkerFinanceStatus.OWNER_APPROVED) {
            throw new RuntimeException("Entry is not owner-approved");
        }
        entry.setStatus(WorkerFinanceStatus.PAID);
        entry.setPaymentDate(java.time.LocalDate.now());
        entry.setAdminConfirmedBy(getCurrentUser());
        entry.setAdminConfirmedAt(LocalDateTime.now());
        WorkerAdvanceBonus saved = repository.save(entry);

        // Deduct full amount from petty cash for both ADVANCE and BONUS
        String refType = saved.getType() == AdvanceBonusType.ADVANCE ? "ADVANCE" : "BONUS";
        String desc = saved.getType() + " - " + saved.getRecipient().getName()
                + " (" + saved.getReason() + ", " + saved.getMonth() + ")";
        if (!pettyCashDeductionRepository.existsByReferenceTypeAndReferenceId(refType, saved.getId())) {
            pettyCashDeductionRepository.save(PettyCashDeduction.builder()
                    .referenceType(refType)
                    .referenceId(saved.getId())
                    .recipientName(saved.getRecipient().getName())
                    .amount(saved.getAmount())
                    .description(desc)
                    .deductedBy(getCurrentUser())
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        return toResponse(saved);
    }

    @Transactional
    public WorkerAdvanceBonusResponse recoverAdvance(Long id, BigDecimal amount, String recoveryMonth) {
        WorkerAdvanceBonus entry = findById(id);
        if (entry.getType() != AdvanceBonusType.ADVANCE || entry.getStatus() != WorkerFinanceStatus.PAID) {
            throw new RuntimeException("Can only recover PAID advances");
        }
        BigDecimal outstanding = entry.getAmount().subtract(entry.getRecoveredAmount());
        if (amount.compareTo(outstanding) > 0) {
            throw new RuntimeException("Recovery amount exceeds outstanding balance");
        }
        BigDecimal newRecovered = entry.getRecoveredAmount().add(amount);
        entry.setRecoveredAmount(newRecovered);
        entry.setFullyRecovered(newRecovered.compareTo(entry.getAmount()) >= 0);
        WorkerAdvanceBonus saved = repository.save(entry);

        // Create formal salary deduction for the recovery month
        String month = recoveryMonth != null ? recoveryMonth : entry.getMonth();
        salaryDeductionRepository.save(WorkerSalaryDeduction.builder()
                .recipient(entry.getRecipient())
                .deductionMonth(month)
                .amount(amount)
                .deductionType("ADVANCE_RECOVERY")
                .referenceId(entry.getId())
                .description("Advance recovery — " + entry.getReason() + " (" + month + ")")
                .createdBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkerAdvanceBonusResponse> getPaidByDateRange(LocalDate from, LocalDate to) {
        return repository.findByStatusAndPaymentDateBetweenOrderByPaymentDateDesc(
                WorkerFinanceStatus.PAID, from, to)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getPendingOwnerCount() {
        return repository.countByStatus(WorkerFinanceStatus.PENDING_OWNER);
    }

    @Transactional(readOnly = true)
    public long getPendingAdminCount() {
        return repository.countByStatus(WorkerFinanceStatus.OWNER_APPROVED);
    }

    private WorkerAdvanceBonus findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Advance/Bonus not found"));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private WorkerAdvanceBonusResponse toResponse(WorkerAdvanceBonus e) {
        return WorkerAdvanceBonusResponse.builder()
                .id(e.getId())
                .recipientId(e.getRecipient().getId())
                .recipientName(e.getRecipient().getName())
                .type(e.getType())
                .amount(e.getAmount())
                .reason(e.getReason())
                .month(e.getMonth())
                .paymentDate(e.getPaymentDate())
                .status(e.getStatus())
                .recoveredAmount(e.getRecoveredAmount())
                .fullyRecovered(e.getFullyRecovered())
                .notes(e.getNotes())
                .enteredByName(e.getEnteredBy().getFullName())
                .ownerReviewedByName(e.getOwnerReviewedBy() != null ? e.getOwnerReviewedBy().getFullName() : null)
                .ownerReviewedAt(e.getOwnerReviewedAt())
                .rejectionReason(e.getRejectionReason())
                .adminConfirmedByName(e.getAdminConfirmedBy() != null ? e.getAdminConfirmedBy().getFullName() : null)
                .adminConfirmedAt(e.getAdminConfirmedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
