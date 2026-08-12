package com.multi.finance.invoicing.controller;

import com.multi.finance.invoicing.dto.request.GrnLineRequest;
import com.multi.finance.invoicing.dto.request.GrnRequest;
import com.multi.finance.invoicing.dto.response.GrnResponse;
import com.multi.finance.invoicing.entity.GoodsReceivedNote;
import com.multi.finance.invoicing.entity.GrnLine;
import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.entity.StockMovement;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.GrnStatus;
import com.multi.finance.invoicing.enums.StockMovementType;
import com.multi.finance.invoicing.repository.GrnRepository;
import com.multi.finance.invoicing.repository.ItemRepository;
import com.multi.finance.invoicing.repository.StockMovementRepository;
import com.multi.finance.invoicing.repository.SystemSettingsRepository;
import com.multi.finance.invoicing.service.DiscountEngineService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Goods received notes. Accountants and admins record incoming stock; only an admin
 * can approve, and stock levels move at that moment — never at submission.
 */
@RestController
@RequestMapping("/api/invoicing/grn")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
public class GrnController {

    private final GrnRepository grnRepo;
    private final ItemRepository itemRepo;
    private final StockMovementRepository movementRepo;
    private final SystemSettingsRepository settingsRepo;
    private final DiscountEngineService engine = new DiscountEngineService();

