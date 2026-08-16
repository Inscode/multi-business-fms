package com.multi.finance.service.impl;

import com.multi.finance.dto.response.MonthBusinessSummary;
import com.multi.finance.dto.request.OpenRunRequest;
import com.multi.finance.dto.response.DeliveryRunResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.DeliveryRun;
import com.multi.finance.entity.RouteArea;
import com.multi.finance.enums.DeliveryMode;
import com.multi.finance.enums.DeliveryRunStatus;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.DeliveryRunRepository;
import com.multi.finance.repository.RouteAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lorry rounds, and the bills that travelled on them.
 *
 * <p>The point of the open run is that the accountant answers "which round?" once,
 * not once per bill. Fifteen to twenty bills for the same area follow one another, so
 * the run stays selected until it is closed — and the screen shows which one it is,
 * loudly, because a sticky default that goes unnoticed is how bills land on the wrong
 * lorry.
 */
@Service
@RequiredArgsConstructor
public class DeliveryRunServiceImpl {

    private final DeliveryRunRepository runRepository;
    private final RouteAreaRepository areaRepository;
    private final BillRepository billRepository;

    // ── Areas ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RouteArea> areas(boolean includeInactive) {
        return includeInactive
                ? areaRepository.findAllByOrderBySortOrderAscNameAsc()
                : areaRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public RouteArea saveArea(RouteArea area) {
        if (area.getName() == null || area.getName().isBlank()) {
            throw new RuntimeException("A route needs a name.");
        }
        area.setName(area.getName().trim());
        areaRepository.findByNameIgnoreCase(area.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(area.getId())) {
                throw new RuntimeException("There is already a route called " + existing.getName() + ".");
            }
        });
        return areaRepository.save(area);
    }

    // ── Runs ─────────────────────────────────────────────────────────

    /**
     * Opens a round. Refuses a second open run for the same area and date, which would
     * split one lorry load across two records and make the counts meaningless.
     */
    @Transactional
    public DeliveryRunResponse open(OpenRunRequest req, String by) {
        List<Long> ids = req.getRouteAreaIds();
        if (ids == null || ids.isEmpty()) {
            throw new RuntimeException("Pick at least one route for this run.");
        }
        List<RouteArea> areas = areaRepository.findAllById(ids);
        if (areas.size() != ids.size()) throw new RuntimeException("Route not found");

        LocalDate date = req.getPlannedDate() != null ? req.getPlannedDate() : LocalDate.now();

        // An area already out on an open run that day would have its bills split across
        // two loads, which is exactly what the counts are meant to prevent.
        for (RouteArea a : areas) {
            runRepository.findOpenCovering(a.getId(), date).stream().findFirst()
                    .ifPresent(existing -> {
                        throw new RuntimeException(
                                a.getName() + " is already on an open run for " + date
                              + " (" + existing.areaLabel() + "). Add the bills to that one, "
                              + "or close it first.");
                    });
        }

        // A round planned for the end of one month often leaves at the start of the
        // next; the month it counts against is a separate fact from the date it left.
        LocalDate month = req.getRunMonth() != null
                ? req.getRunMonth().withDayOfMonth(1)
                : date.withDayOfMonth(1);

        DeliveryRun run = DeliveryRun.builder()
                .areas(new java.util.LinkedHashSet<>(areas))
                .plannedDate(date)
                .runMonth(month)
                .status(DeliveryRunStatus.OPEN)
                .notes(req.getNotes())
                .openedBy(by)
                .openedAt(LocalDateTime.now())
                .build();
        return toResponse(runRepository.save(run), false);
    }

    /** The run this accountant is entering bills into, if any. */
    @Transactional(readOnly = true)
    public DeliveryRunResponse currentFor(String user) {
        List<DeliveryRun> open = runRepository.findOpenFor(user);
        return open.isEmpty() ? null : toResponse(open.get(0), false);
    }

    @Transactional
    public DeliveryRunResponse setStatus(Long id, DeliveryRunStatus status, String by) {
        DeliveryRun run = runRepository.findByIdWithArea(id)
                .orElseThrow(() -> new RuntimeException("Run not found"));
        run.setStatus(status);
        if (status != DeliveryRunStatus.OPEN) {
            run.setClosedBy(by);
            run.setClosedAt(LocalDateTime.now());
        }
        return toResponse(runRepository.save(run), false);
    }

    /**
     * @param month narrows to the rounds counting against that month, whatever date
     *              they actually went out on
     */
    @Transactional(readOnly = true)
    public List<DeliveryRunResponse> list(LocalDate from, LocalDate to, LocalDate month) {
        if (month != null) {
            LocalDate m = month.withDayOfMonth(1);
            return runRepository.findByMonth(m).stream()
                    .map(r -> toResponse(r, false)).toList();
        }
        LocalDate f = from != null ? from : LocalDate.now().minusMonths(2);
        LocalDate t = to != null ? to : LocalDate.now().plusMonths(1);
        return runRepository.findBetween(f, t).stream()
                .map(r -> toResponse(r, false)).toList();
    }

    /**
     * A month by business: billed, collected, still out.
     *
     * <p>Admin only. It answers the question a round cannot on its own — a single
     * lorry's counts say nothing about whether the month is going well, and the
     * businesses are judged separately because they are stocked and settled separately.
     *
     * @param month any date inside the month
     * @param mode  narrow to one way of delivering, or null for everything
     */
    @Transactional(readOnly = true)
    public List<MonthBusinessSummary> monthSummary(LocalDate month, DeliveryMode mode) {
        LocalDate m = (month != null ? month : LocalDate.now()).withDayOfMonth(1);
        return billRepository.monthSalesByBusiness(m, mode == null ? null : mode.name())
                .stream()
                .map(row -> MonthBusinessSummary.builder()
                        .business(String.valueOf(row[0]))
                        .billCount(((Number) row[1]).longValue())
                        .sales(toMoney(row[2]))
                        .paid(toMoney(row[3]))
                        .pending(toMoney(row[4]))
                        .build())
                .toList();
    }

    private BigDecimal toMoney(Object v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    /** The detail an admin checks the lorry against: every bill, every customer. */
    @Transactional(readOnly = true)
    public DeliveryRunResponse detail(Long id) {
        DeliveryRun run = runRepository.findByIdWithArea(id)
                .orElseThrow(() -> new RuntimeException("Run not found"));
        return toResponse(run, true);
    }

    /**
     * Bills the accountant could still add to this round.
     *
     * <p>The common case this exists for: fifteen bills were entered before anyone
     * opened the run, or one was entered after it was closed. Rather than re-keying
     * them, they are picked from here.
     */
    @Transactional(readOnly = true)
    public List<com.multi.finance.dto.response.BillResponse> candidatesFor(Long runId) {
        DeliveryRun run = runRepository.findByIdWithArea(runId)
                .orElseThrow(() -> new RuntimeException("Run not found"));

        // A fortnight either side of the round: wide enough for bills entered late,
        // narrow enough that the list stays something a person can read.
        LocalDate from = run.getPlannedDate().minusDays(14);
        LocalDate to   = run.getPlannedDate().plusDays(14);

        return billRepository.findRunCandidates(from, to).stream()
                .map(this::billRow).toList();
    }

    /**
     * Moves bills onto a run after the fact — for the ones entered before the round was
     * decided, and for fixing a bill that joined the wrong one.
     */
    @Transactional
    public int assignBills(Long runId, List<Long> billIds) {
        DeliveryRun run = runRepository.findByIdWithArea(runId)
                .orElseThrow(() -> new RuntimeException("Run not found"));
        if (run.getStatus() == DeliveryRunStatus.CANCELLED) {
            throw new RuntimeException("That run was cancelled — bills cannot be added to it.");
        }

        List<Bill> bills = billRepository.findAllById(billIds);
        for (Bill b : bills) {
            b.setDeliveryRun(run);
            b.setDeliveryMode(DeliveryMode.ROUTE);
            // The bill keeps its own area. A run covering three rounds cannot say which
            // one a given bill belongs to, and overwriting would lose the customer's.
            b.setUpdatedAt(LocalDateTime.now());
        }
        billRepository.saveAll(bills);
        return bills.size();
    }

    /** Takes a bill off a run without changing anything else about it. */
    @Transactional
    public void removeBill(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        bill.setDeliveryRun(null);
        bill.setDeliveryMode(DeliveryMode.UNSPECIFIED);
        bill.setUpdatedAt(LocalDateTime.now());
        billRepository.save(bill);
    }

    // ── Mapping ──────────────────────────────────────────────────────

    private DeliveryRunResponse toResponse(DeliveryRun run, boolean withBills) {
        List<Bill> bills = billRepository.findByDeliveryRunId(run.getId());

        Set<String> customers = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Bill b : bills) {
            if (b.getCustomerName() != null) customers.add(b.getCustomerName().trim().toUpperCase());
            if (b.getTotalAmount() != null) total = total.add(b.getTotalAmount());
        }

        return DeliveryRunResponse.builder()
                .id(run.getId())
                .routeAreaIds(run.getAreas().stream().map(RouteArea::getId).toList())
                .areaName(run.areaLabel())
                .areaNames(run.getAreas().stream().map(RouteArea::getName).sorted().toList())
                .plannedDate(run.getPlannedDate())
                .runMonth(run.getRunMonth())
                .status(run.getStatus().name())
                .notes(run.getNotes())
                .openedBy(run.getOpenedBy())
                .openedAt(run.getOpenedAt())
                .closedBy(run.getClosedBy())
                .closedAt(run.getClosedAt())
                .billCount(bills.size())
                .customerCount(customers.size())
                .totalValue(total)
                .bills(withBills ? bills.stream().map(this::billRow).toList() : null)
                .build();
    }

    /** A slim bill row — the run view needs identity and money, not the whole record. */
    private com.multi.finance.dto.response.BillResponse billRow(Bill b) {
        return com.multi.finance.dto.response.BillResponse.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .business(b.getBusiness())
                .customerName(b.getCustomerName())
                .area(b.getArea())
                .totalAmount(b.getTotalAmount())
                .amountPaid(b.getAmountPaid())
                .balanceRemaining(b.getBalanceRemaining())
                .status(b.getStatus())
                .billDate(b.getBillDate())
                .build();
    }
}
