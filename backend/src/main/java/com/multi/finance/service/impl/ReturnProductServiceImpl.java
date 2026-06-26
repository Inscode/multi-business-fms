package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ReturnProductRequest;
import com.multi.finance.dto.response.ReturnProductResponse;
import com.multi.finance.entity.ReturnProduct;
import com.multi.finance.entity.ShadowStockMovement;
import com.multi.finance.entity.User;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.repository.ReturnProductRepository;
import com.multi.finance.repository.ShadowStockMovementRepository;
import com.multi.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnProductServiceImpl {

    private final ReturnProductRepository returnProductRepository;
    private final ShadowStockMovementRepository shadowStockMovementRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ReturnProductResponse> getAll() {
        return returnProductRepository.findAllByOrderByBusinessAscNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnProductResponse> getByBusiness(BusinessType business) {
        return returnProductRepository.findByBusinessAndIsReturnProductTrue(business)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReturnProductResponse create(ReturnProductRequest req) {
        ReturnProduct product = ReturnProduct.builder()
                .business(BusinessType.RAINCO)
                .name(req.getName())
                .unitPrice(req.getUnitPrice())
                .active(true)
                .isStockProduct(false)
                .isReturnProduct(true)
                .createdAt(LocalDateTime.now())
                .build();
        return toResponse(returnProductRepository.save(product));
    }

    @Transactional
    public ReturnProductResponse update(Long id, ReturnProductRequest req) {
        ReturnProduct product = returnProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (req.getName() != null) product.setName(req.getName());
        if (req.getUnitPrice() != null) product.setUnitPrice(req.getUnitPrice());
        return toResponse(returnProductRepository.save(product));
    }

    @Transactional
    public void setReturnProduct(Long id, boolean value) {
        ReturnProduct product = returnProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setIsReturnProduct(value);
        returnProductRepository.save(product);
    }

    @Transactional
    public void setDamageQty(Long id, Long targetQty) {
        if (targetQty < 0) throw new RuntimeException("Qty must be >= 0");
        ReturnProduct product = returnProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        Long current = shadowStockMovementRepository.getDamageBalance(product);
        if (current == null) current = 0L;
        long delta = targetQty - current;
        if (delta == 0) return;

        User currentUser = getCurrentUser();
        ShadowStockMovement movement = ShadowStockMovement.builder()
                .product(product)
                .type(delta > 0
                        ? ShadowStockMovement.MovementType.DAMAGE_IN
                        : ShadowStockMovement.MovementType.DAMAGE_TO_COMPANY)
                .quantity(Math.abs(delta))
                .movementDate(LocalDate.now())
                .notes("Admin damage qty adjustment: " + current + " → " + targetQty)
                .enteredBy(currentUser)
                .cancelled(false)
                .createdAt(LocalDateTime.now())
                .build();
        shadowStockMovementRepository.save(movement);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private ReturnProductResponse toResponse(ReturnProduct p) {
        return ReturnProductResponse.builder()
                .id(p.getId())
                .business(p.getBusiness())
                .name(p.getName())
                .unitPrice(p.getUnitPrice())
                .active(p.getActive())
                .isStockProduct(p.getIsStockProduct())
                .isReturnProduct(p.getIsReturnProduct())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
