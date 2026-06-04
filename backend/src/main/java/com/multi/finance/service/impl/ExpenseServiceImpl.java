package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ExpenseRequest;
import com.multi.finance.dto.response.ExpenseResponse;
import com.multi.finance.entity.Expense;
import com.multi.finance.entity.User;
import com.multi.finance.entity.Worker;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.ExpenseCategory;
import com.multi.finance.enums.ExpenseStatus;
import com.multi.finance.repository.ExpenseRepository;
import com.multi.finance.repository.WorkerRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl {

    private static final Set<ExpenseCategory> FLAGGED_CATEGORIES =
            Set.of(ExpenseCategory.REPAIR, ExpenseCategory.OTHER);

    private final ExpenseRepository expenseRepository;
    private final WorkerRepository workerRepository;

    @Transactional
    public ExpenseResponse create(ExpenseRequest req) {
        Worker worker = null;
        if (req.getWorkerId() != null) {
            worker = workerRepository.findById(req.getWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
        }

        boolean needsReview = FLAGGED_CATEGORIES.contains(req.getCategory())
                || (req.getCategory() == ExpenseCategory.DRIVER_SALARY
                    && req.getAmount().compareTo(new java.math.BigDecimal("3000")) > 0);
        ExpenseStatus status = needsReview ? ExpenseStatus.PENDING_REVIEW : ExpenseStatus.RECORDED;

        Expense expense = Expense.builder()
                .business(req.getBusiness())
                .category(req.getCategory())
                .amount(req.getAmount())
                .date(req.getDate())
                .description(req.getDescription())
                .worker(worker)
                .status(status)
                .enteredBy(getCurrentUser())
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(expenseRepository.save(expense));
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getAll(BusinessType business, ExpenseCategory category,
                                        ExpenseStatus status, LocalDate from, LocalDate to) {
        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (business != null) predicates.add(cb.equal(root.get("business"), business));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            if (status  != null) predicates.add(cb.equal(root.get("status"),   status));
            if (from    != null) predicates.add(cb.greaterThanOrEqualTo(root.get("date"), from));
            if (to      != null) predicates.add(cb.lessThanOrEqualTo(root.get("date"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort sort = Sort.by(Sort.Direction.DESC, "date").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        return expenseRepository.findAll(spec, sort).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExpenseResponse review(Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        if (expense.getStatus() != ExpenseStatus.PENDING_REVIEW) {
            throw new RuntimeException("Expense is not pending review");
        }
        expense.setStatus(ExpenseStatus.REVIEWED);
        expense.setReviewedBy(getCurrentUser());
        expense.setReviewedAt(LocalDateTime.now());
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return expenseRepository.countByStatus(ExpenseStatus.PENDING_REVIEW);
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private ExpenseResponse toResponse(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .business(e.getBusiness())
                .category(e.getCategory())
                .amount(e.getAmount())
                .date(e.getDate())
                .description(e.getDescription())
                .workerName(e.getWorker() != null ? e.getWorker().getFullName() : null)
                .status(e.getStatus())
                .enteredByName(e.getEnteredBy().getFullName())
                .reviewedByName(e.getReviewedBy() != null ? e.getReviewedBy().getFullName() : null)
                .reviewedAt(e.getReviewedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}