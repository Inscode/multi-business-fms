package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ExpenseAdvanceRequest;
import com.multi.finance.dto.request.SettleExpenseAdvanceRequest;
import com.multi.finance.dto.response.ExpenseAdvanceResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.ExpenseCategory;
import com.multi.finance.enums.ExpenseStatus;
import com.multi.finance.enums.DeliveryRunStatus;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseAdvanceService {
    private final ExpenseAdvanceRepository advances;
    private final ExpenseRepository expenses;
    private final DeliveryRunRepository runs;
    private final WorkerRepository workers;
    private final ExpenseBusFareRepository busFares;
    private final CashFundRepository cashFunds;
    private final PettyCashDeductionRepository pettyCashDeductions;

    @Transactional(readOnly = true)
    public List<ExpenseAdvanceResponse> list() {
        return advances.findAllByOrderByActivityDateDescCreatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional
    public ExpenseAdvanceResponse issue(ExpenseAdvanceRequest req) {
        if (req.getBusiness() != BusinessType.RAINCO && req.getBusiness() != BusinessType.PLASTIC)
            throw new IllegalArgumentException("Expense advances are available for Rainco or Plastic only.");
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Advance amount must be greater than zero.");
        BigDecimal available = cashFunds.totalFunded().subtract(expenses.totalSpent())
                .subtract(pettyCashDeductions.totalDeducted()).subtract(advances.totalOutstanding());
        if (req.getAmount().compareTo(available) > 0)
            throw new IllegalArgumentException("Advance exceeds the available expense fund balance (Rs " + available + ").");
        ExpenseAdvance.ExpenseAdvanceBuilder builder = ExpenseAdvance.builder()
                .business(req.getBusiness()).targetType(req.getTargetType())
                .amount(req.getAmount()).notes(req.getNotes())
                .issuedBy(currentUser()).createdAt(LocalDateTime.now()).status("OPEN");
        if ("DELIVERY_ROUTE".equals(req.getTargetType())) {
            if (req.getDeliveryRunId() == null || req.getCollectorId() != null)
                throw new IllegalArgumentException("Choose one delivery route.");
            DeliveryRun run = runs.findByIdWithArea(req.getDeliveryRunId())
                    .orElseThrow(() -> new IllegalArgumentException("Delivery route was not found."));
            if (run.getStatus() == DeliveryRunStatus.CANCELLED)
                throw new IllegalArgumentException("Cannot issue cash for a cancelled delivery route.");
            builder.deliveryRun(run).activityDate(run.getPlannedDate());
        } else if ("COLLECTION_DAY".equals(req.getTargetType())) {
            if (req.getCollectorId() == null || req.getActivityDate() == null || req.getDeliveryRunId() != null)
                throw new IllegalArgumentException("Choose a collector and collection date.");
            Worker worker = workers.findById(req.getCollectorId())
                    .orElseThrow(() -> new IllegalArgumentException("Collector was not found."));
            if (!Boolean.TRUE.equals(worker.getActive())) throw new IllegalArgumentException("Collector is inactive.");
            if (worker.getWorkerType() != com.multi.finance.enums.WorkerType.DELIVERY)
                throw new IllegalArgumentException("Collection-day advances can only be assigned to Delivery staff.");
            builder.collector(worker).activityDate(req.getActivityDate());
        } else throw new IllegalArgumentException("Choose a delivery route or collection day.");
        return response(advances.save(builder.build()));
    }

    @Transactional
    public ExpenseAdvanceResponse settle(Long id, SettleExpenseAdvanceRequest req) {
        ExpenseAdvance advance = advances.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cash advance was not found."));
        if (!"OPEN".equals(advance.getStatus())) throw new IllegalArgumentException("This cash advance is already closed.");
        BigDecimal returned = req.getReturnedAmount() == null ? BigDecimal.ZERO : req.getReturnedAmount();
        if (returned.signum() < 0) throw new IllegalArgumentException("Returned cash cannot be negative.");
        BigDecimal spent = BigDecimal.ZERO;
        List<SettleExpenseAdvanceRequest.Item> items = req.getItems() == null ? List.of() : req.getItems();
        for (var item : items) {
            if (item.getCategory() == null || item.getCategory() == ExpenseCategory.PETTY_CASH)
                throw new IllegalArgumentException("Choose a valid expense category.");
            if ("COLLECTION_DAY".equals(advance.getTargetType())
                    && (item.getCategory() == ExpenseCategory.PARKING || item.getCategory() == ExpenseCategory.DRIVER_SALARY))
                throw new IllegalArgumentException("Parking and Driver Salary are not available for collection-day close-out.");
            if (item.getAmount() == null || item.getAmount().signum() <= 0)
                throw new IllegalArgumentException("Each expense amount must be greater than zero.");
            if (item.getCategory() == ExpenseCategory.BUS_FARE) {
                if (!"COLLECTION_DAY".equals(advance.getTargetType()) || item.getBusFareId() == null)
                    throw new IllegalArgumentException("Select a configured bus fare for a collection day.");
                ExpenseBusFare fare = busFares.findById(item.getBusFareId())
                        .orElseThrow(() -> new IllegalArgumentException("Bus fare route was not found."));
                if (!Boolean.TRUE.equals(fare.getActive()) || fare.getAmount().compareTo(item.getAmount()) != 0)
                    throw new IllegalArgumentException("The selected bus fare is inactive or its amount changed. Refresh and try again.");
            } else if (item.getBusFareId() != null) {
                throw new IllegalArgumentException("Bus fare route does not match this expense.");
            }
            if (item.getCategory() != ExpenseCategory.TEA && item.getCategory() != ExpenseCategory.BUS_FARE
                    && item.getCategory() != ExpenseCategory.DRIVER_SALARY
                    && (item.getReceiptImageUrl() == null || item.getReceiptImageUrl().isBlank()))
                throw new IllegalArgumentException("Attach a photo for each expense except Tea and Driver Salary.");
            spent = spent.add(item.getAmount());
        }
        if (spent.add(returned).compareTo(advance.getAmount()) != 0)
            throw new IllegalArgumentException("Expenses plus returned cash must equal the issued advance.");
        User enteredBy = currentUser();
        for (var item : items) {
            boolean review = item.getCategory() == ExpenseCategory.REPAIR || item.getCategory() == ExpenseCategory.OTHER
                    || (item.getCategory() == ExpenseCategory.DRIVER_SALARY && item.getAmount().compareTo(new BigDecimal("3000")) > 0);
            ExpenseBusFare fare = item.getBusFareId() == null ? null : busFares.findById(item.getBusFareId()).orElseThrow();
            String description = item.getDescription();
            if (fare != null) description = fare.getFromLocation() + " → " + fare.getToLocation();
            expenses.save(Expense.builder().business(advance.getBusiness()).category(item.getCategory())
                    .amount(item.getAmount()).date(advance.getActivityDate()).description(description)
                    .receiptImageUrl(item.getReceiptImageUrl())
                    .receiptUploadedAt(item.getReceiptImageUrl() == null || item.getReceiptImageUrl().isBlank() ? null : LocalDateTime.now())
                    .advanceId(advance.getId()).busFareId(item.getBusFareId())
                    .status(review ? ExpenseStatus.PENDING_REVIEW : ExpenseStatus.RECORDED)
                    .enteredBy(enteredBy).createdAt(LocalDateTime.now()).build());
        }
        advance.setReturnedAmount(returned);
        advance.setStatus("CLOSED");
        advance.setSettledAt(LocalDateTime.now());
        return response(advances.save(advance));
    }

    private User currentUser() { return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal(); }

    private ExpenseAdvanceResponse response(ExpenseAdvance a) {
        return ExpenseAdvanceResponse.builder().id(a.getId()).business(a.getBusiness().name())
                .targetType(a.getTargetType()).deliveryRunId(a.getDeliveryRun() == null ? null : a.getDeliveryRun().getId())
                .deliveryRoute(a.getDeliveryRun() == null ? null : a.getDeliveryRun().areaLabel())
                .collectorId(a.getCollector() == null ? null : a.getCollector().getId())
                .collectorName(a.getCollector() == null ? null : a.getCollector().getFullName())
                .activityDate(a.getActivityDate()).amount(a.getAmount()).returnedAmount(a.getReturnedAmount())
                .status(a.getStatus()).notes(a.getNotes()).issuedByName(a.getIssuedBy().getFullName())
                .createdAt(a.getCreatedAt()).build();
    }
}
