package com.multi.finance.service.impl;

import com.multi.finance.dto.request.BillAuditMarkRequest;
import com.multi.finance.dto.response.BillAuditRowResponse;
import com.multi.finance.dto.response.BillAuditSessionResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.BillAuditMark;
import com.multi.finance.entity.BillAuditSession;
import com.multi.finance.entity.User;
import com.multi.finance.enums.BillAuditMarkType;
import com.multi.finance.enums.BusinessType;
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

    @Transactional
    public BillAuditSessionResponse openSession(LocalDate month, String business, String area) {
        LocalDate periodMonth = month.withDayOfMonth(1);
        String businessScope = blankToNull(business);
        String areaScope = blankToNull(area);

        BillAuditSession session = sessionRepository
                .findOpenSession(periodMonth, businessScope, areaScope)
                .orElseGet(() -> sessionRepository.save(BillAuditSession.builder()
                        .periodMonth(periodMonth)
                        .businessScope(businessScope)
                        .areaScope(areaScope)
                        .openedBy(currentUser())
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

        return billsInScope(session).stream()
                .map(bill -> toRow(bill, marksByBill.get(bill.getId())))
                .toList();
    }

    /** Sets, changes or (when markType is null) clears a bill's mark. */
    @Transactional
    public BillAuditRowResponse mark(BillAuditMarkRequest req) {
        BillAuditSession session = getSession(req.getSessionId());
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
        session.setClosedAt(LocalDateTime.now());
        return toSessionResponse(sessionRepository.save(session));
    }

    // ── helpers ──────────────────────────────────────────────────────

    private List<Bill> billsInScope(BillAuditSession session) {
        LocalDate cutoff = session.getPeriodMonth()
                .plusMonths(1).minusDays(1);   // last day of the period month
        BusinessType business = session.getBusinessScope() != null
                ? BusinessType.valueOf(session.getBusinessScope())
                : null;
        return billRepository.findPendingForAudit(cutoff, business, session.getAreaScope());
    }

    private Map<Long, BillAuditMark> marksByBill(Long sessionId) {
        return markRepository.findBySessionIdWithBill(sessionId).stream()
                .collect(Collectors.toMap(m -> m.getBill().getId(), Function.identity()));
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
        int total = billsInScope(session).size();
        Map<Long, BillAuditMark> marks = marksByBill(session.getId());

        int inHand = countOf(marks, BillAuditMarkType.IN_HAND);
        int paidNotEntered = countOf(marks, BillAuditMarkType.PAID_NOT_ENTERED);
        int missing = countOf(marks, BillAuditMarkType.MISSING);

        return BillAuditSessionResponse.builder()
                .id(session.getId())
                .periodMonth(session.getPeriodMonth())
                .businessScope(session.getBusinessScope())
                .areaScope(session.getAreaScope())
                .openedByName(session.getOpenedBy() != null ? session.getOpenedBy().getFullName() : null)
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

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
