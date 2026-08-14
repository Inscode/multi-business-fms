package com.multi.finance.service.impl;

import com.multi.finance.dto.request.ApproveReturnRequest;
import com.multi.finance.dto.request.CancelReturnRequest;
import com.multi.finance.dto.request.ConfirmGoodsRequest;
import com.multi.finance.dto.request.CreateBillReturnRequest;
import com.multi.finance.dto.response.BillReturnItemResponse;
import com.multi.finance.dto.response.BillReturnResponse;
import com.multi.finance.dto.response.BillReturnSummary;
import com.multi.finance.dto.response.ReturnableLineResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.GoodsReceipt;
import com.multi.finance.enums.ReturnStatus;
import com.multi.finance.enums.ReturnType;
import com.multi.finance.invoicing.entity.Invoice;
import com.multi.finance.invoicing.entity.InvoiceLine;
import com.multi.finance.invoicing.entity.StockMovement;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.InvoiceType;
import com.multi.finance.invoicing.enums.StockMovementType;
import com.multi.finance.invoicing.repository.InvoiceRepository;
import com.multi.finance.invoicing.repository.ItemRepository;
import com.multi.finance.invoicing.repository.StockMovementRepository;
import com.multi.finance.repository.*;
import com.multi.finance.service.BillBalance;
import com.multi.finance.service.ReturnCreditCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Salable and damage returns against a bill.
 *
 * <p>Two rules shape everything here.
 *
 * <p><b>The bill's invoiced amount is never rewritten.</b> A return accumulates into
 * {@code bill.returnsTotal} and the balance is derived from the pair. Reversing a
 * return is then a recompute, not a compensating entry that has to be got right a
 * second time.
 *
 * <p><b>A return has to be answered before money is collected.</b> The accountant
 * confirms what physically arrived, and until they do, payment entry on that bill is
 * refused. That is the whole answer to returns going missing: the check sits at the
 * moment cash moves, not on a screen someone has to remember to open.
 */
@Service
@RequiredArgsConstructor
public class BillReturnServiceImpl {

    /** Statuses that still owe someone an action; these hold a bill open. */
    private static final List<ReturnStatus> OPEN_STATUSES =
            List.of(ReturnStatus.PENDING, ReturnStatus.GOODS_CONFIRMED);

    private final BillReturnRepository billReturnRepository;
    private final BillRepository billRepository;
    private final ReturnProductRepository returnProductRepository;
    private final WorkerRepository workerRepository;
    private final ShadowStockMovementRepository shadowStockMovementRepository;
    private final InvoiceRepository invoiceRepository;
    private final ItemRepository itemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReturnCreditCalculator calculator;

    // ── Picking items off the bill ───────────────────────────────────────

    /**
     * The lines of the invoice behind this bill that can still be sent back, with the
     * prices and discount that were actually charged.
     *
     * <p>Empty when the bill predates invoicing — those returns are entered from the
     * catalogue instead, with the discount typed by hand.
     */
    @Transactional(readOnly = true)
    public List<ReturnableLineResponse> getReturnableLines(Long billId) {
        Invoice invoice = invoiceRepository.findByBillIdWithLines(billId).orElse(null);
        if (invoice == null) return List.of();

        // Present only on a cash invoice, and even then only against Rainco lines —
        // stationery and plastic never had a cash discount taken on the way out.
        BigDecimal invoiceCashPct = invoice.getInvoiceType() == InvoiceType.CASH
                ? invoice.getCashDiscountPct() : null;

        List<ReturnableLineResponse> out = new ArrayList<>();
        for (InvoiceLine line : invoice.getLines()) {
            Integer claimed = billReturnRepository.sumClaimedQtyForLine(line.getId());
            int already = claimed == null ? 0 : claimed;
            int sold = line.getQty() == null ? 0 : line.getQty();
            int available = Math.max(sold - already, 0);

            out.add(ReturnableLineResponse.builder()
                    .invoiceLineId(line.getId())
                    .itemId(line.getItem().getId())
                    .itemCode(line.getItem().getItemCode())
                    .description(line.getItem().getDescription())
                    .brandName(line.getBrand() != null ? line.getBrand().getName() : null)
                    .qtySold(sold)
                    .qtyAlreadyReturned(already)
                    .qtyAvailable(available)
                    .wsp(line.getWsp())
                    .appliedDiscountPct(line.getAppliedDiscountPct())
                    .cashDiscountPct(calculator.cashApplies(categoryOf(line)) ? invoiceCashPct : null)
                    .build());
        }
        return out;
    }

