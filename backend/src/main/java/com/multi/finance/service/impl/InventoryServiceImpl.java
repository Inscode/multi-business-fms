package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ReturnProductRequest;
import com.multi.finance.dto.request.StockItemRequest;
import com.multi.finance.dto.request.SubmitStockInRequest;
import com.multi.finance.dto.response.ProductStockBalanceResponse;
import com.multi.finance.dto.response.ReturnProductResponse;
import com.multi.finance.dto.response.StockInRequestResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl {

    private final ReturnProductRepository productRepository;
    private final StockInRequestRepository stockInRequestRepository;
    private final ShadowStockMovementRepository movementRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // PRODUCTS
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductStockBalanceResponse> getRaincoStockBalances() {
        // Single aggregate query — avoids N+1 (2 queries per product previously)
        List<Object[]> rows = movementRepository.getAggregatedBalancesForRainco();
        Map<Long, long[]> balanceMap = new HashMap<>();
        for (Object[] row : rows) {
            Long productId  = ((Number) row[0]).longValue();
            long available  = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long damage     = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            balanceMap.put(productId, new long[]{available, damage});
        }

        return productRepository.findByBusiness(BusinessType.RAINCO).stream()
                .map(p -> {
                    long[] bal = balanceMap.getOrDefault(p.getId(), new long[]{0L, 0L});
                    return ProductStockBalanceResponse.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .unitPrice(p.getUnitPrice())
                            .active(p.getActive())
                            .availableQty(bal[0])
                            .damageQty(bal[1])
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ReturnProductResponse createProduct(ReturnProductRequest req) {
        ReturnProduct product = ReturnProduct.builder()
                .business(BusinessType.RAINCO)
                .name(req.getName())
                .unitPrice(req.getUnitPrice())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        ReturnProduct saved = productRepository.save(product);
        return toProductResponse(saved);
    }

    @Transactional
    public void deleteProduct(Long id) {
        ReturnProduct p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        // Check if product has any stock movements before deleting
        Long balance = movementRepository.getAvailableBalance(p);
        if (balance != null && balance != 0) {
            throw new RuntimeException("Cannot delete product with existing stock balance (" + balance + " units). Deactivate it instead.");
        }
        productRepository.delete(p);
    }

    @Transactional
    public ReturnProductResponse updateProduct(Long id, ReturnProductRequest req) {
        ReturnProduct p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        if (req.getName() != null) p.setName(req.getName());
        if (req.getUnitPrice() != null) p.setUnitPrice(req.getUnitPrice());
        return toProductResponse(productRepository.save(p));
    }

    // ─────────────────────────────────────────────────────────────
    // OPENING STOCK (ADMIN direct — no approval needed)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void addOpeningStock(SubmitStockInRequest request) {
        User currentUser = getCurrentUser();
        for (StockItemRequest item : request.getItems()) {
            ReturnProduct product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));
            ShadowStockMovement movement = ShadowStockMovement.builder()
                    .product(product)
                    .type(ShadowStockMovement.MovementType.STOCK_IN)
                    .quantity(item.getQuantity())
                    .invoiceNumber(request.getReferenceNumber())
                    .movementDate(request.getStockDate() != null ? request.getStockDate() : LocalDate.now())
                    .enteredBy(currentUser)
                    .cancelled(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            movementRepository.save(movement);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // STOCK-IN REQUESTS (ACC/MAIN_ACC submit → ADMIN approves)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public StockInRequestResponse submitStockIn(SubmitStockInRequest request) {
        User currentUser = getCurrentUser();

        StockInRequest stockIn = StockInRequest.builder()
                .referenceNumber(request.getReferenceNumber())
                .stockDate(request.getStockDate() != null ? request.getStockDate() : LocalDate.now())
                .notes(request.getNotes())
                .status(StockInRequest.ApprovalStatus.PENDING)
                .submittedBy(currentUser)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        StockInRequest saved = stockInRequestRepository.save(stockIn);

        for (StockItemRequest item : request.getItems()) {
            ReturnProduct product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));
            StockInRequestItem lineItem = StockInRequestItem.builder()
                    .stockInRequest(saved)
                    .product(product)
                    .quantity(item.getQuantity())
                    .createdAt(LocalDateTime.now())
                    .build();
            saved.getItems().add(lineItem);
        }

        stockInRequestRepository.save(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StockInRequestResponse> getAllStockInRequests() {
        return stockInRequestRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockInRequestResponse> getPendingStockInRequests() {
        return stockInRequestRepository.findByStatusOrderByCreatedAtDesc(StockInRequest.ApprovalStatus.PENDING)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public void approveStockIn(Long id) {
        User currentUser = getCurrentUser();
        StockInRequest req = stockInRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock-in request not found"));
        if (req.getStatus() != StockInRequest.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only PENDING requests can be approved");
        }

        req.setStatus(StockInRequest.ApprovalStatus.APPROVED);
        req.setApprovedBy(currentUser);
        req.setApprovedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());
        stockInRequestRepository.save(req);

        // Create actual STOCK_IN movements
        for (StockInRequestItem item : req.getItems()) {
            ShadowStockMovement movement = ShadowStockMovement.builder()
                    .product(item.getProduct())
                    .type(ShadowStockMovement.MovementType.STOCK_IN)
                    .quantity(item.getQuantity())
                    .invoiceNumber(req.getReferenceNumber())
                    .movementDate(req.getStockDate())
                    .enteredBy(currentUser)
                    .cancelled(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            movementRepository.save(movement);
        }
    }

    @Transactional
    public void rejectStockIn(Long id, String reason) {
        StockInRequest req = stockInRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock-in request not found"));
        if (req.getStatus() != StockInRequest.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only PENDING requests can be rejected");
        }
        req.setStatus(StockInRequest.ApprovalStatus.REJECTED);
        req.setRejectionReason(reason);
        req.setUpdatedAt(LocalDateTime.now());
        stockInRequestRepository.save(req);
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private ReturnProductResponse toProductResponse(ReturnProduct p) {
        return ReturnProductResponse.builder()
                .id(p.getId())
                .business(p.getBusiness())
                .name(p.getName())
                .unitPrice(p.getUnitPrice())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private StockInRequestResponse toResponse(StockInRequest r) {
        List<StockInRequestResponse.StockInItemDetail> items = r.getItems().stream()
                .map(i -> StockInRequestResponse.StockInItemDetail.builder()
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return StockInRequestResponse.builder()
                .id(r.getId())
                .referenceNumber(r.getReferenceNumber())
                .stockDate(r.getStockDate())
                .notes(r.getNotes())
                .status(r.getStatus().name())
                .submittedByName(r.getSubmittedBy().getFullName())
                .createdAt(r.getCreatedAt())
                .approvedByName(r.getApprovedBy() != null ? r.getApprovedBy().getFullName() : null)
                .approvedAt(r.getApprovedAt())
                .rejectionReason(r.getRejectionReason())
                .items(items)
                .build();
    }
}
