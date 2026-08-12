package com.multi.finance.service.impl;

import com.multi.finance.dto.request.CreateDamageDispatchRequest;
import com.multi.finance.dto.response.DamageDispatchResponse;
import com.multi.finance.dto.response.DamageStockResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.DispatchStatus;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DamageDispatchService {

    private final DamageDispatchRepository dispatchRepository;
    private final ReturnProductRepository returnProductRepository;
    private final ShadowStockMovementRepository shadowStockMovementRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DamageStockResponse> getDamageStock(String business) {
        List<Object[]> rows = shadowStockMovementRepository.getDamageStockByBusiness(BusinessType.valueOf(business));

        // Collect product IDs to fetch names and prices
        List<Long> ids = rows.stream().map(r -> (Long) r[0]).toList();
        Map<Long, ReturnProduct> products = returnProductRepository.findAllById(ids)
                .stream().collect(Collectors.toMap(ReturnProduct::getId, p -> p));

        return rows.stream().map(r -> {
            Long productId = (Long) r[0];
            Long damageQty = ((Number) r[1]).longValue();
            ReturnProduct p = products.get(productId);
            return DamageStockResponse.builder()
                    .productId(productId)
                    .productName(p != null ? p.getName() : "Unknown")
                    .unitPrice(p != null ? p.getUnitPrice() : BigDecimal.ZERO)
                    .damageQty(damageQty)
                    .build();
        }).toList();
    }

    @Transactional
    public DamageDispatchResponse create(CreateDamageDispatchRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty())
            throw new RuntimeException("At least one item is required");

        User user = getCurrentUser();
        BusinessType business = BusinessType.valueOf(request.getBusiness());

        // Fetch all products up front
        List<Long> productIds = request.getItems().stream()
                .map(CreateDamageDispatchRequest.ItemRequest::getProductId).toList();
        Map<Long, ReturnProduct> products = returnProductRepository.findAllById(productIds)
                .stream().collect(Collectors.toMap(ReturnProduct::getId, p -> p));

        // Build dispatch items and shadow movements
        List<DamageDispatchItem> dispatchItems = request.getItems().stream().map(item -> {
            ReturnProduct product = products.get(item.getProductId());
            if (product == null) throw new RuntimeException("Product not found: " + item.getProductId());
            if (item.getQuantity() <= 0) throw new RuntimeException("Quantity must be > 0");

            BigDecimal lineTotal = product.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));

            return DamageDispatchItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .unitPrice(product.getUnitPrice())
                    .quantity(item.getQuantity())
                    .lineTotal(lineTotal)
                    .build();
        }).collect(Collectors.toList());

        BigDecimal totalValue = dispatchItems.stream()
                .map(DamageDispatchItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DamageDispatch dispatch = DamageDispatch.builder()
                .business(business)
                .dispatchDate(request.getDispatchDate() != null ? request.getDispatchDate() : LocalDate.now())
                .totalValue(totalValue)
                .predictedValue(request.getPredictedValue())
                .notes(request.getNotes())
                .enteredBy(user)
                .createdAt(LocalDateTime.now())
                .build();
        dispatch = dispatchRepository.save(dispatch);

        // Link items to dispatch and save
        final DamageDispatch savedDispatch = dispatch;
        dispatchItems.forEach(item -> item.setDispatch(savedDispatch));
        savedDispatch.setItems(dispatchItems);
        dispatch = dispatchRepository.save(savedDispatch);

        // No stock movement here — damage stock is only deducted when an admin approves.
        return toResponse(dispatch);
    }

    /**
     * Admin approval — this is where the damage stock actually leaves. Availability is
     * re-checked at this moment, since other dispatches may have been approved since
     * this one was submitted.
     */
    @Transactional
    public DamageDispatchResponse approve(Long id) {
        DamageDispatch dispatch = dispatchRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Dispatch not found"));
        if (dispatch.getStatus() != DispatchStatus.PENDING) {
            throw new RuntimeException("Dispatch is already " + dispatch.getStatus());
        }

        Map<Long, Long> available = availableDamageStock(dispatch.getBusiness());
        List<String> shortfalls = new ArrayList<>();
        for (DamageDispatchItem item : dispatch.getItems()) {
            long have = available.getOrDefault(item.getProduct().getId(), 0L);
            if (item.getQuantity() > have) {
                shortfalls.add(item.getProductName() + " — available " + have
                        + ", dispatching " + item.getQuantity());
            }
        }
        if (!shortfalls.isEmpty()) {
            throw new RuntimeException("Not enough damage stock: " + String.join("; ", shortfalls));
        }

        User user = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        for (DamageDispatchItem item : dispatch.getItems()) {
            ShadowStockMovement movement = ShadowStockMovement.builder()
                    .product(item.getProduct())
                    .type(ShadowStockMovement.MovementType.DAMAGE_TO_COMPANY)
                    .quantity((long) item.getQuantity())
                    .movementDate(dispatch.getDispatchDate())
                    .notes("Damage dispatch #" + dispatch.getId()
                            + (dispatch.getNotes() != null ? ": " + dispatch.getNotes() : ""))
                    .enteredBy(user)
                    .cancelled(false)
                    .createdAt(now)
                    .build();
            shadowStockMovementRepository.save(movement);
        }

        dispatch.setStatus(DispatchStatus.APPROVED);
        dispatch.setReviewedBy(user);
        dispatch.setReviewedAt(now);
        return toResponse(dispatchRepository.save(dispatch));
    }

    @Transactional
    public DamageDispatchResponse reject(Long id, String reason) {
        DamageDispatch dispatch = dispatchRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Dispatch not found"));
        if (dispatch.getStatus() != DispatchStatus.PENDING) {
            throw new RuntimeException("Dispatch is already " + dispatch.getStatus());
        }
        dispatch.setStatus(DispatchStatus.REJECTED);
        dispatch.setRejectionReason(reason);
        dispatch.setReviewedBy(getCurrentUser());
        dispatch.setReviewedAt(LocalDateTime.now());
        return toResponse(dispatchRepository.save(dispatch));
    }

    /** productId -> damage units currently on hand for this business. */
    private Map<Long, Long> availableDamageStock(BusinessType business) {
        return shadowStockMovementRepository.getDamageStockByBusiness(business)
                .stream()
                .collect(Collectors.toMap(
                        r -> (Long) r[0],
                        r -> ((Number) r[1]).longValue()));
    }

    @Transactional(readOnly = true)
    public List<DamageDispatchResponse> getAll() {
        return dispatchRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(d -> {
                    // items not fetched here — load on demand for list view (summary only)
                    return DamageDispatchResponse.builder()
                            .id(d.getId())
                            .business(d.getBusiness().name())
                            .dispatchDate(d.getDispatchDate())
                            .totalValue(d.getTotalValue())
                            .predictedValue(d.getPredictedValue())
                            .notes(d.getNotes())
                            .enteredByName(d.getEnteredBy() != null ? d.getEnteredBy().getFullName() : null)
                            .status(d.getStatus() != null ? d.getStatus().name() : null)
                            .rejectionReason(d.getRejectionReason())
                            .reviewedByName(d.getReviewedBy() != null ? d.getReviewedBy().getFullName() : null)
                            .reviewedAt(d.getReviewedAt())
                            .createdAt(d.getCreatedAt())
                            .items(List.of())
                            .build();
                }).toList();
    }

    @Transactional(readOnly = true)
    public DamageDispatchResponse getById(Long id) {
        DamageDispatch dispatch = dispatchRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Dispatch not found"));
        return toResponse(dispatch);
    }

    private DamageDispatchResponse toResponse(DamageDispatch d) {
        List<DamageDispatchResponse.ItemResponse> itemResponses = d.getItems() == null ? List.of() :
                d.getItems().stream().map(i -> DamageDispatchResponse.ItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineTotal(i.getLineTotal())
                        .build()).toList();

        return DamageDispatchResponse.builder()
                .id(d.getId())
                .business(d.getBusiness().name())
                .dispatchDate(d.getDispatchDate())
                .totalValue(d.getTotalValue())
                .predictedValue(d.getPredictedValue())
                .notes(d.getNotes())
                .enteredByName(d.getEnteredBy() != null ? d.getEnteredBy().getFullName() : null)
                .status(d.getStatus() != null ? d.getStatus().name() : null)
                .rejectionReason(d.getRejectionReason())
                .reviewedByName(d.getReviewedBy() != null ? d.getReviewedBy().getFullName() : null)
                .reviewedAt(d.getReviewedAt())
                .createdAt(d.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
