package com.multi.finance.service.impl;

import com.multi.finance.service.BillBalance;
import com.multi.finance.dto.request.BackorderItemRequest;
import com.multi.finance.dto.request.BackorderSubmitRequest;
import com.multi.finance.dto.response.BackorderReqItemResponse;
import com.multi.finance.dto.response.BackorderRequestResponse;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.entity.*;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BackorderServiceImpl {

    private final BillBackorderRequestRepository requestRepo;
    private final BillBackorderItemRepository itemRepo;
    private final BillRepository billRepository;
    private final ReturnProductRepository productRepo;
    private final ShadowStockMovementRepository movementRepo;
    private final BillStockItemRepository billStockItemRepository;

    @Transactional
    public BackorderRequestResponse submit(BackorderSubmitRequest req) {
        User caller = getCurrentUser();
        Bill bill = billRepository.findById(req.getBillId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        BillBackorderRequest request = BillBackorderRequest.builder()
                .bill(bill)
                .submittedBy(caller)
                .submittedAt(LocalDateTime.now())
                .notes(req.getNotes())
                .build();

        for (BackorderItemRequest ir : req.getItems()) {
            ReturnProduct product = productRepo.findById(ir.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + ir.getProductId()));
            BigDecimal unitPrice = product.getUnitPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(ir.getQuantity()));
            BigDecimal amountToAdd = ir.getAmountToAdd() != null ? ir.getAmountToAdd() : BigDecimal.ZERO;
            request.getItems().add(BillBackorderItem.builder()
                    .request(request)
                    .product(product)
                    .quantity(ir.getQuantity())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .amountToAdd(amountToAdd)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        requestRepo.save(request);
        return toResponse(request, Map.of());
    }

    @Transactional(readOnly = true)
    public List<BackorderRequestResponse> getAll() {
        List<BillBackorderRequest> all = requestRepo.findAllByOrderBySubmittedAtDesc();
        List<Long> productIds = all.stream()
                .flatMap(r -> r.getItems().stream().map(i -> i.getProduct().getId()))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Long> availableMap = buildAvailableMap(productIds);
        return all.stream().map(r -> toResponse(r, availableMap)).collect(Collectors.toList());
    }

    @Transactional
    public void approve(Long id) {
        User caller = getCurrentUser();
        BillBackorderRequest request = requestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Backorder request not found"));
        if (request.getStatus() != BillBackorderRequest.ApprovalStatus.PENDING) {
            throw new RuntimeException("Request is not PENDING");
        }

        List<Long> productIds = request.getItems().stream()
                .map(i -> i.getProduct().getId())
                .collect(Collectors.toList());
        Map<Long, Long> availableMap = buildAvailableMap(productIds);

        List<String> shortages = new ArrayList<>();
        for (BillBackorderItem item : request.getItems()) {
            long available = availableMap.getOrDefault(item.getProduct().getId(), 0L);
            if (available < item.getQuantity()) {
                shortages.add(item.getProduct().getName()
                        + " (need " + item.getQuantity() + ", have " + available + ")");
            }
        }
        if (!shortages.isEmpty()) {
            throw new RuntimeException("Insufficient stock for: " + String.join(", ", shortages));
        }

        Bill bill = request.getBill();
        BigDecimal totalToAdd = BigDecimal.ZERO;

        for (BillBackorderItem item : request.getItems()) {
            ShadowStockMovement movement = ShadowStockMovement.builder()
                    .product(item.getProduct())
                    .type(ShadowStockMovement.MovementType.BILL_OUT)
                    .quantity(item.getQuantity())
                    .bill(bill)
                    .movementDate(LocalDate.now())
                    .notes("Backorder approval - request #" + request.getId())
                    .enteredBy(caller)
                    .cancelled(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            movementRepo.save(movement);

            billStockItemRepository.save(BillStockItem.builder()
                    .bill(bill)
                    .product(item.getProduct())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .lineTotal(item.getLineTotal())
                    .backorder(false)
                    .createdAt(LocalDateTime.now())
                    .build());

            totalToAdd = totalToAdd.add(item.getAmountToAdd());
        }

        if (totalToAdd.compareTo(BigDecimal.ZERO) > 0) {
            bill.setTotalAmount(bill.getTotalAmount().add(totalToAdd));
            BillBalance.recompute(bill);
            billRepository.save(bill);
        }

        request.setStatus(BillBackorderRequest.ApprovalStatus.APPROVED);
        request.setReviewedBy(caller);
        request.setReviewedAt(LocalDateTime.now());
        requestRepo.save(request);
    }

    @Transactional
    public void reject(Long id, String reason) {
        User caller = getCurrentUser();
        BillBackorderRequest request = requestRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Backorder request not found"));
        if (request.getStatus() != BillBackorderRequest.ApprovalStatus.PENDING) {
            throw new RuntimeException("Request is not PENDING");
        }
        request.setStatus(BillBackorderRequest.ApprovalStatus.REJECTED);
        request.setReviewedBy(caller);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(reason);
        requestRepo.save(request);
    }

    @Transactional(readOnly = true)
    public List<BillResponse> getActiveRaincoBills() {
        return billRepository.findActiveRaincoBillsForBackorder().stream()
                .map(b -> BillResponse.builder()
                        .id(b.getId())
                        .billNumber(b.getBillNumber())
                        .customerName(b.getCustomerName())
                        .totalAmount(b.getTotalAmount())
                        .balanceRemaining(b.getBalanceRemaining())
                        .billDate(b.getBillDate())
                        .status(b.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<Long, Long> buildAvailableMap(List<Long> productIds) {
        if (productIds.isEmpty()) return Map.of();
        Map<Long, Long> result = new HashMap<>();
        movementRepo.getAvailableBalancesForProducts(productIds).forEach(row -> {
            Long pid = ((Number) row[0]).longValue();
            Long qty = ((Number) row[1]).longValue();
            result.put(pid, qty);
        });
        return result;
    }

    private BackorderRequestResponse toResponse(BillBackorderRequest r, Map<Long, Long> availableMap) {
        List<BackorderReqItemResponse> items = r.getItems().stream().map(i -> {
            long available = availableMap.getOrDefault(i.getProduct().getId(), 0L);
            return BackorderReqItemResponse.builder()
                    .id(i.getId())
                    .productId(i.getProduct().getId())
                    .productName(i.getProduct().getName())
                    .quantity(i.getQuantity())
                    .unitPrice(i.getUnitPrice())
                    .lineTotal(i.getLineTotal())
                    .amountToAdd(i.getAmountToAdd())
                    .availableQty(available)
                    .build();
        }).collect(Collectors.toList());

        boolean hasShortage = items.stream()
                .anyMatch(i -> i.getAvailableQty() < i.getQuantity());
        BigDecimal totalToAdd = items.stream()
                .map(BackorderReqItemResponse::getAmountToAdd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BackorderRequestResponse.builder()
                .id(r.getId())
                .billId(r.getBill().getId())
                .billNumber(r.getBill().getBillNumber())
                .customerName(r.getBill().getCustomerName())
                .status(r.getStatus().name())
                .submittedByName(r.getSubmittedBy() != null ? r.getSubmittedBy().getFullName() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .submittedAt(r.getSubmittedAt())
                .reviewedAt(r.getReviewedAt())
                .notes(r.getNotes())
                .rejectionReason(r.getRejectionReason())
                .items(items)
                .totalAmountToAdd(totalToAdd)
                .hasInsufficientStock(hasShortage)
                .build();
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
