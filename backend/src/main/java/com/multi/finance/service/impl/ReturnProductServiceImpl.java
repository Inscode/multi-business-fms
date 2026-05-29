package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ReturnProductRequest;
import com.multi.finance.dto.response.ReturnProductResponse;
import com.multi.finance.entity.ReturnProduct;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.repository.ReturnProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReturnProductServiceImpl {

    private final ReturnProductRepository returnProductRepository;

    @Transactional(readOnly = true)
    public List<ReturnProductResponse> getAll() {
        return returnProductRepository.findAllByOrderByBusinessAscNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnProductResponse> getByBusiness(BusinessType business) {
        return returnProductRepository.findByBusinessAndActiveTrue(business)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReturnProductResponse create(ReturnProductRequest req) {
        ReturnProduct product = ReturnProduct.builder()
                .business(req.getBusiness())
                .name(req.getName())
                .unitPrice(req.getUnitPrice())
                .active(true)
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
        if (req.getBusiness() != null) product.setBusiness(req.getBusiness());
        return toResponse(returnProductRepository.save(product));
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        ReturnProduct product = returnProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setActive(active);
        returnProductRepository.save(product);
    }

    private ReturnProductResponse toResponse(ReturnProduct p) {
        return ReturnProductResponse.builder()
                .id(p.getId())
                .business(p.getBusiness())
                .name(p.getName())
                .unitPrice(p.getUnitPrice())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .build();
    }
}