    @PostMapping
    @Transactional
    public ResponseEntity<GrnResponse> create(@Valid @RequestBody GrnRequest req, Authentication auth) {
        GoodsReceivedNote grn = new GoodsReceivedNote();
        grn.setCategory(req.getCategory());
        grn.setSupplierName(req.getSupplierName());
        grn.setReceivedDate(req.getReceivedDate());
        grn.setNotes(req.getNotes());
        grn.setSubmittedBy(auth.getName());
        grn.setStatus(GrnStatus.PENDING);
        grn.setDiscountPct(categoryDiscount(req.getCategory()));
        grn.setPaymentTermsDays(req.getPaymentTermsDays());
        grn.setPaymentRequired(req.getPaymentRequired() == null || req.getPaymentRequired());
        if (req.getPaymentTermsDays() != null) {
            grn.setDueDate(req.getReceivedDate().plusDays(req.getPaymentTermsDays()));
        }
        grn.setGrnNo(String.format("GRN-%06d", grnRepo.nextSequenceValue()));

        List<GrnLine> lines = new ArrayList<>();
        for (GrnLineRequest lr : req.getLines()) {
            Item item = itemRepo.findById(lr.getItemId())
                    .orElseThrow(() -> new EntityNotFoundException("Item not found: " + lr.getItemId()));

            // A GRN is category-wise — a mismatched line means the wrong item was picked
            if (item.getCategory() != req.getCategory()) {
                throw new IllegalArgumentException("Item \"" + item.getDescription() + "\" is "
                        + item.getCategory() + " but this GRN is " + req.getCategory());
            }

            // Price is never taken from the client -- it is the catalog price, full stop.
            BigDecimal unitCost = catalogPrice(item);

            GrnLine line = new GrnLine();
            line.setGrn(grn);
            line.setItem(item);
            line.setQty(lr.getQty());
            line.setUnitCost(unitCost);
            line.setLineTotal(unitCost.multiply(BigDecimal.valueOf(lr.getQty()))
                    .setScale(2, RoundingMode.HALF_UP));
            lines.add(line);
        }

        grn.getLines().addAll(lines);
        grnRepo.save(grn);
        return ResponseEntity.ok(toResponse(grn));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<GrnResponse> list(@RequestParam(required = false) GrnStatus status) {
        List<GoodsReceivedNote> grns = status != null
                ? grnRepo.findByStatusWithLines(status)
                : grnRepo.findAllWithLines();
        return grns.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<GrnResponse> byId(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(grnRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found"))));
    }

    /** Approval is the point stock actually moves. */
    @PostMapping("/{id}/approve")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrnResponse> approve(@PathVariable Long id, Authentication auth) {
        GoodsReceivedNote grn = grnRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found"));
        if (grn.getStatus() != GrnStatus.PENDING) {
            throw new IllegalArgumentException("GRN " + grn.getGrnNo() + " is already " + grn.getStatus());
        }

        grn.setStatus(GrnStatus.APPROVED);
        grn.setReviewedBy(auth.getName());
        grn.setReviewedAt(LocalDateTime.now());

        for (GrnLine line : grn.getLines()) {
            itemRepo.adjustStock(line.getItem().getId(), line.getQty());
            StockMovement mv = new StockMovement();
            mv.setItem(line.getItem());
            mv.setType(StockMovementType.GRN_RECEIPT);
            mv.setQuantity(line.getQty());
            mv.setReferenceId(grn.getId());
            mv.setReferenceType("GRN");
            movementRepo.save(mv);
        }

        grnRepo.save(grn);
        return ResponseEntity.ok(toResponse(grn));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrnResponse> reject(@PathVariable Long id,
                                              @RequestParam String reason,
                                              Authentication auth) {
        GoodsReceivedNote grn = grnRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found"));
        if (grn.getStatus() != GrnStatus.PENDING) {
            throw new IllegalArgumentException("GRN " + grn.getGrnNo() + " is already " + grn.getStatus());
        }
        grn.setStatus(GrnStatus.REJECTED);
        grn.setRejectionReason(reason);
        grn.setReviewedBy(auth.getName());
        grn.setReviewedAt(LocalDateTime.now());
        grnRepo.save(grn);
        return ResponseEntity.ok(toResponse(grn));
    }

    /** Admin correction on a note still awaiting approval -- quantity only. */
    @PatchMapping("/{grnId}/lines/{lineId}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrnResponse> updateLineQty(@PathVariable Long grnId,
                                                     @PathVariable Long lineId,
                                                     @RequestParam int qty) {
        GoodsReceivedNote grn = pendingGrn(grnId);
        if (qty < 1) throw new IllegalArgumentException("Quantity must be at least 1");

        GrnLine line = grn.getLines().stream()
                .filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Line not found on this GRN"));

        line.setQty(qty);
        line.setLineTotal(line.getUnitCost().multiply(BigDecimal.valueOf(qty))
                .setScale(2, RoundingMode.HALF_UP));

        grnRepo.save(grn);
        return ResponseEntity.ok(toResponse(grn));
    }

    @DeleteMapping("/{grnId}/lines/{lineId}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrnResponse> removeLine(@PathVariable Long grnId,
                                                  @PathVariable Long lineId) {
        GoodsReceivedNote grn = pendingGrn(grnId);
        if (grn.getLines().size() <= 1) {
            throw new IllegalArgumentException(
                    "A GRN needs at least one item - reject the whole note instead.");
        }
        boolean removed = grn.getLines().removeIf(l -> l.getId().equals(lineId));
        if (!removed) throw new EntityNotFoundException("Line not found on this GRN");

        grnRepo.save(grn);   // orphanRemoval deletes the row
        return ResponseEntity.ok(toResponse(grn));
    }

    /** Opening-stock notes owe nothing — admins can flag that either way. */
    @PatchMapping("/{id}/payment-required")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GrnResponse> setPaymentRequired(@PathVariable Long id,
                                                          @RequestParam boolean required) {
        GoodsReceivedNote grn = grnRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found"));
        grn.setPaymentRequired(required);
        grnRepo.save(grn);
        return ResponseEntity.ok(toResponse(grn));
    }

    /** Only a note that has not moved stock yet may be corrected. */
    private GoodsReceivedNote pendingGrn(Long id) {
        GoodsReceivedNote grn = grnRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found"));
        if (grn.getStatus() != GrnStatus.PENDING) {
            throw new IllegalArgumentException(
                    "GRN " + grn.getGrnNo() + " is " + grn.getStatus() + " - stock has already moved.");
        }
        return grn;
    }

    /** The catalog's own price: a flat wholesale price, else MRP less margin. */
    private BigDecimal catalogPrice(Item item) {
        BigDecimal price = item.getWholesalePrice() != null
                ? item.getWholesalePrice()
                : (item.getMrp() != null ? engine.computeWsp(item) : BigDecimal.ZERO);
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal categoryDiscount(CategoryType category) {
        return settingsRepo.findByKey("grn_discount_pct_" + category.name())
                .map(setting -> new BigDecimal(setting.getValue()))
                .orElse(BigDecimal.ZERO);
    }

    private GrnResponse toResponse(GoodsReceivedNote grn) {
        GrnResponse r = new GrnResponse();
        r.setId(grn.getId());
        r.setGrnNo(grn.getGrnNo());
        r.setCategory(grn.getCategory());
        r.setSupplierName(grn.getSupplierName());
        r.setReceivedDate(grn.getReceivedDate());
        r.setPaymentTermsDays(grn.getPaymentTermsDays());
        r.setDueDate(grn.getDueDate());
        r.setPaymentRequired(grn.getPaymentRequired());
        r.setStatus(grn.getStatus());
        r.setRejectionReason(grn.getRejectionReason());
        r.setNotes(grn.getNotes());
        r.setSubmittedBy(grn.getSubmittedBy());
        r.setReviewedBy(grn.getReviewedBy());
        r.setReviewedAt(grn.getReviewedAt());
        r.setCreatedAt(grn.getCreatedAt());

        BigDecimal discountPct = grn.getDiscountPct() != null ? grn.getDiscountPct() : BigDecimal.ZERO;
        r.setDiscountPct(discountPct);

        r.setLines(grn.getLines().stream().map(l -> {
            GrnResponse.GrnLineResponse lr = new GrnResponse.GrnLineResponse();
            lr.setId(l.getId());
            lr.setItemId(l.getItem().getId());
            lr.setItemCode(l.getItem().getItemCode());
            lr.setItemDescription(l.getItem().getDescription());
            lr.setBrandName(l.getItem().getBrand() != null ? l.getItem().getBrand().getName() : null);
            lr.setQty(l.getQty());
            lr.setUnitCost(l.getUnitCost());
            lr.setLineTotal(l.getLineTotal());
            lr.setNetTotal(applyDiscount(l.getLineTotal(), discountPct));
            return lr;
        }).toList());

        BigDecimal gross = grn.getLines().stream()
                .map(l -> l.getLineTotal() != null ? l.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Net sums the rounded line values, so the printed column actually adds up
        BigDecimal net = r.getLines().stream()
                .map(GrnResponse.GrnLineResponse::getNetTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        r.setTotalQty(grn.getLines().stream().mapToInt(GrnLine::getQty).sum());
        r.setTotalCost(gross);
        r.setNetTotal(net);
        r.setDiscountAmount(gross.subtract(net));
        return r;
    }

    private BigDecimal applyDiscount(BigDecimal lineTotal, BigDecimal discountPct) {
        if (lineTotal == null) return BigDecimal.ZERO;
        if (discountPct == null || discountPct.signum() == 0) {
            return lineTotal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                discountPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return lineTotal.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
