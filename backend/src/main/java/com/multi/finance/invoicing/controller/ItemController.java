package com.multi.finance.invoicing.controller;

import com.multi.finance.invoicing.dto.request.ItemRequest;
import com.multi.finance.invoicing.dto.request.StockAdjustRequest;
import com.multi.finance.invoicing.dto.response.ItemResponse;
import com.multi.finance.invoicing.entity.Brand;
import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.entity.StockMovement;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.StockMovementType;
import com.multi.finance.invoicing.repository.BrandRepository;
import com.multi.finance.invoicing.repository.ItemRepository;
import com.multi.finance.invoicing.repository.StockMovementRepository;
import com.multi.finance.invoicing.service.DiscountEngineService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
@RequestMapping("/api/invoicing/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemRepository itemRepo;
    private final BrandRepository brandRepo;
    private final StockMovementRepository movementRepo;
    private final DiscountEngineService engine = new DiscountEngineService();

    @GetMapping
    public List<ItemResponse> list(@RequestParam(required = false) CategoryType category) {
        List<Item> items = category != null
                ? itemRepo.findByCategoryWithBrand(category)
                : itemRepo.findAllWithBrand();
        return items.stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemRequest req) {
        Brand brand = brandRepo.findById(req.getBrandId())
                .orElseThrow(() -> new EntityNotFoundException("Brand not found"));
        Item item = new Item();
        applyRequest(item, req, brand);
        itemRepo.save(item);
        return ResponseEntity.ok(toResponse(item));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ItemResponse> update(@PathVariable Long id, @Valid @RequestBody ItemRequest req) {
        Item item = itemRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("Item not found"));
        Brand brand = brandRepo.findById(req.getBrandId())
                .orElseThrow(() -> new EntityNotFoundException("Brand not found"));
        applyRequest(item, req, brand);
        itemRepo.save(item);
        return ResponseEntity.ok(toResponse(item));
    }

    @PostMapping("/stock-adjust")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ItemResponse> adjustStock(@Valid @RequestBody StockAdjustRequest req) {
        itemRepo.adjustStock(req.getItemId(), req.getDelta());
        Item item = itemRepo.findById(req.getItemId())
                .orElseThrow(() -> new EntityNotFoundException("Item not found"));
        StockMovement mv = new StockMovement();
        mv.setItem(item);
        mv.setType(StockMovementType.MANUAL_ADJUSTMENT);
        mv.setQuantity(req.getDelta());
        mv.setNotes(req.getNotes());
        movementRepo.save(mv);
        return ResponseEntity.ok(toResponse(item));
    }

    private void applyRequest(Item item, ItemRequest req, Brand brand) {
        item.setItemCode(req.getItemCode());
        item.setDescription(req.getDescription());
        item.setCategory(req.getCategory());
        item.setBrand(brand);
        item.setMrp(req.getMrp());
        item.setMarginPct(req.getMarginPct());
        item.setWholesalePrice(req.getWholesalePrice());
    }

    private ItemResponse toResponse(Item item) {
        ItemResponse r = new ItemResponse();
        r.setId(item.getId());
        r.setItemCode(item.getItemCode());
        r.setDescription(item.getDescription());
        r.setCategory(item.getCategory());
        r.setBrandId(item.getBrand().getId());
        r.setBrandName(item.getBrand().getName());
        r.setMrp(item.getMrp());
        r.setMarginPct(item.getMarginPct());
        if (item.getWholesalePrice() != null) {
            r.setWsp(item.getWholesalePrice());
        } else if (item.getMrp() != null) {
            r.setWsp(engine.computeWsp(item));
        }
        r.setWholesalePrice(item.getWholesalePrice());
        r.setActive(item.isActive());
        r.setStockQty(item.getStockQty());
        return r;
    }
}
