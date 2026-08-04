package com.multi.finance.invoicing.controller;

import com.multi.finance.invoicing.dto.request.BrandRequest;
import com.multi.finance.invoicing.dto.response.BrandResponse;
import com.multi.finance.invoicing.entity.Brand;
import com.multi.finance.invoicing.entity.DiscountSlab;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.Principal;
import com.multi.finance.invoicing.repository.BrandRepository;
import com.multi.finance.invoicing.repository.DiscountSlabRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
@RequestMapping("/api/invoicing/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandRepository brandRepo;
    private final DiscountSlabRepository slabRepo;

    @GetMapping
    public List<BrandResponse> list(@RequestParam(required = false) CategoryType category) {
        List<Brand> brands = category != null
                ? brandRepo.findByCategoryWithSlabs(category)
                : brandRepo.findAllActiveWithSlabs();
        return brands.stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandRequest req) {
        Brand brand = new Brand();
        applyRequest(brand, req);
        brandRepo.save(brand);
        if (req.getSlabs() != null) {
            saveSlabs(brand, req);
        }
        return ResponseEntity.ok(toResponse(brandRepo.findByCategoryWithSlabs(brand.getCategory())
                .stream().filter(b -> b.getId().equals(brand.getId())).findFirst().orElse(brand)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<BrandResponse> update(@PathVariable Long id, @Valid @RequestBody BrandRequest req) {
        Brand brand = brandRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Brand not found"));
        applyRequest(brand, req);
        slabRepo.deleteAll(slabRepo.findByBrandIdOrderBySortOrder(id));
        brandRepo.save(brand);
        if (req.getSlabs() != null) {
            saveSlabs(brand, req);
        }
        return ResponseEntity.ok(toResponse(brand));
    }

    private void applyRequest(Brand brand, BrandRequest req) {
        brand.setName(req.getName());
        brand.setBrandCode(req.getBrandCode());
        brand.setCategory(req.getCategory());
        brand.setPrincipal(req.getPrincipal() != null ? req.getPrincipal() : defaultPrincipal(req.getCategory()));
        brand.setDiscountType(req.getDiscountType());
        brand.setDefaultMarginPct(req.getDefaultMarginPct());
    }

    /** The brand form doesn't expose principal — in this catalog it follows category 1:1. */
    private Principal defaultPrincipal(CategoryType category) {
        return switch (category) {
            case RAINCO -> Principal.RAINCO;
            case STATIONERY -> Principal.STATIONERY_AGENT;
            case PLASTIC -> Principal.OWN;
        };
    }

    private void saveSlabs(Brand brand, BrandRequest req) {
        int i = 0;
        for (BrandRequest.SlabRequest sr : req.getSlabs()) {
            DiscountSlab slab = new DiscountSlab();
            slab.setBrand(brand);
            slab.setMinValue(sr.getMinValue());
            slab.setMaxValue(sr.getMaxValue());
            slab.setDiscountPct(sr.getDiscountPct());
            slab.setSortOrder(sr.getSortOrder() != null ? sr.getSortOrder() : i);
            slabRepo.save(slab);
            i++;
        }
    }

    private BrandResponse toResponse(Brand b) {
        BrandResponse r = new BrandResponse();
        r.setId(b.getId());
        r.setName(b.getName());
        r.setBrandCode(b.getBrandCode());
        r.setCategory(b.getCategory());
        r.setPrincipal(b.getPrincipal());
        r.setDiscountType(b.getDiscountType());
        r.setDefaultMarginPct(b.getDefaultMarginPct());
        r.setActive(b.isActive());
        r.setSlabs(b.getSlabs().stream()
                .sorted(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                .map(s -> {
                    BrandResponse.SlabResponse sr = new BrandResponse.SlabResponse();
                    sr.setId(s.getId());
                    sr.setMinValue(s.getMinValue());
                    sr.setMaxValue(s.getMaxValue());
                    sr.setDiscountPct(s.getDiscountPct());
                    sr.setSortOrder(s.getSortOrder());
                    return sr;
                }).toList());
        return r;
    }
}
