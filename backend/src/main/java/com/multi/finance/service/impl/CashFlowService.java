package com.multi.finance.service.impl;

import com.multi.finance.dto.response.CashFlowResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.Payment;
import com.multi.finance.entity.SupplierPayable;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.enums.PaymentType;
import com.multi.finance.invoicing.entity.GoodsReceivedNote;
import com.multi.finance.invoicing.enums.GrnStatus;
import com.multi.finance.invoicing.repository.GrnRepository;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.PaymentRepository;
import com.multi.finance.repository.SupplierPayableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Forward cash-flow forecast: over the next N days, what is committed to arrive
 * versus what falls due to the principals.
 *
 * Deliberately forward-looking. Cash already banked is not counted — only money
 * still to come, which is what answers "am I getting more than I'm paying".
 *
 * Incoming counts cheques already in hand with a date inside the window: they are
 * committed and dated. Outstanding balances with no collection date are reported
 * separately as context and never enter the net, because treating a hope as cash
 * is how a forecast starts lying.
 */
@Service
@RequiredArgsConstructor
public class CashFlowService {

    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final GrnRepository grnRepository;
    private final SupplierPayableRepository payableRepository;

    @Transactional(readOnly = true)
    public CashFlowResponse forecast(int horizonDays) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(horizonDays);

        Map<BusinessType, Agg> byBusiness = new EnumMap<>(BusinessType.class);

        collectCheques(byBusiness, today, end);
        collectGrnDues(byBusiness, today, end);
        collectPayables(byBusiness, today, end);
        collectUndatedReceivable(byBusiness);

        List<CashFlowResponse.BusinessCashFlow> rows = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO, totalOut = BigDecimal.ZERO;
        BigDecimal totalUndated = BigDecimal.ZERO, totalOverdue = BigDecimal.ZERO;
        BigDecimal totalUntermed = BigDecimal.ZERO;
        int untermedCount = 0;

        for (Map.Entry<BusinessType, Agg> e : byBusiness.entrySet()) {
            Agg a = e.getValue();
            BigDecimal out = a.purchasesDue.add(a.payablesDue);

            rows.add(CashFlowResponse.BusinessCashFlow.builder()
                    .business(e.getKey().name())
                    .chequesIncoming(a.cheques)
                    .chequeCount(a.chequeCount)
                    .purchasesDue(a.purchasesDue)
                    .payablesDue(a.payablesDue)
                    .totalOutgoing(out)
                    .net(a.cheques.subtract(out))
                    .overdueOutgoing(a.overdue)
                    .undatedReceivable(a.undatedReceivable)
                    .untermed(a.untermed)
                    .untermedCount(a.untermedCount)
                    .build());

            totalIn = totalIn.add(a.cheques);
            totalOut = totalOut.add(out);
            totalUndated = totalUndated.add(a.undatedReceivable);
            totalOverdue = totalOverdue.add(a.overdue);
            totalUntermed = totalUntermed.add(a.untermed);
            untermedCount += a.untermedCount;
        }

        rows.sort(Comparator.comparing(CashFlowResponse.BusinessCashFlow::getBusiness));

