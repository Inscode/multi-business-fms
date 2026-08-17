package com.multi.finance.service.impl;

import com.multi.finance.dto.response.AccountantDashboardResponse;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.dto.response.BillReminderResponse;
import com.multi.finance.dto.response.BillSummaryResponse;
import com.multi.finance.dto.response.ChequeDetailEntry;
import com.multi.finance.dto.response.CollectionHealthResponse;
import com.multi.finance.dto.response.CollectorPerformanceEntry;
import com.multi.finance.dto.response.CustomerRiskEntry;
import com.multi.finance.dto.response.DsoTrendEntry;
import com.multi.finance.dto.response.OwnerDashboardResponse;
import com.multi.finance.dto.response.PaymentMixEntry;
import com.multi.finance.dto.response.PaymentSummaryResponse;
import com.multi.finance.dto.response.ShopDashboardResponse;
import com.multi.finance.dto.response.StaleChequeEntry;
import com.multi.finance.dto.response.UpcomingChequeEntry;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.Payment;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final BillReminderServiceImpl reminderService;
    private final CustomerHealthService customerHealthService;

    private static final List<BillStatus> SHOP_STATUSES = List.of(
            BillStatus.SHOP_WORKER_ASSIGNED,
            BillStatus.SHOP_RECEIVED
    );

    private static final List<BillStatus> PENDING_STATUSES = List.of(
            BillStatus.CREATED, BillStatus.ASSIGNED,
            BillStatus.SHOP_RECEIVED, BillStatus.SHOP_WORKER_ASSIGNED,
            BillStatus.STORE_RECEIVED
    );

    private static final List<BillStatus> SHOP_DASHBOARD_STATUSES = List.of(
            BillStatus.ASSIGNED,
            BillStatus.SHOP_RECEIVED,
            BillStatus.SHOP_WORKER_ASSIGNED
    );

    @Transactional(readOnly = true)
    public AccountantDashboardResponse getAccountantDashboard() {
        LocalDate today = LocalDate.now();

        return AccountantDashboardResponse.builder()
                .totalBillsToday(billRepository
                        .countByBillDateAndStatusNot(today, BillStatus.CANCELLED))
                .assignedBills(billRepository
                        .countByStatus(BillStatus.ASSIGNED))
                .inShopBills(billRepository
                        .countByStatusIn(SHOP_STATUSES))
                .receivedBills(billRepository
                        .countByBillDateAndStatus(today, BillStatus.STORE_RECEIVED))
                .pendingPayments(paymentRepository
                        .countByStatus(PaymentStatus.ENTERED))
                .recentBills(billRepository.findTop5ByOrderByCreatedAtDesc()
                        .stream().map(this::toBillSummary).toList())
                .unassignedBills(billRepository.findByStatus(BillStatus.CREATED)
                        .stream().map(this::toBillSummary).toList())
                .todayReminders(reminderService.getTodayAndOverdue())
                .build();
    }

    @Transactional(readOnly = true)
    public OwnerDashboardResponse getOwnerDashboard(BusinessType business) {
        LocalDate today = LocalDate.now();

        return OwnerDashboardResponse.builder()
                .assignedBills(billRepository
                        .countByBusinessAndStatus(business, BillStatus.ASSIGNED))
                .inFieldBills(billRepository
                        .countByBusinessAndStatus(business, BillStatus.ASSIGNED))
                .inShopBills(billRepository
                        .countByBusinessAndStatusIn(business, SHOP_STATUSES))
                .awaitingConfirmation(paymentRepository
                        .countByStatus(PaymentStatus.ENTERED))
                .pendingBills(billRepository
                        .countByBusinessAndStatusIn(business, PENDING_STATUSES))
                .totalOutstanding(billRepository
                        .sumOutstandingByBusinessExcludingLinking(business))
                // Today's bills by business
                .raincoTodayBills(billRepository
                        .countByBusinessAndBillDateAndStatusNot(BusinessType.RAINCO, today, BillStatus.CANCELLED))
                .stationeryTodayBills(billRepository
                        .countByBusinessAndBillDateAndStatusNot(BusinessType.STATIONERY, today, BillStatus.CANCELLED))
                .plasticTodayBills(billRepository
                        .countByBusinessAndBillDateAndStatusNot(BusinessType.PLASTIC, today, BillStatus.CANCELLED))
                // Today's payments by business
                .raincoTodayPayments(paymentRepository
                        .countByPaymentDateAndBillBusiness(today, BusinessType.RAINCO))
                .stationeryTodayPayments(paymentRepository
                        .countByPaymentDateAndBillBusiness(today, BusinessType.STATIONERY))
                .plasticTodayPayments(paymentRepository
                        .countByPaymentDateAndBillBusiness(today, BusinessType.PLASTIC))
                .cashPending(billRepository.sumCashPendingByBusiness(business))
                .cashSerious(billRepository.sumCashSeriousByBusiness(business, today.minusDays(15)))
                .pendingPayments(paymentRepository
                        .findByStatusOrderByCreatedAtDesc(PaymentStatus.ENTERED)
                        .stream().map(this::toPaymentSummary).toList())
                .assignedBillList(billRepository
                        .findByBusinessAndStatus(business, BillStatus.ASSIGNED)
                        .stream().map(this::toBillSummary).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public ShopDashboardResponse getShopDashboard() {
        List<BillResponse> bills = billRepository
                .findByStatusInOrderByCreatedAtDesc(SHOP_DASHBOARD_STATUSES)
                .stream().map(this::toBillResponse).toList();

        return ShopDashboardResponse.builder()
                .shopReceivedBills(billRepository
                        .countByStatus(BillStatus.SHOP_RECEIVED))
                .shopWorkerAssignedBills(billRepository
                        .countByStatus(BillStatus.SHOP_WORKER_ASSIGNED))
                .pendingPayments(paymentRepository
                        .countByPaymentStatusAndBillStatus(
                                PaymentStatus.ENTERED, BillStatus.SHOP_RECEIVED))
                .bills(bills)
                .build();
    }

    @Transactional(readOnly = true)
    public CollectionHealthResponse getCollectionHealth(BusinessType business) {
        LocalDate today = LocalDate.now();

        return CollectionHealthResponse.builder()
                .dsoTrend(buildDsoTrend(business, today))
                .collectorLeaderboard(buildCollectorLeaderboard(business, today))
                .upcomingCheques(buildUpcomingCheques(business, today))
                .staleCheques(buildStaleCheques(business, today))
                .riskyCustomers(buildRiskyCustomers(business))
                .paymentMix(buildPaymentMix(business, today))
                .build();
    }

    private List<PaymentMixEntry> buildPaymentMix(BusinessType business, LocalDate today) {
        LocalDate from = today.minusDays(30);
        return paymentRepository.findPaymentMixByDate(business.name(), from, today).stream()
                .map(r -> {
                    BigDecimal cash = toBigDecimal(r[1]);
                    BigDecimal cheque = toBigDecimal(r[2]);
                    BigDecimal bankTransfer = toBigDecimal(r[3]);
                    return PaymentMixEntry.builder()
                            .date(toLocalDate(r[0]))
                            .cash(cash)
                            .cheque(cheque)
                            .bankTransfer(bankTransfer)
                            .total(cash.add(cheque).add(bankTransfer))
                            .build();
                })
                .toList();
    }

    /** Avg days-to-collect for settled credit bills, bucketed by the month they were issued — a DSO proxy. */
    private List<DsoTrendEntry> buildDsoTrend(BusinessType business, LocalDate today) {
        LocalDate cutoff = today.minusMonths(6).withDayOfMonth(1);
        List<Bill> settled = billRepository.findSettledCreditBillsSince(business, cutoff);
        if (settled.isEmpty()) return List.of();

        List<Long> billIds = settled.stream().map(Bill::getId).toList();
        Map<Long, LocalDate> lastPaymentByBill = paymentRepository.findLastConfirmedDatesByBillIds(billIds)
                .stream().collect(java.util.stream.Collectors.toMap(
                        r -> ((Number) r[0]).longValue(),
                        r -> (LocalDate) r[1]));

        Map<YearMonth, List<Long>> daysByMonth = new TreeMap<>();
        for (Bill b : settled) {
            LocalDate lastPayment = lastPaymentByBill.get(b.getId());
            if (lastPayment == null) continue;
            long days = ChronoUnit.DAYS.between(b.getBillDate(), lastPayment);
            daysByMonth.computeIfAbsent(YearMonth.from(b.getBillDate()), k -> new java.util.ArrayList<>())
                    .add(days);
        }

        return daysByMonth.entrySet().stream()
                .map(e -> DsoTrendEntry.builder()
                        .month(e.getKey().toString())
                        .avgCollectionDays(e.getValue().stream().mapToLong(Long::longValue).average().orElse(0))
                        .settledBillCount(e.getValue().size())
                        .build())
                .toList();
    }

    private List<CollectorPerformanceEntry> buildCollectorLeaderboard(BusinessType business, LocalDate today) {
        LocalDate from = today.minusDays(30);
        return paymentRepository.findCollectorPerformance(business.name(), from, today).stream()
                .map(r -> {
                    int paymentCount = toInt(r[3]);
                    int partialCount = toInt(r[5]);
                    return CollectorPerformanceEntry.builder()
                            .workerId(((Number) r[0]).longValue())
                            .workerName((String) r[1])
                            .totalCollected(toBigDecimal(r[2]))
                            .paymentCount(paymentCount)
                            .avgDaysToCollect(toDouble(r[4]))
                            .partialRate(paymentCount == 0 ? 0 : (double) partialCount / paymentCount)
                            .build();
                })
                .toList();
    }

    private List<UpcomingChequeEntry> buildUpcomingCheques(BusinessType business, LocalDate today) {
        return getUpcomingCheques(business, today, today.plusDays(30));
    }

    /** Upcoming cheques for an arbitrary date range — used by the dashboard's default 30-day view and the custom range picker. */
    @Transactional(readOnly = true)
    public List<UpcomingChequeEntry> getUpcomingCheques(BusinessType business, LocalDate from, LocalDate to) {
        return paymentRepository.findUpcomingChequeTotals(business.name(), from, to).stream()
                .map(r -> UpcomingChequeEntry.builder()
                        .chequeDate(toLocalDate(r[0]))
                        .totalAmount(toBigDecimal(r[1]))
                        .count(toInt(r[2]))
                        .build())
                .toList();
    }

    public List<ChequeDetailEntry> getChequeDetails(BusinessType business, LocalDate date) {
        return paymentRepository.findChequeDetailsByDate(business.name(), date).stream()
                .map(r -> ChequeDetailEntry.builder()
                        .customerName((String) r[0])
                        .billNumber((String) r[1])
                        .bankName((String) r[2])
                        .chequeNumber((String) r[3])
                        .amount(toBigDecimal(r[4]))
                        .status((String) r[5])
                        .build())
                .toList();
    }

    private List<StaleChequeEntry> buildStaleCheques(BusinessType business, LocalDate today) {
        return paymentRepository.findStaleCheques(business.name(), today).stream()
                .map(r -> StaleChequeEntry.builder()
                        .chequeDate(toLocalDate(r[0]))
                        .amount(toBigDecimal(r[1]))
                        .customerName((String) r[2])
                        .billNumber((String) r[3])
                        .build())
                .toList();
    }

    /**
     * The customers worth being careful with, worst first.
     *
     * <p>Reads the same ratings as a customer's own record, so the dashboard and the
     * record cannot say different things about the same shop. The score it used before —
     * partials plus twice the returned cheques — had no time in it and no denominator,
     * which put a customer of sixty bills and two late ones above one with two bills and
     * a bounced cheque.
     */
    private List<CustomerRiskEntry> buildRiskyCustomers(BusinessType business) {
        return customerHealthService.forBusiness(business).stream()
                // Only the ones there is something to say about. A list that includes
                // everybody is a list nobody reads.
                .filter(h -> !CustomerHealthService.GOOD.equals(h.getOverallRating()))
                .map(h -> {
                    var b = h.getBusinesses().stream()
                            .filter(x -> business.name().equals(x.getBusiness()))
                            .findFirst().orElse(null);
                    if (b == null) return null;
                    return CustomerRiskEntry.builder()
                            .customerId(h.getCustomerId())
                            .customerName(h.getCustomerName())
                            .area(h.getArea())
                            .rating(b.getRating())
                            .reasons(b.getReasons())
                            .currentOutstanding(b.getCurrentOutstanding())
                            .overdueAmount(b.getOverdueAmount())
                            .oldestOpenDays(b.getOldestOpenDays())
                            .avgDaysToSettle(b.getAvgDaysToSettleRecent() != null
                                    ? b.getAvgDaysToSettleRecent() : b.getAvgDaysToSettle())
                            .bouncedChequeCount(b.getBouncedChequeCount())
                            .lastBouncedChequeDate(b.getLastBouncedChequeDate())
                            .partialCount(b.getPartialPaymentCount())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .limit(20)
                .toList();
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    private static int toInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static double toDouble(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof Date sqlDate) return sqlDate.toLocalDate();
        return LocalDate.parse(o.toString());
    }

    private BillSummaryResponse toBillSummary(Bill b) {
        return BillSummaryResponse.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .customerName(b.getCustomerName())
                .totalAmount(b.getTotalAmount())
                .workerName(b.getCurrentHolder() != null
                        ? b.getCurrentHolder().getFullName() : null)
                .status(b.getStatus())
                .build();
    }

    private BillResponse toBillResponse(Bill b) {
        return BillResponse.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .business(b.getBusiness())
                .division(b.getDivision())
                .billType(b.getBillType())
                .billSource(b.getBillSource())
                .customerName(b.getCustomerName())
                .area(b.getArea())
                .totalAmount(b.getTotalAmount())
                .amountPaid(b.getAmountPaid())
                .balanceRemaining(b.getBalanceRemaining())
                .status(b.getStatus())
                .workerId(b.getCurrentHolder() != null ? b.getCurrentHolder().getId() : null)
                .workerName(b.getCurrentHolder() != null ? b.getCurrentHolder().getFullName() : null)
                .enteredByName(b.getEnteredBy() != null ? b.getEnteredBy().getFullName() : null)
                .receivedByName(b.getReceivedBy() != null ? b.getReceivedBy().getFullName() : null)
                .receivedAt(b.getReceivedAt())
                .billDate(b.getBillDate())
                .notes(b.getNotes())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private PaymentSummaryResponse toPaymentSummary(Payment p) {
        return PaymentSummaryResponse.builder()
                .id(p.getId())
                .billNumber(p.getBill().getBillNumber())
                .customerName(p.getBill().getCustomerName())
                .amount(p.getAmount())
                .paymentType(p.getPaymentType().name())
                .enteredByName(p.getEnteredBy() != null
                        ? p.getEnteredBy().getFullName() : null)
                .paymentDate(p.getPaymentDate())
                .status(p.getStatus().name())
                .collectedByOwnerName(p.getCollectionNote() != null
                        ? p.getCollectionNote().getCollectedBy().getFullName() : null)
                .collectedByWorkerName(p.getCollectedByWorker() != null
                        ? p.getCollectedByWorker().getFullName() : null)
                .collectorNote(p.getCollectorNote())
                .receiptImageUrl(p.getReceiptImageUrl())
                .confirmImageUrl(p.getConfirmImageUrl())
                .build();
    }


}
