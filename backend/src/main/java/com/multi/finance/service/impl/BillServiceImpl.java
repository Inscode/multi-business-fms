package com.multi.finance.service.impl;

import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.User;
import com.multi.finance.entity.Worker;
import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.PaymentRepository;
import com.multi.finance.repository.UserRepository;
import com.multi.finance.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillServiceImpl {

    private final BillRepository billRepository;
    private final WorkerRepository workerRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public BillResponse createBill(BillRequest request) {
        User currentUser = getCurrentUser();

        String division = request.getDivision();
        switch (currentUser.getRole()) {
            case ACCOUNTANT    -> division = "STORE";
            case SHOP_ACCOUNTANT -> division = "SHOP";
            default -> {
                if (division == null || division.isBlank()) {
                    throw new RuntimeException("Division is required");
                }
            }
        }

        String billNumber = switch (request.getBillSource()) {
            case DRAFT  -> generateDraftNumber();
            case SYSTEM -> "SYS-" + request.getBillNumber();
            case MANUAL -> "MAN-" + request.getBillNumber();
        };

        Worker worker = null;
        if (request.getWorkerId() != null) {
            worker = workerRepository.findById(request.getWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
        }

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .business(request.getBusiness())
                .division(request.getDivision())
                .billType(request.getBillType())
                .billSource(request.getBillSource())
                .customerName(request.getCustomerName())
                .totalAmount(request.getTotalAmount())
                .area(request.getArea())
                .amountPaid(BigDecimal.ZERO)
                .balanceRemaining(request.getTotalAmount())
                .fullyPaid(false)
                .status( worker != null
                        ? BillStatus.ASSIGNED
                        : BillStatus.CREATED)
                .currentHolder(worker)
                .enteredBy(currentUser)
                .billDate(request.getBillDate() != null ?
                        request.getBillDate() : LocalDate.now())
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toResponse(billRepository.save(bill));
    }

    @Transactional(readOnly = true)
    public List<BillResponse> getAllBills(BusinessType business, BillStatus status) {
        if (business != null && status != null) {
            return billRepository
                    .findByBusinessAndStatusOrderByCreatedAtDesc(business, status)
                    .stream().map(this::toResponse).toList();
        }
        if (business != null) {
            return billRepository
                    .findByBusinessOrderByCreatedAtDesc(business)
                    .stream().map(this::toResponse).toList();
        }
        if (status != null) {
            return billRepository
                    .findByStatusOrderByCreatedAtDesc(status)
                    .stream().map(this::toResponse).toList();
        }
        return billRepository
                .findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly=true)
    public List<BillResponse> getTodaysBills(BusinessType business) {
        return billRepository
                .findByBusinessAndBillDateAndStatusNot(
                        business, LocalDate.now(), BillStatus.CANCELLED)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly=true)
    public List<BillResponse> getUnconfirmedBills(BusinessType business) {
        return billRepository
                .findUnconfirmedByBusinessAndDate(business, LocalDate.now())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public BillResponse assignBill(Long id, AssignBillRequest request) {
        Bill bill = findBillById(id);
        User caller = getCurrentUser();

        if (bill.getStatus() == BillStatus.COMPLETED ||
                bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException(
                    "Cannot assign a completed or cancelled bill");
        }

        if (caller.getRole() == UserRole.SHOP_ACCOUNTANT) {
            List<BillStatus> allowed = List.of(
                    BillStatus.ASSIGNED,
                    BillStatus.SHOP_RECEIVED,
                    BillStatus.SHOP_WORKER_ASSIGNED
            );
            if (!allowed.contains(bill.getStatus())) {
                throw new RuntimeException(
                        "Shop accountant can only assign bills with status: ASSIGNED, SHOP_RECEIVED or SHOP_WORKER_ASSIGNED");
            }
        }

        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        BillStatus newStatus = (caller.getRole() == UserRole.SHOP_ACCOUNTANT)
                ? BillStatus.SHOP_WORKER_ASSIGNED
                : BillStatus.ASSIGNED;

        bill.setCurrentHolder(worker);
        bill.setStatus(newStatus);
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public BillResponse markShopReceived(Long id) {
        Bill bill = findBillById(id);
        User caller = getCurrentUser();

        if (bill.getStatus() == BillStatus.COMPLETED ||
                bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Cannot change status of a completed or cancelled bill");
        }

        switch (caller.getRole()) {
            case SHOP_ACCOUNTANT -> {
                if (bill.getStatus() != BillStatus.ASSIGNED &&
                        bill.getStatus() != BillStatus.SHOP_WORKER_ASSIGNED) {
                    throw new RuntimeException(
                            "Shop accountant can only mark ASSIGNED or SHOP_WORKER_ASSIGNED bills as shop received");
                }
            }
            case ACCOUNTANT, MAIN_ACCOUNTANT, ADMIN -> {
                // allowed from any non-terminal status — already checked above
            }
            default -> throw new RuntimeException("Not authorized to mark bill as shop received");
        }

        bill.setStatus(BillStatus.SHOP_RECEIVED);
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public BillResponse markReceived(Long id) {
        Bill bill = findBillById(id);

        if (bill.getStatus() != BillStatus.ASSIGNED &&
                bill.getStatus() != BillStatus.SHOP_RECEIVED) {
            throw new RuntimeException(
                    "Bill must be ASSIGNED or SHOP_RECEIVED to mark as store received");
        }

        bill.setStatus(BillStatus.STORE_RECEIVED);
        bill.setReceivedBy(getCurrentUser());
        bill.setReceivedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional(readOnly = true)
    private Bill findBillById(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
    }

    @Transactional(readOnly = true)
    public BillResponse getBillById(Long id) {
        return toResponse(findBillById(id));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private String generateDraftNumber() {
        long count = billRepository.countAllDrafts();
        return String.format("DFT-%04d", count + 1);
    }


    private BillResponse toResponse(Bill bill) {
        return BillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .business(bill.getBusiness())
                .division(bill.getDivision())
                .billType(bill.getBillType())
                .amountPaid(bill.getAmountPaid())
                .billSource(bill.getBillSource())
                .area(bill.getArea())
                .balanceRemaining(bill.getBalanceRemaining())
                .customerName(bill.getCustomerName())
                .totalAmount(bill.getTotalAmount())
                .status(bill.getStatus())
                .workerId(bill.getCurrentHolder() != null ? bill.getCurrentHolder().getId() : null)
                .workerName(bill.getCurrentHolder() != null ? bill.getCurrentHolder().getFullName() : null)
                .enteredByName(bill.getEnteredBy() != null ? bill.getEnteredBy().getFullName() : null)
                .receivedByName(bill.getReceivedBy() != null ? bill.getReceivedBy().getFullName() : null)
                .receivedAt(bill.getReceivedAt())
                .billDate(bill.getBillDate())
                .notes(bill.getNotes())
                .createdAt(bill.getCreatedAt())
                .build();
    }
}