        return CashFlowResponse.builder()
                .from(today)
                .to(end)
                .horizonDays(horizonDays)
                .businesses(rows)
                .totalIncoming(totalIn)
                .totalOutgoing(totalOut)
                .totalNet(totalIn.subtract(totalOut))
                .totalUndatedReceivable(totalUndated)
                .totalOverdueOutgoing(totalOverdue)
                .totalUntermed(totalUntermed)
                .untermedCount(untermedCount)
                .build();
    }

    /** Every dated movement in the window, for the timeline. */
    @Transactional(readOnly = true)
    public List<CashFlowResponse.CashFlowEntry> entries(int horizonDays) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(horizonDays);
        List<CashFlowResponse.CashFlowEntry> entries = new ArrayList<>();

        for (Payment p : chequesInWindow(today, end)) {
            entries.add(CashFlowResponse.CashFlowEntry.builder()
                    .date(p.getChequeDate())
                    .business(businessOf(p).name())
                    .direction("IN")
                    .source("CHEQUE")
                    .reference(p.getChequeNumber())
                    .party(p.getBill() != null ? p.getBill().getCustomerName() : null)
                    .amount(p.getAmount())
                    .overdue(false)
                    .build());
        }

        for (GoodsReceivedNote g : payableGrns()) {
            if (g.getDueDate() == null || g.getDueDate().isAfter(end)) continue;
            entries.add(CashFlowResponse.CashFlowEntry.builder()
                    .date(g.getDueDate())
                    .business(grnBusiness(g).name())
                    .direction("OUT")
                    .source("GRN")
                    .reference(g.getGrnNo())
                    .party(g.getSupplierName())
                    .amount(netValue(g))
                    .overdue(g.getDueDate().isBefore(today))
                    .build());
        }

        for (SupplierPayable p : unsettledPayables(end)) {
            entries.add(CashFlowResponse.CashFlowEntry.builder()
                    .date(p.getDueDate())
                    .business(p.getBusiness().name())
                    .direction("OUT")
                    .source("PAYABLE")
                    .reference(p.getChequeNumber())
                    .party(p.getSupplierName() != null ? p.getSupplierName() : p.getDescription())
                    .amount(p.getAmount())
                    .overdue(p.getDueDate().isBefore(today))
                    .build());
        }

        entries.sort(Comparator.comparing(CashFlowResponse.CashFlowEntry::getDate));
        return entries;
    }

    // ── collection ───────────────────────────────────────────────────

    private void collectCheques(Map<BusinessType, Agg> map, LocalDate today, LocalDate end) {
        for (Payment p : chequesInWindow(today, end)) {
            Agg a = map.computeIfAbsent(businessOf(p), k -> new Agg());
            a.cheques = a.cheques.add(p.getAmount());
            a.chequeCount++;
        }
    }

    private void collectGrnDues(Map<BusinessType, Agg> map, LocalDate today, LocalDate end) {
        for (GoodsReceivedNote g : payableGrns()) {
            Agg a = map.computeIfAbsent(grnBusiness(g), k -> new Agg());
            BigDecimal net = netValue(g);

            // No terms typed: still owed, but it cannot be placed on the timeline.
            // Reported separately rather than dropped, so it can't quietly flatter the net.
            if (g.getDueDate() == null) {
                a.untermed = a.untermed.add(net);
                a.untermedCount++;
                continue;
            }

            if (g.getDueDate().isBefore(today)) {
                a.overdue = a.overdue.add(net);
                a.purchasesDue = a.purchasesDue.add(net);   // still owed, so still counts
            } else if (!g.getDueDate().isAfter(end)) {
                a.purchasesDue = a.purchasesDue.add(net);
            }
        }
    }

    private void collectPayables(Map<BusinessType, Agg> map, LocalDate today, LocalDate end) {
        for (SupplierPayable p : unsettledPayables(end)) {
            Agg a = map.computeIfAbsent(p.getBusiness(), k -> new Agg());
            a.payablesDue = a.payablesDue.add(p.getAmount());
            if (p.getDueDate().isBefore(today)) a.overdue = a.overdue.add(p.getAmount());
        }
    }

    /** Context only: owed by customers with no date attached to it. */
    private void collectUndatedReceivable(Map<BusinessType, Agg> map) {
        List<BillStatus> excluded = List.of(
                BillStatus.COMPLETED, BillStatus.AWAITING_CONFIRMATION, BillStatus.CANCELLED);

        for (Bill b : billRepository.findByStatusNotInOrderByCreatedAtDesc(excluded)) {
            if (b.getBalanceRemaining() == null
                    || b.getBalanceRemaining().compareTo(BigDecimal.ZERO) <= 0
                    || Boolean.TRUE.equals(b.getWillBeLinked())) continue;
            Agg a = map.computeIfAbsent(b.getBusiness(), k -> new Agg());
            a.undatedReceivable = a.undatedReceivable.add(b.getBalanceRemaining());
        }
    }

    // ── sources ──────────────────────────────────────────────────────

    /**
     * Cheques in hand that clear inside the window. Rejected and returned ones are
     * excluded — that money is not coming.
     */
    private List<Payment> chequesInWindow(LocalDate today, LocalDate end) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentType() == PaymentType.CHEQUE)
                .filter(p -> p.getStatus() == PaymentStatus.ENTERED
                          || p.getStatus() == PaymentStatus.CONFIRMED)
                .filter(p -> p.getChequeDate() != null)
                .filter(p -> !p.getChequeDate().isBefore(today) && !p.getChequeDate().isAfter(end))
                .toList();
    }

    /** Approved notes that actually owe the principal something. */
    private List<GoodsReceivedNote> payableGrns() {
        return grnRepository.findByStatusWithLines(GrnStatus.APPROVED).stream()
                .filter(g -> !Boolean.FALSE.equals(g.getPaymentRequired()))
                .toList();
    }

    private List<SupplierPayable> unsettledPayables(LocalDate end) {
        return payableRepository.findAllByOrderByDueDateAsc().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getSettled()))
                .filter(p -> !p.getDueDate().isAfter(end))
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────

    /** What is actually payable on a note: gross less the supplier discount. */
    private BigDecimal netValue(GoodsReceivedNote g) {
        BigDecimal gross = g.getLines().stream()
                .map(l -> l.getLineTotal() != null ? l.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pct = g.getDiscountPct() != null ? g.getDiscountPct() : BigDecimal.ZERO;
        if (pct.signum() == 0) return gross.setScale(2, RoundingMode.HALF_UP);

        BigDecimal factor = BigDecimal.ONE.subtract(
                pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return gross.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private BusinessType businessOf(Payment p) {
        return p.getBill() != null && p.getBill().getBusiness() != null
                ? p.getBill().getBusiness()
                : BusinessType.RAINCO;
    }

    /** The invoicing category maps onto the bills business of the same name. */
    private BusinessType grnBusiness(GoodsReceivedNote g) {
        try {
            return BusinessType.valueOf(g.getCategory().name());
        } catch (IllegalArgumentException e) {
            return BusinessType.RAINCO;
        }
    }

    private static class Agg {
        BigDecimal cheques = BigDecimal.ZERO;
        int chequeCount = 0;
        BigDecimal purchasesDue = BigDecimal.ZERO;
        BigDecimal payablesDue = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        BigDecimal undatedReceivable = BigDecimal.ZERO;
        BigDecimal untermed = BigDecimal.ZERO;
        int untermedCount = 0;
    }
}
