package com.multi.finance.service.impl;

import com.multi.finance.dto.request.WorkerTabPurchaseRequest;
import com.multi.finance.dto.response.WorkerSalaryDeductionResponse;
import com.multi.finance.dto.response.WorkerTabPurchaseResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.WorkerFinanceStatus;
import com.multi.finance.repository.*;
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
public class WorkerTabPurchaseServiceImpl {

    private final WorkerTabPurchaseRepository repository;
    private final WorkerTabPurchaseItemRepository itemRepository;
    private final WorkerSalaryDeductionRepository salaryDeductionRepository;
    private final SalaryRecipientRepository recipientRepository;

    @Transactional
    public WorkerTabPurchaseResponse create(WorkerTabPurchaseRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("At least one item is required");
        }
        SalaryRecipient recipient = recipientRepository.findById(req.getRecipientId())
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        String month = req.getBillDate().toString().substring(0, 7);

        BigDecimal total = req.getItems().stream()
                .map(i -> i.getQuantity().multiply(i.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // First item used as legacy header values to satisfy any existing NOT NULL DB constraints.
        // Actual item details are stored in worker_tab_purchase_items.
        WorkerTabPurchaseRequest.WorkerTabPurchaseItemRequest firstItem = req.getItems().get(0);

        WorkerTabPurchase purchase = WorkerTabPurchase.builder()
                .recipient(recipient)
                .purchaseDate(req.getBillDate())
                .month(month)
                .totalAmount(total)
                .description(firstItem.getDescription())
                .quantity(firstItem.getQuantity())
                .unitPrice(firstItem.getUnitPrice())
                .status(WorkerFinanceStatus.PENDING_OWNER)
                .notes(req.getNotes())
                .enteredBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build();

        WorkerTabPurchase saved = repository.save(purchase);

        List<WorkerTabPurchaseItem> items = req.getItems().stream()
                .map(i -> WorkerTabPurchaseItem.builder()
                        .purchase(saved)
                        .description(i.getDescription())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .lineTotal(i.getQuantity().multiply(i.getUnitPrice()))
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();
        itemRepository.saveAll(items);
        saved.setItems(items);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkerTabPurchaseResponse> getAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerTabPurchaseResponse> getPendingOwner() {
        return repository.findByStatusOrderByCreatedAtDesc(WorkerFinanceStatus.PENDING_OWNER)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerTabPurchaseResponse> getPendingAdmin() {
        return repository.findByStatusOrderByCreatedAtDesc(WorkerFinanceStatus.OWNER_APPROVED)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerTabPurchaseResponse> getForRecipient(Long recipientId) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerTabPurchaseResponse> getPaidForMonth(Long recipientId, String month) {
        return repository.findByRecipientIdAndMonthOrderByCreatedAtDesc(recipientId, month)
                .stream()
                .filter(p -> p.getStatus() == WorkerFinanceStatus.PAID)
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerTabPurchaseResponse> getPaidByDateRange(LocalDate from, LocalDate to) {
        return repository.findByStatusAndPurchaseDateBetweenOrderByPurchaseDateDesc(
                WorkerFinanceStatus.PAID, from, to)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkerTabPurchaseResponse ownerApprove(Long id) {
        WorkerTabPurchase purchase = findById(id);
        if (purchase.getStatus() != WorkerFinanceStatus.PENDING_OWNER) {
            throw new RuntimeException("Purchase is not pending owner approval");
        }
        purchase.setStatus(WorkerFinanceStatus.OWNER_APPROVED);
        purchase.setOwnerReviewedBy(getCurrentUser());
        purchase.setOwnerReviewedAt(LocalDateTime.now());
        return toResponse(repository.save(purchase));
    }

    @Transactional
    public WorkerTabPurchaseResponse ownerReject(Long id, String reason) {
        WorkerTabPurchase purchase = findById(id);
        if (purchase.getStatus() != WorkerFinanceStatus.PENDING_OWNER) {
            throw new RuntimeException("Purchase is not pending owner approval");
        }
        purchase.setStatus(WorkerFinanceStatus.REJECTED);
        purchase.setRejectionReason(reason);
        purchase.setOwnerReviewedBy(getCurrentUser());
        purchase.setOwnerReviewedAt(LocalDateTime.now());
        return toResponse(repository.save(purchase));
    }

    @Transactional
    public WorkerTabPurchaseResponse adminConfirm(Long id) {
        WorkerTabPurchase purchase = findById(id);
        if (purchase.getStatus() != WorkerFinanceStatus.OWNER_APPROVED) {
            throw new RuntimeException("Purchase is not owner-approved");
        }
        purchase.setStatus(WorkerFinanceStatus.PAID);
        purchase.setAdminConfirmedBy(getCurrentUser());
        purchase.setAdminConfirmedAt(LocalDateTime.now());
        WorkerTabPurchase saved = repository.save(purchase);

        // Create formal salary deduction record
        if (!salaryDeductionRepository.existsByDeductionTypeAndReferenceId("TAB_PURCHASE", saved.getId())) {
            salaryDeductionRepository.save(WorkerSalaryDeduction.builder()
                    .recipient(saved.getRecipient())
                    .deductionMonth(saved.getMonth())
                    .amount(saved.getTotalAmount())
                    .deductionType("TAB_PURCHASE")
                    .referenceId(saved.getId())
                    .description("Tab purchase bill — " + saved.getTotalAmount() + " (" + saved.getMonth() + ")")
                    .createdBy(getCurrentUser())
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkerSalaryDeductionResponse> getSalaryDeductions(Long recipientId, String month) {
        return salaryDeductionRepository
                .findByRecipientIdAndDeductionMonthOrderByCreatedAtAsc(recipientId, month)
                .stream().map(this::toDeductionResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getPendingOwnerCount() {
        return repository.countByStatus(WorkerFinanceStatus.PENDING_OWNER);
    }

    @Transactional(readOnly = true)
    public long getPendingAdminCount() {
        return repository.countByStatus(WorkerFinanceStatus.OWNER_APPROVED);
    }

    private WorkerTabPurchase findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tab purchase not found"));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private WorkerTabPurchaseResponse toResponse(WorkerTabPurchase p) {
        List<WorkerTabPurchaseItem> items = p.getItems() != null ? p.getItems()
                : itemRepository.findByPurchaseIdOrderByIdAsc(p.getId());

        return WorkerTabPurchaseResponse.builder()
                .id(p.getId())
                .recipientId(p.getRecipient().getId())
                .recipientName(p.getRecipient().getName())
                .billDate(p.getPurchaseDate())
                .month(p.getMonth())
                .totalAmount(p.getTotalAmount())
                .status(p.getStatus())
                .notes(p.getNotes())
                .items(items.stream().map(i -> WorkerTabPurchaseResponse.ItemResponse.builder()
                        .id(i.getId())
                        .description(i.getDescription())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .lineTotal(i.getLineTotal())
                        .build()).toList())
                .enteredByName(p.getEnteredBy().getFullName())
                .ownerReviewedByName(p.getOwnerReviewedBy() != null ? p.getOwnerReviewedBy().getFullName() : null)
                .ownerReviewedAt(p.getOwnerReviewedAt())
                .rejectionReason(p.getRejectionReason())
                .adminConfirmedByName(p.getAdminConfirmedBy() != null ? p.getAdminConfirmedBy().getFullName() : null)
                .adminConfirmedAt(p.getAdminConfirmedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private WorkerSalaryDeductionResponse toDeductionResponse(WorkerSalaryDeduction d) {
        return WorkerSalaryDeductionResponse.builder()
                .id(d.getId())
                .recipientId(d.getRecipient().getId())
                .recipientName(d.getRecipient().getName())
                .deductionMonth(d.getDeductionMonth())
                .amount(d.getAmount())
                .deductionType(d.getDeductionType())
                .referenceId(d.getReferenceId())
                .description(d.getDescription())
                .createdByName(d.getCreatedBy().getFullName())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
