package com.multi.finance.service.impl;

import com.multi.finance.dto.response.AccountantDashboardResponse;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.dto.response.BillSummaryResponse;
import com.multi.finance.dto.response.OwnerDashboardResponse;
import com.multi.finance.dto.response.PaymentSummaryResponse;
import com.multi.finance.dto.response.ShopDashboardResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;

    private static final List<BillStatus> SHOP_STATUSES = List.of(
            BillStatus.SHOP_WORKER_ASSIGNED,
            BillStatus.SHOP_RECEIVED
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
                .build();
    }

    @Transactional(readOnly = true)
    public OwnerDashboardResponse getOwnerDashboard(BusinessType business) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        return OwnerDashboardResponse.builder()
                .unassignedBills(billRepository
                        .countByBusinessAndStatus(business, BillStatus.CREATED))
                .inFieldBills(billRepository
                        .countByBusinessAndStatus(business, BillStatus.ASSIGNED))
                .inShopBills(billRepository
                        .countByBusinessAndStatusIn(business, SHOP_STATUSES))
                .awaitingConfirmation(paymentRepository
                        .countByStatus(PaymentStatus.ENTERED))
                .fullyPaidToday(billRepository
                        .countByBillDateAndStatus(today, BillStatus.COMPLETED))
                .totalOutstanding(billRepository
                        .sumOutstandingByBusiness(business))
                .pendingPayments(paymentRepository
                        .findByStatusOrderByCreatedAtDesc(PaymentStatus.ENTERED)
                        .stream().map(this::toPaymentSummary).toList())
                .unassignedBillList(billRepository
                        .findByBusinessAndStatus(business, BillStatus.CREATED)
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
                .build();
    }


}
