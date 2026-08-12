package com.multi.finance.service.impl;

import com.multi.finance.dto.request.BillAuditMarkRequest;
import com.multi.finance.dto.response.BillAuditRowResponse;
import com.multi.finance.dto.response.BillAuditSessionResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.BillAuditMark;
import com.multi.finance.entity.BillAuditSession;
import com.multi.finance.entity.User;
import com.multi.finance.enums.BillAuditMarkType;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.BillAuditMarkRepository;
import com.multi.finance.repository.BillAuditSessionRepository;
import com.multi.finance.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Month-end reconciliation: work through every bill the system still shows as owing
 * and mark what you physically hold. Whatever stays unmarked — or is marked
 * PAID_NOT_ENTERED / MISSING — is the exception list to act on.
 */
@Service
@RequiredArgsConstructor
public class BillAuditServiceImpl {

    private final BillAuditSessionRepository sessionRepository;
    private final BillAuditMarkRepository markRepository;
    private final BillRepository billRepository;

    /**
     * Opens the caller's own sweep for a month, covering every business and area.
     * Narrowing to a business or area is a view filter applied on the frontend — it must
     * never start a new sweep, or marks already made would vanish from the list.
     *
     * Sweeps are per person: you tick your own copy, and can view but not change anyone
     * else's.
     */
    @Transactional
    public BillAuditSessionResponse openSession(LocalDate month) {
        LocalDate periodMonth = month.withDayOfMonth(1);
        User me = currentUser();

        BillAuditSession session = sessionRepository.findOpenSessions(periodMonth, me.getId())
                .stream().findFirst()
                .orElseGet(() -> sessionRepository.save(BillAuditSession.builder()
                        .periodMonth(periodMonth)
                        .openedBy(me)
                        .openedAt(LocalDateTime.now())
                        .build()));

        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<BillAuditSessionResponse> listSessions() {
        return sessionRepository.findAllByOrderByOpenedAtDesc()
                .stream().map(this::toSessionResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BillAuditRowResponse> getRows(Long sessionId) {
        BillAuditSession session = getSession(sessionId);
        Map<Long, BillAuditMark> marksByBill = marksByBill(sessionId);

        return billsForMonth(session).stream()
                .map(bill -> toRow(bill, marksByBill.get(bill.getId())))
                .toList();
    }

    /** Sets, changes or (when markType is null) clears a bill's mark. Owner or admin only. */
    @Transactional
    public BillAuditRowResponse mark(BillAuditMarkRequest req) {
        BillAuditSession session = getSession(req.getSessionId());
        requireEditable(session);
        Bill bill = billRepository.findById(req.getBillId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        if (req.getMarkType() == null) {
            markRepository.findBySessionIdAndBillId(session.getId(), bill.getId())
                    .ifPresent(markRepository::delete);
            return toRow(bill, null);
        }

        BillAuditMark mark = markRepository
                .findBySessionIdAndBillId(session.getId(), bill.getId())
                .orElseGet(() -> BillAuditMark.builder()
                        .session(session)
                        .bill(bill)
                        .build());

        mark.setMarkType(req.getMarkType());
        mark.setNote(req.getNote());
        mark.setMarkedBy(currentUser());
        mark.setMarkedAt(LocalDateTime.now());

        return toRow(bill, markRepository.save(mark));
    }

    @Transactional
    public BillAuditSessionResponse closeSession(Long sessionId) {
        BillAuditSession session = getSession(sessionId);
        requireEditable(session);
        session.setClosedAt(LocalDateTime.now());
        return toSessionResponse(sessionRepository.save(session));
    }

    // ── helpers ──────────────────────────────────────────────────────

    /** Every bill still owing as of the last day of the sweep's month. */
    private List<Bill> billsForMonth(BillAuditSession session) {
        LocalDate cutoff = session.getPeriodMonth().plusMonths(1).minusDays(1);
        return billRepository.findPendingForAudit(cutoff, null, null);
    }

    private Map<Long, BillAuditMark> marksByBill(Long sessionId) {
        return markRepository.findBySessionIdWithBill(sessionId).stream()
                .collect(Collectors.toMap(m -> m.getBill().getId(), Function.identity()));
    }

    /** A sweep is the property of whoever opened it; admins may edit any. */
    private boolean canEdit(BillAuditSession session) {
        User me = currentUser();
        if (me.getRole() == UserRole.ADMIN) return true;
        return session.getOpenedBy() != null && session.getOpenedBy().getId().equals(me.getId());
    }

    /** Ownership proper — an admin may edit anyone's sweep but only owns their own. */
    private boolean isMine(BillAuditSession session) {
        return session.getOpenedBy() != null
                && session.getOpenedBy().getId().equals(currentUser().getId());
    }

    private void requireEditable(BillAuditSession session) {
        if (!canEdit(session)) {
            throw new RuntimeException("This check belongs to "
                    + (session.getOpenedBy() != null ? session.getOpenedBy().getFullName() : "someone else")
                    + " — you can view it, but only they can change the marks.");
        }
    }

    private BillAuditSession getSession(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit session not found"));
    }

    private BillAuditRowResponse toRow(Bill bill, BillAuditMark mark) {
        return BillAuditRowResponse.builder()
                .billId(bill.getId())
                .billNumber(bill.getBillNumber())
                .billDate(bill.getBillDate())
                .customerName(bill.getCustomerName())
                .area(bill.getArea())
                .business(bill.getBusiness() != null ? bill.getBusiness().name() : null)
                .totalAmount(bill.getTotalAmount())
                .balanceRemaining(bill.getBalanceRemaining())
                .status(bill.getStatus() != null ? bill.getStatus().name() : null)
                .workerName(bill.getCurrentHolder() != null ? bill.getCurrentHolder().getFullName() : null)
                .markType(mark != null ? mark.getMarkType() : null)
                .note(mark != null ? mark.getNote() : null)
                .markedByName(mark != null && mark.getMarkedBy() != null ? mark.getMarkedBy().getFullName() : null)
                .markedAt(mark != null ? mark.getMarkedAt() : null)
                .build();
    }

    private BillAuditSessionResponse toSessionResponse(BillAuditSession session) {
        int total = billsForMonth(session).size();
        Map<Long, BillAuditMark> marks = marksByBill(session.getId());

        int inHand = countOf(marks, BillAuditMarkType.IN_HAND);
        int paidNotEntered = countOf(marks, BillAuditMarkType.PAID_NOT_ENTERED);
        int missing = countOf(marks, BillAuditMarkType.MISSING);

        return BillAuditSessionResponse.builder()
                .id(session.getId())
                .periodMonth(session.getPeriodMonth())
                .businessScope(session.getBusinessScope())
                .areaScope(session.getAreaScope())
                .openedById(session.getOpenedBy() != null ? session.getOpenedBy().getId() : null)
                .openedByName(session.getOpenedBy() != null ? session.getOpenedBy().getFullName() : null)
                .canEdit(canEdit(session))
                .mine(isMine(session))
                .openedAt(session.getOpenedAt())
                .closedAt(session.getClosedAt())
                .totalInScope(total)
                .inHand(inHand)
                .paidNotEntered(paidNotEntered)
                .missing(missing)
                .unchecked(Math.max(0, total - (inHand + paidNotEntered + missing)))
                .build();
    }

    private int countOf(Map<Long, BillAuditMark> marks, BillAuditMarkType type) {
        return (int) marks.values().stream().filter(m -> m.getMarkType() == type).count();
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
