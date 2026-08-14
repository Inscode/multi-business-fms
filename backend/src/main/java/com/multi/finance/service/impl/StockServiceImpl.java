package com.multi.finance.service.impl;

import com.multi.finance.service.BillBalance;
import com.multi.finance.dto.request.CreateLinkingSystemBillRequest;
import com.multi.finance.dto.request.CreateStockBillRequest;
import com.multi.finance.dto.request.CreateSummaryLoadBillRequest;
import com.multi.finance.dto.request.IndividualStockReductionRequest;
import com.multi.finance.dto.request.LinkBillsRequest;
import com.multi.finance.dto.request.StockItemRequest;
import com.multi.finance.dto.response.BackorderItemResponse;
import com.multi.finance.dto.response.BillStockItemResponse;
import com.multi.finance.dto.response.BillStockStatusResponse;
import com.multi.finance.dto.response.StockBillResponse;
import com.multi.finance.dto.response.StockReductionPendingResponse;
import com.multi.finance.dto.response.StockReductionStatusResponse;
import com.multi.finance.dto.response.SummaryLoadBillResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockServiceImpl {

    private final BillRepository billRepository;
    private final ReturnProductRepository returnProductRepository;
    private final ShadowStockMovementRepository shadowStockMovementRepository;
    private final BillStockItemRepository billStockItemRepository;
    private final BillStockLinkRepository billStockLinkRepository;
    private final SummaryLoadBillRepository summaryLoadBillRepository;
    private final SummaryLoadBillItemRepository summaryLoadBillItemRepository;
    private final UserRepository userRepository;
    private final StockReductionPendingRepository stockReductionPendingRepository;

    // ─────────────────────────────────────────────────────────────
    // SUMMARY LOAD BILL
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public SummaryLoadBillResponse createSummaryLoadBill(CreateSummaryLoadBillRequest request) {
        User currentUser = getCurrentUser();

        List<Bill> systemBills = billRepository.findAllById(request.getSystemBillIds());
        if (systemBills.size() != request.getSystemBillIds().size()) {
            throw new RuntimeException("Some system bills not found");
        }

        SummaryLoadBill summaryLoadBill = SummaryLoadBill.builder()
                .systemBills(new HashSet<>(systemBills))
                .loadDate(request.getLoadDate())
                .notes(request.getNotes())
                .status(SummaryLoadBill.ApprovalStatus.PENDING)
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        SummaryLoadBill saved = summaryLoadBillRepository.save(summaryLoadBill);

        // Store items for later use at approval — NO stock movements yet
        for (StockItemRequest item : request.getItems()) {
            ReturnProduct product = returnProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

            BigDecimal itemUnitPrice = product.getUnitPrice();
            BigDecimal itemLineTotal = BigDecimal.valueOf(item.getQuantity()).multiply(itemUnitPrice);
            SummaryLoadBillItem lineItem = SummaryLoadBillItem.builder()
                    .summaryLoadBill(saved)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(itemUnitPrice)
                    .lineTotal(itemLineTotal)
                    .createdAt(LocalDateTime.now())
                    .build();
            summaryLoadBillItemRepository.save(lineItem);
        }

        return toSummaryLoadBillResponse(saved);
    }

    @Transactional
    public SummaryLoadBillResponse addItemToSummaryLoad(Long loadId, StockItemRequest req) {
        SummaryLoadBill load = summaryLoadBillRepository.findById(loadId)
            .orElseThrow(() -> new RuntimeException("Summary load not found"));
        if (load.getStatus() != SummaryLoadBill.ApprovalStatus.PENDING)
            throw new RuntimeException("Can only edit PENDING summary loads");
        ReturnProduct product = returnProductRepository.findById(req.getProductId())
            .orElseThrow(() -> new RuntimeException("Product not found"));
        BigDecimal lineTotal = BigDecimal.valueOf(req.getQuantity()).multiply(product.getUnitPrice());
        SummaryLoadBillItem item = SummaryLoadBillItem.builder()
            .summaryLoadBill(load)
            .product(product)
            .quantity(req.getQuantity())
            .unitPrice(product.getUnitPrice())
            .lineTotal(lineTotal)
            .createdAt(LocalDateTime.now())
            .build();
        summaryLoadBillItemRepository.save(item);
        return toSummaryLoadBillResponse(summaryLoadBillRepository.findById(loadId).get());
    }

    @Transactional
    public SummaryLoadBillResponse updateSummaryLoadItem(Long loadId, Long itemId, Long quantity) {
        SummaryLoadBillItem item = summaryLoadBillItemRepository.findByIdAndSummaryLoadBillId(itemId, loadId)
            .orElseThrow(() -> new RuntimeException("Item not found"));
        if (item.getSummaryLoadBill().getStatus() != SummaryLoadBill.ApprovalStatus.PENDING)
            throw new RuntimeException("Can only edit PENDING summary loads");
        if (quantity <= 0) throw new RuntimeException("Quantity must be positive");
        item.setQuantity(quantity);
        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : item.getProduct().getUnitPrice();
        item.setLineTotal(BigDecimal.valueOf(quantity).multiply(unitPrice));
        summaryLoadBillItemRepository.save(item);
        return toSummaryLoadBillResponse(summaryLoadBillRepository.findById(loadId).get());
    }

    @Transactional
    public void deleteSummaryLoadItem(Long loadId, Long itemId) {
        SummaryLoadBillItem item = summaryLoadBillItemRepository.findByIdAndSummaryLoadBillId(itemId, loadId)
            .orElseThrow(() -> new RuntimeException("Item not found"));
        if (item.getSummaryLoadBill().getStatus() != SummaryLoadBill.ApprovalStatus.PENDING)
            throw new RuntimeException("Can only edit PENDING summary loads");
        summaryLoadBillItemRepository.delete(item);
    }

    @Transactional
    public void approveSummaryLoadBill(Long summaryLoadBillId) {
        User currentUser = getCurrentUser();

        SummaryLoadBill summaryLoadBill = summaryLoadBillRepository.findById(summaryLoadBillId)
                .orElseThrow(() -> new RuntimeException("Summary load bill not found"));

        if (summaryLoadBill.getStatus() != SummaryLoadBill.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only PENDING summary load bills can be approved");
        }

        summaryLoadBill.setStatus(SummaryLoadBill.ApprovalStatus.APPROVED);
        summaryLoadBill.setApprovedBy(currentUser);
        summaryLoadBill.setApprovedAt(LocalDateTime.now());
        summaryLoadBill.setUpdatedAt(LocalDateTime.now());
        summaryLoadBillRepository.save(summaryLoadBill);

        // NOW create stock movements — deduction happens only on approval
        List<SummaryLoadBillItem> items = summaryLoadBillItemRepository.findBySummaryLoadBillId(summaryLoadBillId);
        for (SummaryLoadBillItem item : items) {
            createShadowStockMovement(
                    item.getProduct(),
                    item.getQuantity(),
                    ShadowStockMovement.MovementType.BILL_OUT,
                    null,
                    "LOAD-" + summaryLoadBillId,
                    currentUser
            );
        }
    }

    @Transactional
    public void rejectSummaryLoadBill(Long summaryLoadBillId) {
        SummaryLoadBill summaryLoadBill = summaryLoadBillRepository.findById(summaryLoadBillId)
                .orElseThrow(() -> new RuntimeException("Summary load bill not found"));

        if (summaryLoadBill.getStatus() != SummaryLoadBill.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only PENDING summary load bills can be rejected");
        }

        // No stock movements were created at save time, so nothing to reverse
        summaryLoadBill.setStatus(SummaryLoadBill.ApprovalStatus.REJECTED);
        summaryLoadBill.setUpdatedAt(LocalDateTime.now());
        summaryLoadBillRepository.save(summaryLoadBill);
    }

    @Transactional(readOnly = true)
    public List<SummaryLoadBillResponse> getAllSummaryLoadBills() {
        return summaryLoadBillRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummaryLoadBillResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // INDIVIDUAL STOCK REDUCTION (Stock Item Entry)
    // ─────────────────────────────────────────────────────────────

    /**
     * Submit an individual stock reduction request for admin approval.
     * For SYSTEM linking bills: saves BillStockItems as reference only (no approval needed).
     * For all other bills: creates a PENDING request that admin must approve.
     */
    @Transactional
    public void reduceStockForBill(IndividualStockReductionRequest request) {
        User currentUser = getCurrentUser();

        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new RuntimeException("Bill not found: " + request.getBillId()));

        // SYSTEM linking bills: save reference items only — no approval needed
        boolean isLinkingBill = Boolean.TRUE.equals(bill.getWillBeLinked())
                || !billStockLinkRepository.findBySystemBillId(bill.getId()).isEmpty();
        if (isLinkingBill) {
            enterReferenceItemsForSystemBill(bill.getId(), request.getItems());
            return;
        }

        // Guard: already individually reduced?
        List<ShadowStockMovement> existing = shadowStockMovementRepository.findByBillId(bill.getId());
        if (!existing.isEmpty()) {
            throw new RuntimeException("Stock has already been reduced for this bill");
        }

        // Guard: already has a pending or approved reduction request?
        if (stockReductionPendingRepository.existsByBillIdAndStatus(bill.getId(), StockReductionPending.ApprovalStatus.PENDING)) {
            throw new RuntimeException("A pending reduction request already exists for this bill");
        }

        // Build pending request with items
        StockReductionPending pending = StockReductionPending.builder()
                .bill(bill)
                .submittedBy(currentUser)
                .submittedAt(LocalDateTime.now())
                .notes(request.getNotes())
                .build();

        for (StockItemRequest item : request.getItems()) {
            ReturnProduct product = returnProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

            BigDecimal unitPrice = (item.isBackorder() && item.getPredictedUnitPrice() != null)
                    ? item.getPredictedUnitPrice()
                    : product.getUnitPrice();
            BigDecimal lineTotal = BigDecimal.valueOf(item.getQuantity()).multiply(unitPrice);
            StockReductionPendingItem pendingItem = StockReductionPendingItem.builder()
                    .reduction(pending)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .backorder(item.isBackorder())
                    .predictedUnitPrice(item.isBackorder() ? item.getPredictedUnitPrice() : null)
                    .createdAt(LocalDateTime.now())
                    .build();
            pending.getItems().add(pendingItem);
        }

        stockReductionPendingRepository.save(pending);
    }

    @Transactional(readOnly = true)
    public List<StockReductionPendingResponse> getPendingIndividualReductions() {
        return stockReductionPendingRepository.findAllByOrderBySubmittedAtDesc().stream()
                .map(this::toReductionPendingResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void approveIndividualReduction(Long id) {
        User currentUser = getCurrentUser();

        StockReductionPending pending = stockReductionPendingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reduction request not found: " + id));

        if (pending.getStatus() != StockReductionPending.ApprovalStatus.PENDING) {
            throw new RuntimeException("Request is not in PENDING status");
        }

        // Separate items into regular and backorder lists
        List<StockReductionPendingItem> regularItems = pending.getItems().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getBackorder()))
                .collect(Collectors.toList());
        List<StockReductionPendingItem> backorderItems = pending.getItems().stream()
                .filter(i -> Boolean.TRUE.equals(i.getBackorder()))
                .collect(Collectors.toList());

        // Stock check for non-backorder items (bulk, no N+1)
        if (!regularItems.isEmpty()) {
            List<Long> productIds = regularItems.stream()
                    .map(i -> i.getProduct().getId())
                    .distinct()
                    .collect(Collectors.toList());

            // Aggregate required qty per product (in case same product appears multiple times)
            Map<Long, Long> requiredQty = new HashMap<>();
            for (StockReductionPendingItem item : regularItems) {
                requiredQty.merge(item.getProduct().getId(), item.getQuantity(), Long::sum);
            }

            Map<Long, Long> availableBalances = new HashMap<>();
            shadowStockMovementRepository.getAvailableBalancesForProducts(productIds).forEach(row -> {
                Long productId = ((Number) row[0]).longValue();
                Long balance = ((Number) row[1]).longValue();
                availableBalances.put(productId, balance);
            });

            // Build name map for error messages
            Map<Long, String> productNames = new HashMap<>();
            for (StockReductionPendingItem item : regularItems) {
                productNames.put(item.getProduct().getId(), item.getProduct().getName());
            }

            List<String> shortages = new ArrayList<>();
            for (Map.Entry<Long, Long> entry : requiredQty.entrySet()) {
                Long productId = entry.getKey();
                Long required = entry.getValue();
                Long available = availableBalances.getOrDefault(productId, 0L);
                if (available < required) {
                    shortages.add(productNames.get(productId) +
                            " (available: " + available + ", required: " + required + ")");
                }
            }
            if (!shortages.isEmpty()) {
                throw new RuntimeException("Insufficient stock for: " + String.join(", ", shortages));
            }
        }

        Bill bill = pending.getBill();

        // Process regular items: create BillStockItem + BILL_OUT movement
        for (StockReductionPendingItem pendingItem : regularItems) {
            ReturnProduct product = pendingItem.getProduct();
            billStockItemRepository.save(BillStockItem.builder()
                    .bill(bill)
                    .product(product)
                    .quantity(pendingItem.getQuantity())
                    .unitPrice(pendingItem.getUnitPrice())
                    .lineTotal(pendingItem.getLineTotal())
                    .backorder(false)
                    .createdAt(LocalDateTime.now())
                    .build());
            createShadowStockMovement(product, pendingItem.getQuantity(),
                    ShadowStockMovement.MovementType.BILL_OUT, bill, null, currentUser);
        }

        // Process backorder items: create BillStockItem with backorder=true, NO BILL_OUT
        for (StockReductionPendingItem pendingItem : backorderItems) {
            ReturnProduct product = pendingItem.getProduct();
            billStockItemRepository.save(BillStockItem.builder()
                    .bill(bill)
                    .product(product)
                    .quantity(pendingItem.getQuantity())
                    .unitPrice(pendingItem.getUnitPrice())
                    .lineTotal(pendingItem.getLineTotal())
                    .backorder(true)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        pending.setStatus(StockReductionPending.ApprovalStatus.APPROVED);
        pending.setReviewedBy(currentUser);
        pending.setReviewedAt(LocalDateTime.now());
        stockReductionPendingRepository.save(pending);
    }

    @Transactional
    public void rejectIndividualReduction(Long id, String rejectionReason) {
        User currentUser = getCurrentUser();

        StockReductionPending pending = stockReductionPendingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reduction request not found: " + id));

        if (pending.getStatus() != StockReductionPending.ApprovalStatus.PENDING) {
            throw new RuntimeException("Request is not in PENDING status");
        }

        pending.setStatus(StockReductionPending.ApprovalStatus.REJECTED);
        pending.setReviewedBy(currentUser);
        pending.setReviewedAt(LocalDateTime.now());
        pending.setRejectionReason(rejectionReason);
        stockReductionPendingRepository.save(pending);
    }

    @Transactional
    public void markSavingsCollected(Long billId, boolean collected) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        bill.setSavingsCollected(collected);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    private StockReductionPendingResponse toReductionPendingResponse(StockReductionPending r) {
        List<StockReductionPendingResponse.ItemDto> items = r.getItems().stream()
                .map(i -> StockReductionPendingResponse.ItemDto.builder()
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .lineTotal(i.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        Bill bill = r.getBill();
        return StockReductionPendingResponse.builder()
                .id(r.getId())
                .billId(bill.getId())
                .billNumber(bill.getBillNumber())
                .customerName(bill.getCustomerName())
                .status(r.getStatus().name())
                .submittedByName(r.getSubmittedBy() != null ? r.getSubmittedBy().getFullName() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .submittedAt(r.getSubmittedAt())
                .reviewedAt(r.getReviewedAt())
                .notes(r.getNotes())
                .rejectionReason(r.getRejectionReason())
                .items(items)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // STOCK REDUCTION STATUS TRACKING
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StockReductionStatusResponse> getStockReductionStatus() {
        List<Bill> allBills = billRepository.findByBusinessOrderByCreatedAtDesc(BusinessType.RAINCO)
                .stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED)
                .collect(Collectors.toList());

        Map<Long, SummaryLoadBill> billToSummary = new HashMap<>();
        summaryLoadBillRepository.findAllByOrderByCreatedAtDesc().forEach(slb ->
            slb.getSystemBills().forEach(b -> billToSummary.put(b.getId(), slb))
        );

        Set<Long> individuallyReduced = shadowStockMovementRepository.findBillIdsWithActiveMovements();
        Set<Long> pendingReductionBillIds = stockReductionPendingRepository.findBillIdsWithPendingReduction();

        // Batch-load all links to compute savings without N+1
        List<BillStockLink> allLinks = billStockLinkRepository.findAllWithBills();
        Map<Long, List<BillStockLink>> linksBySystemBill = allLinks.stream()
                .collect(Collectors.groupingBy(l -> l.getSystemBill().getId()));

        Set<Long> linkedSystemBillIds = linksBySystemBill.keySet();
        Set<Long> linkedChildBillIds = allLinks.stream()
                .map(l -> l.getChildBill().getId()).collect(Collectors.toSet());

        // Children total amounts per system bill (for savings)
        Map<Long, BigDecimal> childrenTotals = new HashMap<>();
        linksBySystemBill.forEach((sysId, links) -> {
            BigDecimal total = links.stream()
                    .map(l -> l.getChildBill().getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            childrenTotals.put(sysId, total);
        });

        return allBills.stream().map(bill -> {
            String status;
            Long summaryId = null;
            BigDecimal childrenTotal = null;
            BigDecimal savings = null;

            if (linkedSystemBillIds.contains(bill.getId())) {
                status = Boolean.TRUE.equals(bill.getStockReconciled()) ? "RECONCILED" : "LINKED";
                childrenTotal = childrenTotals.getOrDefault(bill.getId(), BigDecimal.ZERO);
                savings = childrenTotal.subtract(bill.getTotalAmount());
            } else if (linkedChildBillIds.contains(bill.getId())) {
                status = "LINKED";
            } else if (Boolean.TRUE.equals(bill.getWillBeLinked())) {
                status = "WILL_LINK";
            } else if (pendingReductionBillIds.contains(bill.getId())) {
                status = "REDUCTION_PENDING";
            } else if (individuallyReduced.contains(bill.getId()) || Boolean.TRUE.equals(bill.getStockCleared())) {
                status = "INDIVIDUALLY_REDUCED";
            } else if (billToSummary.containsKey(bill.getId())) {
                SummaryLoadBill slb = billToSummary.get(bill.getId());
                summaryId = slb.getId();
                status = switch (slb.getStatus()) {
                    case APPROVED -> "SUMMARY_APPROVED";
                    case REJECTED -> "NOT_REDUCED";
                    default -> "SUMMARY_PENDING";
                };
            } else {
                status = "NOT_REDUCED";
            }

            return StockReductionStatusResponse.builder()
                    .billId(bill.getId())
                    .billNumber(bill.getBillNumber())
                    .billSource(bill.getBillSource().name())
                    .customerName(bill.getCustomerName())
                    .amount(bill.getTotalAmount())
                    .balanceRemaining(bill.getBalanceRemaining())
                    .billDate(bill.getBillDate())
                    .reductionStatus(status)
                    .summaryLoadBillId(summaryId)
                    .enteredByName(bill.getEnteredBy() != null ? bill.getEnteredBy().getFullName() : null)
                    .stockReconciled(bill.getStockReconciled())
                    .childrenTotalAmount(childrenTotal)
                    .savingsAmount(savings)
                    .build();
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // PER-BILL STOCK STATUS
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BillStockStatusResponse getBillStockStatus(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        List<BillStockItemResponse> ownItems = getBillItems(billId);
        String reductionStatus = computeReductionStatus(bill);

        BillStockStatusResponse.BillStockStatusResponseBuilder builder = BillStockStatusResponse.builder()
                .billId(bill.getId())
                .billNumber(bill.getBillNumber())
                .billSource(bill.getBillSource().name())
                .reductionStatus(reductionStatus)
                .ownItems(ownItems)
                .linkedChildren(Collections.emptyList());

        // Summary load context
        summaryLoadBillRepository.findBySystemBillId(billId).ifPresent(slb -> {
            List<BillStockStatusResponse.SummaryItemInfo> summaryItems =
                    summaryLoadBillItemRepository.findBySummaryLoadBillId(slb.getId()).stream()
                            .map(item -> BillStockStatusResponse.SummaryItemInfo.builder()
                                    .productId(item.getProduct().getId())
                                    .productName(item.getProduct().getName())
                                    .quantity(item.getQuantity())
                                    .build())
                            .collect(Collectors.toList());

            List<BillStockStatusResponse.SiblingBillInfo> siblings = slb.getSystemBills().stream()
                    .filter(b -> !b.getId().equals(billId))
                    .map(b -> BillStockStatusResponse.SiblingBillInfo.builder()
                            .billId(b.getId())
                            .billNumber(b.getBillNumber())
                            .customerName(b.getCustomerName())
                            .amount(b.getTotalAmount())
                            .totalQty(billStockItemRepository.findByBillId(b.getId()).stream()
                                    .mapToLong(BillStockItem::getQuantity).sum())
                            .build())
                    .collect(Collectors.toList());

            builder.summaryLoadId(slb.getId())
                   .summaryStatus(slb.getStatus().name())
                   .summaryCreatedByName(slb.getCreatedBy().getFullName())
                   .summaryLoadDate(slb.getLoadDate())
                   .summaryItems(summaryItems)
                   .summaryRelatedBills(siblings);
        });

        // Reconciliation fields
        builder.stockReconciled(Boolean.TRUE.equals(bill.getStockReconciled()));

        // Linked children (this is a SYSTEM bill with linked DRAFT/MANUAL children)
        List<BillStockLink> childLinks = billStockLinkRepository.findBySystemBillId(billId);
        if (!childLinks.isEmpty()) {
            List<BillStockStatusResponse.LinkedChildInfo> children = childLinks.stream()
                    .map(link -> {
                        Bill child = link.getChildBill();
                        return BillStockStatusResponse.LinkedChildInfo.builder()
                                .billId(child.getId())
                                .billNumber(child.getBillNumber())
                                .billSource(child.getBillSource().name())
                                .customerName(child.getCustomerName())
                                .amount(child.getTotalAmount())
                                .items(getBillItems(child.getId()))
                                .linkedByName(link.getLinkedBy().getFullName())
                                .linkedAt(link.getLinkedAt())
                                .notes(link.getNotes())
                                .build();
                    })
                    .collect(Collectors.toList());
            builder.linkedChildren(children);

            // Savings = system bill amount - sum of all children amounts
            BigDecimal childrenTotal = children.stream()
                    .map(BillStockStatusResponse.LinkedChildInfo::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            builder.childrenTotalAmount(childrenTotal);
            builder.savingsAmount(childrenTotal.subtract(bill.getTotalAmount()));
            builder.savingsCollected(Boolean.TRUE.equals(bill.getSavingsCollected()));

            // Aggregate comparison: system bill reference items vs sum of all children items
            Map<Long, String> nameMap = new HashMap<>();
            Map<Long, Long> childAggQty = new HashMap<>();
            for (BillStockStatusResponse.LinkedChildInfo child : children) {
                for (BillStockItemResponse item : child.getItems()) {
                    nameMap.put(item.getProductId(), item.getProductName());
                    childAggQty.merge(item.getProductId(), item.getQuantity(), Long::sum);
                }
            }
            Map<Long, Long> sysRefQty = new HashMap<>();
            for (BillStockItemResponse item : ownItems) {
                nameMap.put(item.getProductId(), item.getProductName());
                sysRefQty.merge(item.getProductId(), item.getQuantity(), Long::sum);
            }
            Set<Long> aggProducts = new HashSet<>();
            aggProducts.addAll(childAggQty.keySet());
            aggProducts.addAll(sysRefQty.keySet());

            if (!aggProducts.isEmpty()) {
                List<BillStockStatusResponse.ItemComparisonRow> aggComparison = aggProducts.stream()
                        .map(pid -> {
                            long sq = sysRefQty.getOrDefault(pid, 0L);
                            long cq = childAggQty.getOrDefault(pid, 0L);
                            return BillStockStatusResponse.ItemComparisonRow.builder()
                                    .productId(pid)
                                    .productName(nameMap.get(pid))
                                    .systemQty(sq)
                                    .childQty(cq)
                                    .diff(cq - sq)
                                    .build();
                        })
                        .sorted(Comparator.comparing(BillStockStatusResponse.ItemComparisonRow::getProductName))
                        .collect(Collectors.toList());
                builder.childrenAggregateComparison(aggComparison);
                // quantitiesMatch = all rows have diff==0 AND system items exist
                boolean allMatch = !ownItems.isEmpty() &&
                        aggComparison.stream().allMatch(r -> r.getDiff() == 0);
                builder.quantitiesMatch(allMatch);
            }
        }

        // Linked parent (this DRAFT/MANUAL is linked to a SYSTEM parent)
        List<BillStockLink> parentLinks = billStockLinkRepository.findByChildBillId(billId);
        if (!parentLinks.isEmpty()) {
            BillStockLink link = parentLinks.get(0);
            Bill systemBill = link.getSystemBill();
            List<BillStockItemResponse> systemItems = getBillItems(systemBill.getId());

            // Build item-wise comparison: this child bill vs system bill
            Map<Long, String> nameMap = new HashMap<>();
            Map<Long, Long> sysQtyMap = new HashMap<>();
            for (BillStockItemResponse i : systemItems) {
                nameMap.put(i.getProductId(), i.getProductName());
                sysQtyMap.merge(i.getProductId(), i.getQuantity(), Long::sum);
            }
            Map<Long, Long> childQtyMap = new HashMap<>();
            for (BillStockItemResponse i : ownItems) {
                nameMap.put(i.getProductId(), i.getProductName());
                childQtyMap.merge(i.getProductId(), i.getQuantity(), Long::sum);
            }
            Set<Long> allProducts = new HashSet<>();
            allProducts.addAll(sysQtyMap.keySet());
            allProducts.addAll(childQtyMap.keySet());

            List<BillStockStatusResponse.ItemComparisonRow> comparison = allProducts.stream()
                    .map(pid -> {
                        long sq = sysQtyMap.getOrDefault(pid, 0L);
                        long cq = childQtyMap.getOrDefault(pid, 0L);
                        return BillStockStatusResponse.ItemComparisonRow.builder()
                                .productId(pid)
                                .productName(nameMap.get(pid))
                                .systemQty(sq)
                                .childQty(cq)
                                .diff(cq - sq)
                                .build();
                    })
                    .sorted(Comparator.comparing(BillStockStatusResponse.ItemComparisonRow::getProductName))
                    .collect(Collectors.toList());

            builder.linkedParent(BillStockStatusResponse.LinkedParentInfo.builder()
                    .billId(systemBill.getId())
                    .billNumber(systemBill.getBillNumber())
                    .customerName(systemBill.getCustomerName())
                    .amount(systemBill.getTotalAmount())
                    .systemItems(systemItems)
                    .comparison(comparison)
                    .linkedByName(link.getLinkedBy().getFullName())
                    .linkedAt(link.getLinkedAt())
                    .notes(link.getNotes())
                    .build());
        }

        return builder.build();
    }

    /**
     * Admin marks a SYSTEM linking bill as stock-reconciled.
     * Requires the bill to have at least one linked child.
     */
    @Transactional
    public void markStockReconciled(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        if (bill.getBillSource() != BillSource.SYSTEM) {
            throw new RuntimeException("Only SYSTEM bills can be marked as stock-reconciled");
        }
        if (billStockLinkRepository.findBySystemBillId(billId).isEmpty()) {
            throw new RuntimeException("Bill has no linked child bills — link first before reconciling");
        }
        bill.setStockReconciled(true);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    /**
     * Enter reference items on a SYSTEM linking bill for reconciliation.
     * Saves BillStockItems only — no shadow stock movements are created
     * because stock was already reduced through the linked DRAFT/MANUAL bills.
     */
    @Transactional
    public void enterReferenceItemsForSystemBill(Long billId, List<StockItemRequest> items) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        if (bill.getBillSource() != BillSource.SYSTEM) {
            throw new RuntimeException("Reference items can only be entered for SYSTEM bills");
        }
        List<BillStockLink> childLinks = billStockLinkRepository.findBySystemBillId(billId);
        // Allow reference entry if flagged willBeLinked (children may not be linked yet)
        if (childLinks.isEmpty() && !Boolean.TRUE.equals(bill.getWillBeLinked())) {
            throw new RuntimeException("This SYSTEM bill has no linked child bills");
        }

        // Clear existing reference items (allow re-entry / correction)
        billStockItemRepository.deleteAll(billStockItemRepository.findByBillId(billId));

        for (StockItemRequest item : items) {
            ReturnProduct product = returnProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));
            billStockItemRepository.save(BillStockItem.builder()
                    .bill(bill)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(product.getUnitPrice())
                    .lineTotal(BigDecimal.valueOf(item.getQuantity()).multiply(product.getUnitPrice()))
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<BillStockItemResponse> getBillItems(Long billId) {
        return billStockItemRepository.findByBillId(billId).stream()
                .map(item -> BillStockItemResponse.builder()
                        .id(item.getId())
                        .billId(item.getBill().getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Admin: delete an entered stock item.
     * Also cancels the corresponding BILL_OUT shadow movement (if any).
     * Reference items on SYSTEM linking bills have no shadow movements — safe to delete directly.
     */
    @Transactional
    public void deleteStockItem(Long itemId) {
        BillStockItem item = billStockItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Stock item not found: " + itemId));

        shadowStockMovementRepository
                .findByBillIdAndProductIdAndCancelledFalse(item.getBill().getId(), item.getProduct().getId())
                .forEach(m -> {
                    m.setCancelled(true);
                    m.setUpdatedAt(LocalDateTime.now());
                    shadowStockMovementRepository.save(m);
                });

        billStockItemRepository.delete(item);
    }

    /**
     * Admin: update the quantity of an entered stock item.
     * Also adjusts the corresponding BILL_OUT shadow movement quantity.
     */
    @Transactional
    public BillStockItemResponse updateStockItemQuantity(Long itemId, Long newQuantity) {
        if (newQuantity == null || newQuantity <= 0) {
            throw new RuntimeException("Quantity must be greater than zero");
        }
        BillStockItem item = billStockItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Stock item not found: " + itemId));

        item.setQuantity(newQuantity);
        item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(newQuantity)));
        item.setUpdatedAt(LocalDateTime.now());
        billStockItemRepository.save(item);

        shadowStockMovementRepository
                .findByBillIdAndProductIdAndCancelledFalse(item.getBill().getId(), item.getProduct().getId())
                .stream().findFirst()
                .ifPresent(m -> {
                    m.setQuantity(newQuantity);
                    m.setUpdatedAt(LocalDateTime.now());
                    shadowStockMovementRepository.save(m);
                });

        return BillStockItemResponse.builder()
                .id(item.getId())
                .billId(item.getBill().getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .lineTotal(item.getLineTotal())
                .build();
    }

    private String computeReductionStatus(Bill bill) {
        List<ShadowStockMovement> movements = shadowStockMovementRepository.findByBillId(bill.getId());
        if (movements.stream().anyMatch(m -> !m.getCancelled())) {
            return "INDIVIDUALLY_REDUCED";
        }
        Optional<SummaryLoadBill> summary = summaryLoadBillRepository.findBySystemBillId(bill.getId());
        if (summary.isPresent()) {
            return switch (summary.get().getStatus()) {
                case APPROVED -> "SUMMARY_APPROVED";
                case REJECTED -> "NOT_REDUCED";
                default -> "SUMMARY_PENDING";
            };
        }
        if (billStockLinkRepository.existsByChildBillId(bill.getId())) return "LINKED";
        if (!billStockLinkRepository.findBySystemBillId(bill.getId()).isEmpty()) return "LINKED";
        if (stockReductionPendingRepository.existsByBillIdAndStatus(bill.getId(), StockReductionPending.ApprovalStatus.PENDING)) {
            return "REDUCTION_PENDING";
        }
        return "NOT_REDUCED";
    }

    // ─────────────────────────────────────────────────────────────
    // QUERY HELPERS (for dropdowns / bill lists)
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StockBillResponse> getUnassignedSystemBills() {
        return billRepository.findUnassignedSystemBills().stream()
                .map(this::toStockBillResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockBillResponse> getUnlinkedDraftManualBills() {
        return billRepository.findUnlinkedDraftManualBills().stream()
                .map(this::toStockBillResponseWithItems)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockBillResponse> getAvailableSystemBillsForLinking() {
        return billRepository.findAvailableSystemBillsForLinking().stream()
                .map(this::toStockBillResponseWithItems)
                .collect(Collectors.toList());
    }

    /**
     * Bills available for stock item entry.
     * Includes SYSTEM linking bills that have no reference items yet — they appear with isLinkingBill=true
     * so the UI can show a "reference only" label. Saving items for them skips stock movements.
     */
    @Transactional(readOnly = true)
    public List<StockBillResponse> getBillsNotYetReduced() {
        List<Bill> allBills = billRepository.findByBusinessOrderByCreatedAtDesc(BusinessType.RAINCO);

        // Bills already in a summary load (any status except REJECTED)
        Set<Long> inSummary = new HashSet<>();
        summaryLoadBillRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(slb -> slb.getStatus() != SummaryLoadBill.ApprovalStatus.REJECTED)
                .forEach(slb -> slb.getSystemBills().forEach(b -> inSummary.add(b.getId())));

        // Bills already individually reduced (have non-cancelled shadow movements)
        Set<Long> individuallyReduced = shadowStockMovementRepository.findBillIdsWithActiveMovements();

        // SYSTEM linking bills (have linked DRAFT/MANUAL children)
        Set<Long> linkedSystemBills = billRepository.findLinkedSystemBills()
                .stream().map(Bill::getId).collect(Collectors.toSet());

        // Linking bills that already have reference items entered — exclude them
        Set<Long> linkingBillsWithItems = linkedSystemBills.stream()
                .filter(id -> !billStockItemRepository.findByBillId(id).isEmpty())
                .collect(Collectors.toSet());

        // willBeLinked bills without items yet still need reference item entry — include them
        Set<Long> willBeLinkedWithoutItems = allBills.stream()
                .filter(b -> Boolean.TRUE.equals(b.getWillBeLinked())
                          && billStockItemRepository.findByBillId(b.getId()).isEmpty())
                .map(Bill::getId)
                .collect(Collectors.toSet());

        return allBills.stream()
                .filter(b -> !inSummary.contains(b.getId())
                          && !individuallyReduced.contains(b.getId())
                          && !linkingBillsWithItems.contains(b.getId())
                          && (!Boolean.TRUE.equals(b.getWillBeLinked()) || willBeLinkedWithoutItems.contains(b.getId())))
                .map(b -> toStockBillResponseWithFlag(b,
                        linkedSystemBills.contains(b.getId()) || Boolean.TRUE.equals(b.getWillBeLinked())))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // EXISTING (unchanged)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Bill createStockBill(CreateStockBillRequest request) {
        User currentUser = getCurrentUser();

        if (request.getItems().isEmpty()) {
            throw new RuntimeException("Bill must have at least one item");
        }

        Bill bill = Bill.builder()
                .business(BusinessType.RAINCO)
                .division("STORE")
                .billType(com.multi.finance.enums.BillType.CASH)
                .billSource(request.getBillSource())
                .customerName(request.getCustomerName())
                .billNumber(generateBillNumber(request.getBillSource()))
                .area(request.getArea())
                .totalAmount(request.getActualAmount() != null ? request.getActualAmount() : request.getCalculatedAmount())
                .amountPaid(BigDecimal.ZERO)
                .balanceRemaining(request.getActualAmount() != null ? request.getActualAmount() : request.getCalculatedAmount())
                .fullyPaid(false)
                .status(com.multi.finance.enums.BillStatus.CREATED)
                .enteredBy(currentUser)
                .billDate(request.getBillDate() != null ? request.getBillDate() : LocalDate.now())
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Bill savedBill = billRepository.save(bill);

        for (StockItemRequest item : request.getItems()) {
            ReturnProduct product = returnProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProductId()));

            BigDecimal lineTotal = BigDecimal.valueOf(item.getQuantity()).multiply(product.getUnitPrice());

            BillStockItem billItem = BillStockItem.builder()
                    .bill(savedBill)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(product.getUnitPrice())
                    .lineTotal(lineTotal)
                    .createdAt(LocalDateTime.now())
                    .build();
            billStockItemRepository.save(billItem);
        }

        return savedBill;
    }

    // ─────────────────────────────────────────────────────────────
    // LINKING SYSTEM BILL CREATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a SYSTEM bill pre-flagged as willBeLinked=true.
     * Stock is covered by the linked DRAFT/MANUAL children — no BILL_OUT movement will be created for this bill.
     */
    @Transactional
    public StockBillResponse createLinkingSystemBill(CreateLinkingSystemBillRequest request) {
        User currentUser = getCurrentUser();

        Bill bill = Bill.builder()
                .business(BusinessType.RAINCO)
                .division("STORE")
                .billType(com.multi.finance.enums.BillType.CASH)
                .billSource(BillSource.SYSTEM)
                .customerName(request.getCustomerName())
                .billNumber(request.getBillNumber().startsWith("SYS-") ? request.getBillNumber() : "SYS-" + request.getBillNumber())
                .totalAmount(request.getAmount())
                .amountPaid(BigDecimal.ZERO)
                .balanceRemaining(request.getAmount())
                .fullyPaid(false)
                .status(com.multi.finance.enums.BillStatus.CREATED)
                .enteredBy(currentUser)
                .billDate(request.getBillDate() != null ? request.getBillDate() : LocalDate.now())
                .notes(request.getNotes())
                .willBeLinked(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toStockBillResponse(billRepository.save(bill));
    }

    // ─────────────────────────────────────────────────────────────
    // UNLINKED DRAFT/MANUAL DASHBOARD
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns ALL draft/manual bills with their linking status.
     * Used for the "Pending Linking" dashboard in End of Month Linking tab.
     */
    @Transactional(readOnly = true)
    public List<StockBillResponse> getUnlinkedDraftManualDashboard() {
        // Bills that are already linked (child in BillStockLink)
        Set<Long> linkedChildIds = billRepository.findLinkedChildBills()
                .stream().map(Bill::getId).collect(Collectors.toSet());

        return billRepository.findAllDraftManualBills().stream()
                .filter(b -> !linkedChildIds.contains(b.getId())) // only unlinked
                .map(this::toStockBillResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void linkBills(LinkBillsRequest request) {
        User currentUser = getCurrentUser();

        List<Bill> childBills = billRepository.findAllById(request.getDraftManualBillIds());
        if (childBills.size() != request.getDraftManualBillIds().size()) {
            throw new RuntimeException("Some bills not found");
        }

        for (Bill bill : childBills) {
            if (!Arrays.asList(BillSource.DRAFT, BillSource.MANUAL).contains(bill.getBillSource())) {
                throw new RuntimeException("Only DRAFT and MANUAL bills can be linked");
            }
        }

        Bill systemBill;
        if (request.getSystemBillId() != null) {
            systemBill = billRepository.findById(request.getSystemBillId())
                    .orElseThrow(() -> new RuntimeException("System bill not found"));
            if (systemBill.getBillSource() != BillSource.SYSTEM) {
                throw new RuntimeException("Selected bill is not a system bill");
            }
        } else if (request.getSystemBillNumber() != null) {
            systemBill = Bill.builder()
                    .business(BusinessType.RAINCO)
                    .division("STORE")
                    .billType(com.multi.finance.enums.BillType.CASH)
                    .billSource(BillSource.SYSTEM)
                    .customerName("System Bill")
                    .billNumber("SYS-" + request.getSystemBillNumber())
                    .totalAmount(BigDecimal.ZERO)
                    .amountPaid(BigDecimal.ZERO)
                    .balanceRemaining(BigDecimal.ZERO)
                    .fullyPaid(false)
                    .status(com.multi.finance.enums.BillStatus.CREATED)
                    .enteredBy(getCurrentUser())
                    .billDate(LocalDate.now())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            systemBill = billRepository.save(systemBill);
        } else {
            throw new RuntimeException("Either system bill ID or number must be provided");
        }

        for (Bill childBill : childBills) {
            BillStockLink link = BillStockLink.builder()
                    .systemBill(systemBill)
                    .childBill(childBill)
                    .linkedBy(currentUser)
                    .linkedAt(LocalDateTime.now())
                    .notes(request.getNotes())
                    .createdAt(LocalDateTime.now())
                    .build();
            billStockLinkRepository.save(link);
            // Stock movements on DRAFT/MANUAL bills are preserved — stock was already reduced
            // when those bills were processed. The SYSTEM bill is linked purely for reconciliation.
        }
    }

    public Map<Long, Long> getShadowStockBalance(BusinessType business) {
        List<ReturnProduct> products = returnProductRepository.findByBusiness(business);
        Map<Long, Long> balances = new HashMap<>();
        for (ReturnProduct product : products) {
            Long balance = shadowStockMovementRepository.getAvailableBalance(product);
            balances.put(product.getId(), balance != null ? balance : 0L);
        }
        return balances;
    }

    public Map<Long, Long> getDamageStockBalance(BusinessType business) {
        List<ReturnProduct> products = returnProductRepository.findByBusiness(business);
        Map<Long, Long> balances = new HashMap<>();
        for (ReturnProduct product : products) {
            Long balance = shadowStockMovementRepository.getDamageBalance(product);
            balances.put(product.getId(), balance != null ? balance : 0L);
        }
        return balances;
    }

    // ─────────────────────────────────────────────────────────────
    // BACKORDER ITEMS
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BackorderItemResponse> getPendingBackorderItems() {
        return billStockItemRepository.findByBackorderTrue().stream()
                .map(item -> BackorderItemResponse.builder()
                        .id(item.getId())
                        .billId(item.getBill().getId())
                        .billNumber(item.getBill().getBillNumber())
                        .customerName(item.getBill().getCustomerName())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .predictedUnitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void issueBackorderItem(Long id) {
        User currentUser = getCurrentUser();
        BillStockItem item = billStockItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Backorder item not found"));
        if (!Boolean.TRUE.equals(item.getBackorder())) {
            throw new RuntimeException("Item is not a backorder item");
        }
        // Check stock
        List<Object[]> balances = shadowStockMovementRepository
                .getAvailableBalancesForProducts(List.of(item.getProduct().getId()));
        long available = balances.isEmpty() ? 0L : ((Number) balances.get(0)[1]).longValue();
        if (available < item.getQuantity()) {
            throw new RuntimeException("Insufficient stock for " + item.getProduct().getName() +
                    ": available " + available + ", required " + item.getQuantity());
        }
        // Create BILL_OUT
        createShadowStockMovement(item.getProduct(), item.getQuantity(),
                ShadowStockMovement.MovementType.BILL_OUT, item.getBill(), null, currentUser);
        // Add to bill total
        Bill bill = item.getBill();
        bill.setTotalAmount(bill.getTotalAmount().add(item.getLineTotal()));
        BillBalance.recompute(bill);
        billRepository.save(bill);
        // Mark as issued (no longer backorder)
        item.setBackorder(false);
        item.setUpdatedAt(LocalDateTime.now());
        billStockItemRepository.save(item);
    }

    @Transactional
    public BillStockItemResponse addItemToApprovedBill(Long billId, StockItemRequest req) {
        User currentUser = getCurrentUser();
        Bill bill = billRepository.findById(billId)
            .orElseThrow(() -> new RuntimeException("Bill not found"));
        ReturnProduct product = returnProductRepository.findById(req.getProductId())
            .orElseThrow(() -> new RuntimeException("Product not found"));

        // Stock availability check
        List<Long> pids = List.of(product.getId());
        Map<Long, Long> available = shadowStockMovementRepository.getAvailableBalancesForProducts(pids)
            .stream().collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
        long avail = available.getOrDefault(product.getId(), 0L);
        if (avail < req.getQuantity()) {
            throw new RuntimeException("Insufficient stock for " + product.getName() + " (available: " + avail + ")");
        }

        BigDecimal unitPrice = product.getUnitPrice();
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(req.getQuantity()));

        BillStockItem item = BillStockItem.builder()
            .bill(bill)
            .product(product)
            .quantity(req.getQuantity())
            .unitPrice(unitPrice)
            .lineTotal(lineTotal)
            .backorder(false)
            .createdAt(LocalDateTime.now())
            .build();
        BillStockItem saved = billStockItemRepository.save(item);

        createShadowStockMovement(product, req.getQuantity(), ShadowStockMovement.MovementType.BILL_OUT, bill, "ITEM-ADD", currentUser);

        return BillStockItemResponse.builder()
            .id(saved.getId())
            .billId(bill.getId())
            .productId(product.getId())
            .productName(product.getName())
            .quantity(saved.getQuantity())
            .unitPrice(saved.getUnitPrice())
            .lineTotal(saved.getLineTotal())
            .backorder(false)
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private void createShadowStockMovement(
            ReturnProduct product, Long quantity,
            ShadowStockMovement.MovementType type,
            Bill bill, String invoiceNumber, User enteredBy) {

        ShadowStockMovement movement = ShadowStockMovement.builder()
                .product(product)
                .type(type)
                .quantity(quantity)
                .bill(bill)
                .invoiceNumber(invoiceNumber)
                .movementDate(LocalDate.now())
                .enteredBy(enteredBy)
                .cancelled(false)
                .createdAt(LocalDateTime.now())
                .build();
        shadowStockMovementRepository.save(movement);
    }

    private String generateBillNumber(BillSource source) {
        return switch (source) {
            case SYSTEM      -> "SYS-" + System.currentTimeMillis();
            case DRAFT       -> "DFT-" + System.currentTimeMillis();
            case MANUAL      -> "MAN-" + System.currentTimeMillis();
            case MANUAL_BOOK -> "BK-"  + System.currentTimeMillis();
            // Invoicing raises its own bills, numbered from the agent invoice number.
            // The stock module never generates one, and never should.
            case INVOICE     -> throw new IllegalStateException(
                    "Bills from the invoicing module are numbered there, not here");
        };
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private StockBillResponse toStockBillResponse(Bill bill) {
        long totalQty = billStockItemRepository.findByBillId(bill.getId())
                .stream().mapToLong(BillStockItem::getQuantity).sum();
        return StockBillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .billSource(bill.getBillSource().name())
                .customerName(bill.getCustomerName())
                .amount(bill.getTotalAmount())
                .paymentType(bill.getBillType().name())
                .billDate(bill.getBillDate())
                .enteredByName(bill.getEnteredBy() != null ? bill.getEnteredBy().getFullName() : null)
                .totalQty(totalQty)
                .build();
    }

    private StockBillResponse toStockBillResponseWithFlag(Bill bill, boolean isLinkingBill) {
        long totalQty = billStockItemRepository.findByBillId(bill.getId())
                .stream().mapToLong(BillStockItem::getQuantity).sum();
        return StockBillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .billSource(bill.getBillSource().name())
                .customerName(bill.getCustomerName())
                .amount(bill.getTotalAmount())
                .paymentType(bill.getBillType().name())
                .billDate(bill.getBillDate())
                .enteredByName(bill.getEnteredBy() != null ? bill.getEnteredBy().getFullName() : null)
                .totalQty(totalQty)
                .isLinkingBill(isLinkingBill)
                .build();
    }

    private StockBillResponse toStockBillResponseWithItems(Bill bill) {
        List<BillStockItemResponse> items = getBillItems(bill.getId());
        long totalQty = items.stream().mapToLong(BillStockItemResponse::getQuantity).sum();
        return StockBillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .billSource(bill.getBillSource().name())
                .customerName(bill.getCustomerName())
                .amount(bill.getTotalAmount())
                .paymentType(bill.getBillType().name())
                .billDate(bill.getBillDate())
                .enteredByName(bill.getEnteredBy() != null ? bill.getEnteredBy().getFullName() : null)
                .totalQty(totalQty)
                .items(items)
                .build();
    }

    private SummaryLoadBillResponse toSummaryLoadBillResponse(SummaryLoadBill slb) {
        List<Long> billIds = slb.getSystemBills().stream()
                .map(Bill::getId)
                .collect(Collectors.toList());

        List<SummaryLoadBillResponse.ItemDto> itemDtos = slb.getItems().stream()
                .map(i -> SummaryLoadBillResponse.ItemDto.builder()
                        .id(i.getId())
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice() != null ? i.getUnitPrice() : i.getProduct().getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        long totalQty = slb.getItems().stream().mapToLong(i -> i.getQuantity()).sum();

        return SummaryLoadBillResponse.builder()
                .id(slb.getId())
                .systemBillIds(billIds)
                .numberOfBills(slb.getSystemBills().size())
                .totalQuantity(totalQty)
                .loadDate(slb.getLoadDate())
                .notes(slb.getNotes())
                .status(slb.getStatus().toString())
                .createdByName(slb.getCreatedBy().getFullName())
                .createdAt(slb.getCreatedAt())
                .approvedByName(slb.getApprovedBy() != null ? slb.getApprovedBy().getFullName() : null)
                .approvedAt(slb.getApprovedAt())
                .items(itemDtos)
                .build();
    }
}