    // ── Entering a return ────────────────────────────────────────────────

    @Transactional
    public BillReturnResponse create(Long billId, CreateBillReturnRequest req) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        User caller = getCurrentUser();

        boolean fromSameBill = Boolean.TRUE.equals(req.getFromSameBill());
        BusinessType business = bill.getBusiness();

        // For a same-bill return the cash question is already settled by the bill it
        // came off, so the client's answer is ignored in favour of the record.
        Invoice invoice = fromSameBill
                ? invoiceRepository.findByBillIdWithLines(billId).orElse(null)
                : null;
        boolean cashSale = fromSameBill
                ? (invoice != null && invoice.getInvoiceType() == InvoiceType.CASH)
                : Boolean.TRUE.equals(req.getCashSale());
        BigDecimal headerCashPct = !cashSale ? null
                : (invoice != null && invoice.getCashDiscountPct() != null
                        ? invoice.getCashDiscountPct()
                        : defaultCashDiscountPct(business));

        Map<Long, InvoiceLine> linesById = invoice == null ? Map.of()
                : invoice.getLines().stream()
                        .collect(Collectors.toMap(InvoiceLine::getId, l -> l));

        boolean anyEdited = false;
        List<BillReturnItem> items = new ArrayList<>();

        for (var i : req.getItems()) {
            int qty = i.getQuantityRequested() == null ? 0 : i.getQuantityRequested();
            if (qty <= 0) continue;

            BigDecimal wsp;
            BigDecimal slabPct;
            String name;
            com.multi.finance.invoicing.entity.Item invItem = null;
            InvoiceLine sourceLine = null;
            ReturnProduct product = null;

            if (fromSameBill && i.getInvoiceLineId() != null) {
                sourceLine = linesById.get(i.getInvoiceLineId());
                if (sourceLine == null) {
                    throw new RuntimeException(
                            "That line is not on this bill's invoice — it cannot be returned against it.");
                }
                // Can't send back more than was bought, net of what has already gone back.
                Integer claimed = billReturnRepository.sumClaimedQtyForLine(sourceLine.getId());
                int available = (sourceLine.getQty() == null ? 0 : sourceLine.getQty())
                        - (claimed == null ? 0 : claimed);
                if (qty > available) {
                    throw new RuntimeException("Only " + available + " of "
                            + sourceLine.getItem().getDescription()
                            + " is left to return on this bill.");
                }
                // Read from the line, not from the client: it is what was charged.
                wsp = sourceLine.getWsp();
                slabPct = sourceLine.getAppliedDiscountPct();
                invItem = sourceLine.getItem();
                name = invItem.getDescription();
            } else {
                // Off an older bill — the accountant supplies the price and the discount.
                if (i.getProductId() != null) {
                    product = returnProductRepository.findById(i.getProductId())
                            .orElseThrow(() -> new RuntimeException("Product not found"));
                }
                if (i.getItemId() != null) {
                    invItem = itemRepository.findById(i.getItemId()).orElse(null);
                }
                wsp = i.getUnitPrice() != null ? i.getUnitPrice()
                        : product != null ? product.getUnitPrice()
                        : invItem != null ? invItem.getWholesalePrice()
                        : BigDecimal.ZERO;
                slabPct = i.getSlabDiscountPct();
                name = product != null ? product.getName()
                        : invItem != null ? invItem.getDescription()
                        : i.getItemName();
            }

            // Cash is a Rainco arrangement. On a MIX bill the stationery and plastic
            // lines never had it taken, so they must not have it credited back.
            CategoryType lineCategory = sourceLine != null ? categoryOf(sourceLine)
                    : invItem != null ? categoryOf(invItem)
                    : categoryFor(business);
            BigDecimal cashPct = (cashSale && calculator.cashApplies(lineCategory))
                    ? headerCashPct : null;
            var credit = calculator.compute(business, wsp, qty, slabPct, cashPct);

            BigDecimal finalCredit = credit.credit();
            boolean edited = false;
            if (i.getCreditAmountOverride() != null
                    && i.getCreditAmountOverride().compareTo(credit.credit()) != 0) {
                finalCredit = i.getCreditAmountOverride().max(BigDecimal.ZERO);
                edited = true;
                anyEdited = true;
            }

            items.add(BillReturnItem.builder()
                    .product(product)
                    .invoiceLine(sourceLine)
                    .invItem(invItem)
                    .itemName(name)
                    .unitPrice(wsp)
                    .quantityRequested(qty)
                    .quantityReturned(i.getQuantityReturned())
                    .grossValue(credit.gross())
                    .slabDiscountPct(calculator.isFlatRated(business) ? null : slabPct)
                    .cashDiscountPct(calculator.isFlatRated(business) ? null : cashPct)
                    .creditAmount(finalCredit)
                    .computedCreditAmount(credit.credit())
                    .amountEdited(edited)
                    .lineTotal(finalCredit)
                    .build());
        }

