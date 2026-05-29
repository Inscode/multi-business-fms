package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ApproveReturnRequest;
import com.multi.finance.dto.request.BillReturnItemRequest;
import com.multi.finance.dto.request.CreateBillReturnRequest;
import com.multi.finance.dto.response.BillReturnItemResponse;
import com.multi.finance.dto.response.BillReturnResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.ReturnStatus;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillReturnServiceImpl {

    private final BillReturnRepository billReturnRepository;
    private final BillRepository billRepository;
    private final ReturnProductRepository returnProductRepository;
    private final WorkerRepository workerRepository;

    @Transactional
    public BillReturnResponse create(Long billId, CreateBillReturnRequest req) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        User caller = getCurrentUser();

        List<BillReturnItem> items = req.getItems().stream().map(i -> {
            ReturnProduct product = null;
            if (i.getProductId() != null) {
                product = returnProductRepository.findById(i.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found"));
            }
            String name = (product != null) ? product.getName() : i.getItemName();
            BigDecimal price = (product != null) ? product.getUnitPrice() : i.getUnitPrice();
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(i.getQuantityRequested()));

            return BillReturnItem.builder()
                    .product(product)
                    .itemName(name)
                    .unitPrice(price)
                    .quantityRequested(i.getQuantityRequested())
                    .quantityReturned(i.getQuantityReturned())
                    .lineTotal(lineTotal)
                    .build();
        }).collect(Collectors.toCollection(ArrayList::new));

        BigDecimal itemsTotal = items.stream()
                .map(BillReturnItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal calculated = itemsTotal;

        if (req.getDiscountPercentage() != null && req.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pctDiscount = calculated.multiply(req.getDiscountPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            calculated = calculated.subtract(pctDiscount);
        }

        if (req.getDiscountFixed() != null && req.getDiscountFixed().compareTo(BigDecimal.ZERO) > 0) {
            calculated = calculated.subtract(req.getDiscountFixed());
        }

        if (calculated.compareTo(BigDecimal.ZERO) < 0) calculated = BigDecimal.ZERO;

        Worker responsibleWorker = null;
        if (req.getResponsibleWorkerId() != null) {
            responsibleWorker = workerRepository.findById(req.getResponsibleWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
        }

        BillReturn billReturn = BillReturn.builder()
                .bill(bill)
                .returnType(req.getReturnType())
                .status(ReturnStatus.PENDING)
                .itemsTotal(itemsTotal)
                .discountPercentage(req.getDiscountPercentage())
                .discountFixed(req.getDiscountFixed())
                .calculatedReturnAmount(calculated)
                .predictedValue(req.getPredictedValue())
                .notes(req.getNotes())
                .responsibleWorker(responsibleWorker)
                .submittedBy(caller)
                .submittedAt(LocalDateTime.now())
                .items(items)
                .build();

        items.forEach(i -> i.setBillReturn(billReturn));

        return toResponse(billReturnRepository.save(billReturn));
    }

    @Transactional(readOnly = true)
    public List<BillReturnResponse> getAll(ReturnStatus status) {
        List<BillReturn> list = (status != null)
                ? billReturnRepository.findByStatusOrderBySubmittedAtDesc(status)
                : billReturnRepository.findAllByOrderBySubmittedAtDesc();
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BillReturnResponse> getForBill(Long billId) {
        return billReturnRepository.findByBillIdOrderBySubmittedAtDesc(billId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return billReturnRepository.countByStatus(ReturnStatus.PENDING);
    }

    @Transactional
    public BillReturnResponse approve(Long id, ApproveReturnRequest req) {
        BillReturn ret = billReturnRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Bill return not found"));

        if (ret.getStatus() != ReturnStatus.PENDING) {
            throw new RuntimeException("Return is not pending");
        }

        if (req.getItems() != null && !req.getItems().isEmpty()) {
            java.util.Map<Long, Integer> qtyMap = req.getItems().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.multi.finance.dto.request.ReceivedItemDto::getId,
                            com.multi.finance.dto.request.ReceivedItemDto::getQuantityReturned
                    ));
            ret.getItems().forEach(item -> {
                Integer received = qtyMap.get(item.getId());
                if (received != null) {
                    item.setQuantityReturned(received);
                }
            });
        }

        BigDecimal receivedTotal = ret.getItems().stream()
                .map(i -> {
                    int qty = i.getQuantityReturned() != null ? i.getQuantityReturned() : 0;
                    return i.getUnitPrice().multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal receivedCalculated = receivedTotal;
        if (ret.getDiscountPercentage() != null && ret.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal disc = receivedCalculated.multiply(ret.getDiscountPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            receivedCalculated = receivedCalculated.subtract(disc);
        }
        if (ret.getDiscountFixed() != null && ret.getDiscountFixed().compareTo(BigDecimal.ZERO) > 0) {
            receivedCalculated = receivedCalculated.subtract(ret.getDiscountFixed());
        }
        if (receivedCalculated.compareTo(BigDecimal.ZERO) < 0) receivedCalculated = BigDecimal.ZERO;

        BigDecimal approvedAmount;
        if ("PREDICTED".equalsIgnoreCase(req.getApproveWith())) {
            approvedAmount = ret.getPredictedValue();
        } else {
            ret.setCalculatedReturnAmount(receivedCalculated);
            approvedAmount = receivedCalculated;
        }

        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Approved amount must be greater than zero");
        }

        Bill bill = ret.getBill();
        BigDecimal newTotal = bill.getTotalAmount().subtract(approvedAmount);
        if (newTotal.compareTo(BigDecimal.ZERO) < 0) newTotal = BigDecimal.ZERO;

        BigDecimal newBalance = newTotal.subtract(bill.getAmountPaid());
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) newBalance = BigDecimal.ZERO;

        bill.setTotalAmount(newTotal);
        bill.setBalanceRemaining(newBalance);
        bill.setFullyPaid(newBalance.compareTo(BigDecimal.ZERO) <= 0);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);

        ret.setStatus(ReturnStatus.APPROVED);
        ret.setApprovedWith(req.getApproveWith().toUpperCase());
        ret.setApprovedAmount(approvedAmount);
        ret.setReviewedBy(getCurrentUser());
        ret.setReviewedAt(LocalDateTime.now());
        return toResponse(billReturnRepository.save(ret));
    }

    @Transactional
    public BillReturnResponse reject(Long id, String reason) {
        BillReturn ret = findById(id);
        if (ret.getStatus() != ReturnStatus.PENDING) {
            throw new RuntimeException("Return is not pending");
        }
        ret.setStatus(ReturnStatus.REJECTED);
        ret.setRejectionReason(reason);
        ret.setReviewedBy(getCurrentUser());
        ret.setReviewedAt(LocalDateTime.now());
        return toResponse(billReturnRepository.save(ret));
    }

    private BillReturn findById(Long id) {
        return billReturnRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill return not found"));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private BillReturnResponse toResponse(BillReturn r) {
        List<BillReturnItemResponse> itemResponses = (r.getItems() == null) ? List.of() :
                r.getItems().stream().map(i -> BillReturnItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct() != null ? i.getProduct().getId() : null)
                        .itemName(i.getItemName())
                        .unitPrice(i.getUnitPrice())
                        .quantityRequested(i.getQuantityRequested())
                        .quantityReturned(i.getQuantityReturned())
                        .lineTotal(i.getLineTotal())
                        .build()).toList();

        BigDecimal shortfall = (r.getItems() == null) ? BigDecimal.ZERO :
                r.getItems().stream().map(i -> {
                    int requested = i.getQuantityRequested() != null ? i.getQuantityRequested() : 0;
                    int returned  = i.getQuantityReturned()  != null ? i.getQuantityReturned()  : 0;
                    int diff = requested - returned;
                    return diff > 0
                            ? i.getUnitPrice().multiply(BigDecimal.valueOf(diff))
                            : BigDecimal.ZERO;
                }).reduce(BigDecimal.ZERO, BigDecimal::add);

        return BillReturnResponse.builder()
                .id(r.getId())
                .billId(r.getBill().getId())
                .billNumber(r.getBill().getBillNumber())
                .customerName(r.getBill().getCustomerName())
                .business(r.getBill().getBusiness().name())
                .returnType(r.getReturnType())
                .status(r.getStatus())
                .items(itemResponses)
                .itemsTotal(r.getItemsTotal())
                .discountPercentage(r.getDiscountPercentage())
                .discountFixed(r.getDiscountFixed())
                .calculatedReturnAmount(r.getCalculatedReturnAmount())
                .predictedValue(r.getPredictedValue())
                .approvedWith(r.getApprovedWith())
                .approvedAmount(r.getApprovedAmount())
                .rejectionReason(r.getRejectionReason())
                .notes(r.getNotes())
                .responsibleWorkerName(r.getResponsibleWorker() != null ? r.getResponsibleWorker().getFullName() : null)
                .submittedByName(r.getSubmittedBy().getFullName())
                .submittedAt(r.getSubmittedAt())
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .shortfallAmount(shortfall.compareTo(BigDecimal.ZERO) > 0 ? shortfall : null)
                .build();
    }
}