        if (items.isEmpty()) {
            throw new RuntimeException("A return needs at least one item with a quantity.");
        }

        BigDecimal itemsTotal = items.stream()
                .map(BillReturnItem::getGrossValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Header discounts come off what the lines add up to after their own working,
        // and the flat amount comes off after the percentage — each one applies to what
        // is left, never to the starting figure.
        BigDecimal calculated = applyHeaderDiscounts(
                sumCredits(items), req.getDiscountPercentage(), req.getDiscountFixed());

        Worker responsibleWorker = null;
        if (req.getResponsibleWorkerId() != null) {
            responsibleWorker = workerRepository.findById(req.getResponsibleWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
        }

        final boolean editedFlag = anyEdited;
        BillReturn billReturn = BillReturn.builder()
                .bill(bill)
                .returnType(req.getReturnType())
                .status(ReturnStatus.PENDING)
                .fromSameBill(fromSameBill)
                .cashSale(cashSale)
                .cashDiscountPct(headerCashPct)
                .itemsTotal(itemsTotal)
                .discountPercentage(req.getDiscountPercentage())
                .discountFixed(req.getDiscountFixed())
                .calculatedReturnAmount(calculated)
                .predictedValue(req.getPredictedValue())
                .notes(req.getNotes())
                .responsibleWorker(responsibleWorker)
                .submittedBy(caller)
                .submittedAt(LocalDateTime.now())
                .amountEdited(editedFlag)
                .amountEditedBy(editedFlag ? caller.getUsername() : null)
                .items(items)
                .build();

        items.forEach(i -> i.setBillReturn(billReturn));
        return toResponse(billReturnRepository.save(billReturn));
    }

    // ── The accountant's goods gate ──────────────────────────────────────

    /**
     * Records what actually came back. Payment on the bill is refused until this has
     * been answered, so the question is put at the moment it matters.
     */
    @Transactional
    public BillReturnResponse confirmGoods(Long id, ConfirmGoodsRequest req) {
        BillReturn ret = billReturnRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Bill return not found"));
        if (ret.getStatus() != ReturnStatus.PENDING
                && ret.getStatus() != ReturnStatus.GOODS_CONFIRMED) {
            throw new RuntimeException("This return has already been reviewed.");
        }
        if (req.getReceipt() == null) {
            throw new RuntimeException("Say whether the goods arrived: all, some, or none.");
        }
        if (req.getReceipt() != GoodsReceipt.ALL
                && (req.getNote() == null || req.getNote().isBlank())) {
            throw new RuntimeException("Note what was missing so the admin can chase it.");
        }

        if (req.getReceipt() == GoodsReceipt.ALL) {
            ret.getItems().forEach(i -> i.setQuantityReturned(i.getQuantityRequested()));
        } else if (req.getReceipt() == GoodsReceipt.NONE) {
            ret.getItems().forEach(i -> i.setQuantityReturned(0));
        } else {
            applyReceivedQuantities(ret, req.getItems());
        }

        User caller = getCurrentUser();
        ret.setGoodsReceipt(req.getReceipt());
        ret.setGoodsConfirmedBy(caller);
        ret.setGoodsConfirmedAt(LocalDateTime.now());
        ret.setGoodsConfirmedNote(req.getNote());
        ret.setStatus(ReturnStatus.GOODS_CONFIRMED);
        return toResponse(billReturnRepository.save(ret));
    }

    // ── Admin review ─────────────────────────────────────────────────────

    @Transactional
    public BillReturnResponse approve(Long id, ApproveReturnRequest req) {
        BillReturn ret = billReturnRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Bill return not found"));
        if (!ret.getStatus().isOpen()) {
            throw new RuntimeException("This return is not awaiting review.");
        }

        applyReceivedQuantities(ret, req == null ? null : req.getItems());

        // Credit only what actually came back — the per-line credit, prorated by the
        // quantity received against the quantity claimed.
        BigDecimal receivedCredit = ret.getItems().stream()
                .map(this::receivedCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        receivedCredit = applyHeaderDiscounts(
                receivedCredit, ret.getDiscountPercentage(), ret.getDiscountFixed());

        BigDecimal approvedAmount;
        String approveWith = req == null || req.getApproveWith() == null
                ? "CALCULATED" : req.getApproveWith().toUpperCase();
        if ("PREDICTED".equals(approveWith)) {
            approvedAmount = ret.getPredictedValue();
        } else {
            ret.setCalculatedReturnAmount(receivedCredit);
            approvedAmount = receivedCredit;
        }
        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Approved amount must be greater than zero");
        }

        User reviewer = getCurrentUser();
        ret.setStatus(ReturnStatus.APPROVED);
        ret.setApprovedWith(approveWith);
        ret.setApprovedAmount(approvedAmount);
        ret.setReviewedBy(reviewer);
        ret.setReviewedAt(LocalDateTime.now());
        ret.setBillAmountAdjusted(true);
        BillReturn saved = billReturnRepository.save(ret);

        moveStock(saved, reviewer, false);
        recomputeBillReturns(saved.getBill());
        return toResponse(saved);
    }

    @Transactional
    public BillReturnResponse reject(Long id, String reason) {
        BillReturn ret = findById(id);
        if (!ret.getStatus().isOpen()) {
            throw new RuntimeException("This return is not awaiting review.");
        }
        ret.setStatus(ReturnStatus.REJECTED);
        ret.setRejectionReason(reason);
        ret.setReviewedBy(getCurrentUser());
        ret.setReviewedAt(LocalDateTime.now());
        BillReturn saved = billReturnRepository.save(ret);
        recomputeBillReturns(saved.getBill());
        return toResponse(saved);
    }

    /** The goods were claimed but never turned up. Nothing comes off the bill. */
    @Transactional
    public BillReturnResponse markNotReceived(Long id, String reason) {
        BillReturn ret = billReturnRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Bill return not found"));
        if (!ret.getStatus().isOpen()) {
            throw new RuntimeException("This return is not awaiting review.");
        }
        ret.getItems().forEach(i -> i.setQuantityReturned(0));
        ret.setStatus(ReturnStatus.NOT_RECEIVED);
        ret.setRejectionReason(reason);
        ret.setReviewedBy(getCurrentUser());
        ret.setReviewedAt(LocalDateTime.now());
        BillReturn saved = billReturnRepository.save(ret);
        recomputeBillReturns(saved.getBill());
        return toResponse(saved);
    }

    /**
     * Reverses an approved return — the accountant entered something that should not
     * have been. The credit goes back onto the bill and the stock movement is undone.
     */
    @Transactional
    public BillReturnResponse cancel(Long id, CancelReturnRequest req) {
        BillReturn ret = billReturnRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Bill return not found"));
        if (ret.getStatus() == ReturnStatus.CANCELLED) {
            throw new RuntimeException("This return has already been cancelled.");
        }
        if (Boolean.TRUE.equals(ret.getLegacyAmountAdjusted())) {
            throw new RuntimeException(
                    "This return was approved under the old scheme, which wrote the deduction "
                  + "straight into the bill total. Correct the bill amount by hand instead.");
        }

        User caller = getCurrentUser();
        if (Boolean.TRUE.equals(ret.getStockApplied())) {
            moveStock(ret, caller, true);
        }

        ret.setStatus(ReturnStatus.CANCELLED);
        ret.setCancelledBy(caller);
        ret.setCancelledAt(LocalDateTime.now());
        ret.setCancelReason(req == null ? null : req.getReason());
        BillReturn saved = billReturnRepository.save(ret);

        // The bill goes back to its full worth; nothing was written into total_amount,
        // so this is a recompute rather than an adjusting entry.
        recomputeBillReturns(saved.getBill());
        BillBalance.reopenIfClosed(saved.getBill(), com.multi.finance.enums.BillStatus.STORE_RECEIVED);
        billRepository.save(saved.getBill());
        return toResponse(saved);
    }

    // ── Reading ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BillReturnResponse> getAll(ReturnStatus status) {
        List<BillReturn> list = (status != null)
                ? billReturnRepository.findByStatusOrderBySubmittedAtDesc(status)
                : billReturnRepository.findAllByOrderBySubmittedAtDesc();
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BillReturnResponse> getForBill(Long billId) {
        return billReturnRepository.findByBillIdWithItems(billId)
                .stream().map(this::toResponse).toList();
    }

    /** Everything the bill view needs: damage and salable apart, and what is still open. */
    @Transactional(readOnly = true)
    public BillReturnSummary getSummary(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        List<BillReturn> all = billReturnRepository.findByBillIdWithItems(billId);

        BigDecimal salable = BigDecimal.ZERO;
        BigDecimal damage = BigDecimal.ZERO;
        BigDecimal openAmount = BigDecimal.ZERO;
        int openCount = 0;

        for (BillReturn r : all) {
            if (r.getStatus().reducesBill() && !Boolean.TRUE.equals(r.getLegacyAmountAdjusted())) {
                BigDecimal amt = nz(r.getApprovedAmount());
                if (r.getReturnType() == ReturnType.DAMAGE) damage = damage.add(amt);
                else salable = salable.add(amt);
            }
            if (r.getStatus().isOpen()) {
                openCount++;
                openAmount = openAmount.add(nz(r.getCalculatedReturnAmount()));
            }
        }

        return BillReturnSummary.builder()
                .billTotal(nz(bill.getTotalAmount()))
                .salableTotal(salable)
                .damageTotal(damage)
                .returnsTotal(nz(bill.getReturnsTotal()))
                .payable(BillBalance.payable(bill))
                .amountPaid(nz(bill.getAmountPaid()))
                .balanceRemaining(nz(bill.getBalanceRemaining()))
                .openCount(openCount)
                .openAmount(openAmount)
                .returns(all.stream().map(this::toResponse).toList())
                .build();
    }

    /**
     * Returns on a bill that nobody has confirmed or reviewed yet. Payment entry calls
     * this and refuses while it is non-empty.
     */
    @Transactional(readOnly = true)
    public List<BillReturn> openReturnsFor(Long billId) {
        return billReturnRepository.findByBillIdAndStatusIn(billId, OPEN_STATUSES);
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return billReturnRepository.countByStatusIn(OPEN_STATUSES);
    }

    // ── Internals ────────────────────────────────────────────────────────

    /**
     * Rewrites the bill's returns figure from its live returns, then recomputes the
     * balance. Summing from scratch rather than adding a delta means a cancel, a
     * re-approve and a second edit can't drift the number apart from its returns.
     */
    private void recomputeBillReturns(Bill bill) {
        BigDecimal total = billReturnRepository.sumActiveReturns(bill.getId());
        bill.setReturnsTotal(total == null ? BigDecimal.ZERO : total);
        BillBalance.recompute(bill);
        billRepository.save(bill);
    }

    /** The part of a line's credit that is backed by goods actually received. */
    private BigDecimal receivedCredit(BillReturnItem item) {
        int requested = item.getQuantityRequested() == null ? 0 : item.getQuantityRequested();
        int received = item.getQuantityReturned() == null ? requested : item.getQuantityReturned();
        BigDecimal credit = nz(item.getCreditAmount());
        if (requested <= 0 || received >= requested) return credit;
        if (received <= 0) return BigDecimal.ZERO;
        return credit.multiply(BigDecimal.valueOf(received))
                .divide(BigDecimal.valueOf(requested), 2, RoundingMode.HALF_UP);
    }

    private void applyReceivedQuantities(BillReturn ret,
                                         List<com.multi.finance.dto.request.ReceivedItemDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return;
        Map<Long, Integer> qtyMap = dtos.stream()
                .filter(d -> d.getId() != null && d.getQuantityReturned() != null)
                .collect(Collectors.toMap(
                        com.multi.finance.dto.request.ReceivedItemDto::getId,
                        com.multi.finance.dto.request.ReceivedItemDto::getQuantityReturned,
                        (a, b) -> b));
        ret.getItems().forEach(item -> {
            Integer received = qtyMap.get(item.getId());
            if (received != null) item.setQuantityReturned(received);
        });
    }

    /**
     * Moves the returned goods.
     *
     * <p>Salable stock goes back on the shelf to be sold again. Damage does not — it is
     * written off here and picked up by the damage dispatch flow to be claimed off the
     * agent, so it must never re-enter sellable stock.
     *
     * @param reverse true when undoing a cancelled return
     */
    private void moveStock(BillReturn ret, User actor, boolean reverse) {
        boolean salable = ret.getReturnType() == ReturnType.SALABLE;

        for (BillReturnItem item : ret.getItems()) {
            int qty = item.getQuantityReturned() != null
                    ? item.getQuantityReturned()
                    : (item.getQuantityRequested() == null ? 0 : item.getQuantityRequested());
            if (qty <= 0) continue;

            if (item.getInvItem() != null && salable) {
                int delta = reverse ? -qty : qty;
                itemRepository.adjustStock(item.getInvItem().getId(), delta);
                StockMovement mv = new StockMovement();
                mv.setItem(item.getInvItem());
                mv.setType(StockMovementType.RETURN_SALABLE);
                mv.setQuantity(delta);
                mv.setReferenceId(ret.getId());
                mv.setReferenceType("BILL_RETURN");
                mv.setNotes(reverse ? "Return cancelled" : "Salable return on bill "
                        + ret.getBill().getBillNumber());
                stockMovementRepository.save(mv);
            } else if (item.getInvItem() != null) {
                // Damaged goods are real stock — they sit in the warehouse until they go
                // back to the agent — but they must never be sellable. They already left
                // stock_qty when the invoice was raised, so this adds to the damage
                // bucket and leaves sellable stock alone rather than deducting twice.
                int delta = reverse ? -qty : qty;
                itemRepository.adjustDamageStock(item.getInvItem().getId(), delta);
                StockMovement mv = new StockMovement();
                mv.setItem(item.getInvItem());
                mv.setType(StockMovementType.RETURN_DAMAGE);
                mv.setQuantity(delta);
                mv.setReferenceId(ret.getId());
                mv.setReferenceType("BILL_RETURN");
                mv.setNotes(reverse ? "Damage return cancelled"
                        : "Damage return on bill " + ret.getBill().getBillNumber()
                          + " — held for dispatch to agent");
                stockMovementRepository.save(mv);
            }

            // The legacy shadow ledger is still read by the old stock screens; keep it
            // fed until they go.
            if (item.getProduct() != null && !reverse) {
                shadowStockMovementRepository.save(ShadowStockMovement.builder()
                        .product(item.getProduct())
                        .type(salable ? ShadowStockMovement.MovementType.SALABLE_RETURN
                                      : ShadowStockMovement.MovementType.DAMAGE_IN)
                        .quantity((long) qty)
                        .bill(ret.getBill())
                        .invoiceNumber("RET-" + ret.getId())
                        .movementDate(LocalDate.now())
                        .enteredBy(actor)
                        .cancelled(false)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
        ret.setStockApplied(!reverse);
    }

    /** Falls back to the Rainco cash rate when the source invoice isn't to hand. */
    private BigDecimal defaultCashDiscountPct(BusinessType business) {
        return business == BusinessType.RAINCO ? new BigDecimal("5.00") : null;
    }

    private CategoryType categoryOf(InvoiceLine line) {
        return line.getBrand() != null ? line.getBrand().getCategory() : null;
    }

    private CategoryType categoryOf(com.multi.finance.invoicing.entity.Item item) {
        return item.getBrand() != null ? item.getBrand().getCategory() : null;
    }

    /**
     * Last resort for a catalogue return with no invoicing item behind it: read the
     * category off the bill's business. MIX cannot be resolved this way, so it is left
     * unknown and the cash discount is withheld rather than guessed on.
     */
    private CategoryType categoryFor(BusinessType business) {
        return switch (business) {
            case RAINCO     -> CategoryType.RAINCO;
            case STATIONERY -> CategoryType.STATIONERY;
            case PLASTIC    -> CategoryType.PLASTIC;
            default         -> null;
        };
    }

    /**
     * Applies the return's header discounts in order, each to what is left after the
     * one before it.
     *
     * <p>The percentage was previously stored and then ignored, so a return entered
     * with one was credited at full value. Taking it off the running figure rather than
     * the original also matters the moment both are used: charged against the starting
     * amount they would together take off more than either rate describes.
     */
    private BigDecimal applyHeaderDiscounts(BigDecimal base, BigDecimal pct, BigDecimal fixed) {
        BigDecimal amount = nz(base);
        if (pct != null && pct.compareTo(BigDecimal.ZERO) > 0) {
            amount = amount.subtract(
                    amount.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        }
        if (fixed != null && fixed.compareTo(BigDecimal.ZERO) > 0) {
            amount = amount.subtract(fixed);
        }
        return amount.max(BigDecimal.ZERO);
    }

    private BigDecimal sumCredits(List<BillReturnItem> items) {
        return items.stream().map(i -> nz(i.getCreditAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
                        .invoiceLineId(i.getInvoiceLine() != null ? i.getInvoiceLine().getId() : null)
                        .itemId(i.getInvItem() != null ? i.getInvItem().getId() : null)
                        .itemCode(i.getInvItem() != null ? i.getInvItem().getItemCode() : null)
                        .itemName(i.getItemName())
                        .unitPrice(i.getUnitPrice())
                        .quantityRequested(i.getQuantityRequested())
                        .quantityReturned(i.getQuantityReturned())
                        .grossValue(i.getGrossValue())
                        .slabDiscountPct(i.getSlabDiscountPct())
                        .cashDiscountPct(i.getCashDiscountPct())
                        .creditAmount(i.getCreditAmount())
                        .amountEdited(i.getAmountEdited())
                        .computedCreditAmount(i.getComputedCreditAmount())
                        .lineTotal(i.getLineTotal())
                        .build()).toList();

        BigDecimal shortfall = (r.getItems() == null) ? BigDecimal.ZERO :
                r.getItems().stream().map(i -> {
                    int requested = i.getQuantityRequested() != null ? i.getQuantityRequested() : 0;
                    int returned  = i.getQuantityReturned()  != null ? i.getQuantityReturned()  : 0;
                    int diff = requested - returned;
                    return diff > 0
                            ? nz(i.getUnitPrice()).multiply(BigDecimal.valueOf(diff))
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
                .responsibleWorkerName(r.getResponsibleWorker() != null
                        ? r.getResponsibleWorker().getFullName() : null)
                .submittedByName(r.getSubmittedBy().getFullName())
                .submittedAt(r.getSubmittedAt())
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .shortfallAmount(shortfall.compareTo(BigDecimal.ZERO) > 0 ? shortfall : null)
                .fromSameBill(r.getFromSameBill())
                .cashSale(r.getCashSale())
                .cashDiscountPct(r.getCashDiscountPct())
                .goodsReceipt(r.getGoodsReceipt() != null ? r.getGoodsReceipt().name() : null)
                .goodsConfirmedByName(r.getGoodsConfirmedBy() != null
                        ? r.getGoodsConfirmedBy().getFullName() : null)
                .goodsConfirmedAt(r.getGoodsConfirmedAt())
                .goodsConfirmedNote(r.getGoodsConfirmedNote())
                .amountEdited(r.getAmountEdited())
                .amountEditedBy(r.getAmountEditedBy())
                .stockApplied(r.getStockApplied())
                .cancelledByName(r.getCancelledBy() != null ? r.getCancelledBy().getFullName() : null)
                .cancelledAt(r.getCancelledAt())
                .cancelReason(r.getCancelReason())
                .open(r.getStatus().isOpen())
                .build();
    }

    /**
     * One-off repair for returns approved before the rework, whose deduction never made
     * it into the bill. Left in place because it is still the only way to fix them.
     */
    @Transactional
    public int fixHistoricalBillAmounts() {
        List<BillReturn> pending =
                billReturnRepository.findByStatusAndBillAmountAdjustedFalse(ReturnStatus.APPROVED);
        int count = 0;
        for (BillReturn ret : pending) {
            BigDecimal approved = ret.getApprovedAmount();
            if (approved == null || approved.compareTo(BigDecimal.ZERO) <= 0) continue;
            ret.setBillAmountAdjusted(true);
            billReturnRepository.save(ret);
            recomputeBillReturns(ret.getBill());
            count++;
        }
        return count;
    }
}
