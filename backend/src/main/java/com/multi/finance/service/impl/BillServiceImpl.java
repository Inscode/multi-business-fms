package com.multi.finance.service.impl;

import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.Customer;
import com.multi.finance.entity.User;
import com.multi.finance.entity.Worker;
import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.CustomerRepository;
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
    private final CustomerRepository customerRepository;

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

        // Resolve customer: if customerId provided, use it and derive name from it
        Customer customer = null;
        String customerName = request.getCustomerName();
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
            customerName = customer.getName();
        }

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .business(request.getBusiness())
                .division(request.getDivision())
                .billType(request.getBillType())
                .billSource(request.getBillSource())
                .customerName(customerName)
                .customer(customer)
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
    public List<BillResponse> getAllBills(BusinessType business, BillStatus status,
                                          boolean excludeCompleted, LocalDate from, LocalDate to) {
        User caller = getCurrentUser();
        boolean doExclude = excludeCompleted && status == null;
        List<BillStatus> excluded = List.of(BillStatus.COMPLETED, BillStatus.CANCELLED);

        // true = both from+to provided (BETWEEN), false = only to provided (BEFORE = overdue)
        boolean hasBoth = from != null && to != null;
        boolean toOnly  = from == null && to != null;

        // ── SHOP_ACCOUNTANT ──────────────────────────────────────────
        if (caller.getRole() == UserRole.SHOP_ACCOUNTANT) {
            if (toOnly) return billRepository.findShopAccountantOverdueBills(to).stream().map(this::toResponse).toList();
            if (hasBoth) {
                if (status != null) return billRepository.findShopAccountantBillsByStatusInDateRange(status, from, to).stream().map(this::toResponse).toList();
                if (doExclude)      return billRepository.findShopAccountantActiveBillsInDateRange(from, to).stream().map(this::toResponse).toList();
                return billRepository.findShopAccountantBillsInDateRange(from, to).stream().map(this::toResponse).toList();
            }
            if (status != null) return billRepository.findShopAccountantBillsByStatus(status).stream().map(this::toResponse).toList();
            if (doExclude)      return billRepository.findShopAccountantActiveBills().stream().map(this::toResponse).toList();
            return billRepository.findShopAccountantBills().stream().map(this::toResponse).toList();
        }

        List<BusinessType> allowed = getAllowedBusinessTypes(caller.getRole());

        // ── ACCOUNTANT / MAIN_ACCOUNTANT ─────────────────────────────
        if (allowed != null) {
            if (business != null && !allowed.contains(business)) return List.of();
            List<BusinessType> scope = (business != null) ? List.of(business) : allowed;
            if (toOnly) return billRepository.findByBusinessInAndBillDateBeforeAndStatusNotInOrderByBillDateAsc(scope, to, excluded).stream().map(this::toResponse).toList();
            if (hasBoth) {
                if (status != null) return billRepository.findByBusinessInAndStatusAndBillDateBetweenOrderByCreatedAtDesc(scope, status, from, to).stream().map(this::toResponse).toList();
                if (doExclude)      return billRepository.findByBusinessInAndBillDateBetweenAndStatusNotInOrderByCreatedAtDesc(scope, from, to, excluded).stream().map(this::toResponse).toList();
                return billRepository.findByBusinessInAndBillDateBetweenOrderByCreatedAtDesc(scope, from, to).stream().map(this::toResponse).toList();
            }
            if (status != null) return billRepository.findByBusinessInAndStatusOrderByCreatedAtDesc(scope, status).stream().map(this::toResponse).toList();
            if (doExclude)      return billRepository.findByBusinessInAndStatusNotInOrderByCreatedAtDesc(scope, excluded).stream().map(this::toResponse).toList();
            return billRepository.findByBusinessInOrderByCreatedAtDesc(scope).stream().map(this::toResponse).toList();
        }

        // ── ADMIN / OWNER — unrestricted ─────────────────────────────
        if (toOnly) {
            if (business != null) return billRepository.findByBusinessAndBillDateBeforeAndStatusNotInOrderByBillDateAsc(business, to, excluded).stream().map(this::toResponse).toList();
            return billRepository.findByBillDateBeforeAndStatusNotInOrderByBillDateAsc(to, excluded).stream().map(this::toResponse).toList();
        }
        if (hasBoth) {
            if (business != null && status != null) return billRepository.findByBusinessAndStatusAndBillDateBetweenOrderByCreatedAtDesc(business, status, from, to).stream().map(this::toResponse).toList();
            if (business != null && doExclude)      return billRepository.findByBusinessAndBillDateBetweenAndStatusNotInOrderByCreatedAtDesc(business, from, to, excluded).stream().map(this::toResponse).toList();
            if (business != null)                   return billRepository.findByBusinessAndBillDateBetweenOrderByCreatedAtDesc(business, from, to).stream().map(this::toResponse).toList();
            if (status != null)                     return billRepository.findByStatusAndBillDateBetweenOrderByCreatedAtDesc(status, from, to).stream().map(this::toResponse).toList();
            if (doExclude)                          return billRepository.findByBillDateBetweenAndStatusNotInOrderByCreatedAtDesc(from, to, excluded).stream().map(this::toResponse).toList();
            return billRepository.findByBillDateBetweenOrderByCreatedAtDesc(from, to).stream().map(this::toResponse).toList();
        }
        // No date filter — existing behaviour
        if (business != null && status != null) return billRepository.findByBusinessAndStatusOrderByCreatedAtDesc(business, status).stream().map(this::toResponse).toList();
        if (business != null && doExclude)      return billRepository.findByBusinessAndStatusNotInOrderByCreatedAtDesc(business, excluded).stream().map(this::toResponse).toList();
        if (business != null)                   return billRepository.findByBusinessOrderByCreatedAtDesc(business).stream().map(this::toResponse).toList();
        if (status != null)                     return billRepository.findByStatusOrderByCreatedAtDesc(status).stream().map(this::toResponse).toList();
        if (doExclude)                          return billRepository.findByStatusNotInOrderByCreatedAtDesc(excluded).stream().map(this::toResponse).toList();
        return billRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long countOverduePending(LocalDate cutoff) {
        User caller = getCurrentUser();
        List<BillStatus> excluded = List.of(BillStatus.COMPLETED, BillStatus.CANCELLED);
        if (caller.getRole() == UserRole.SHOP_ACCOUNTANT) {
            return billRepository.countShopAccountantOverdueBills(cutoff);
        }
        List<BusinessType> allowed = getAllowedBusinessTypes(caller.getRole());
        if (allowed != null) {
            return billRepository.countByBusinessInAndBillDateBeforeAndStatusNotIn(allowed, cutoff, excluded);
        }
        return billRepository.countByBillDateBeforeAndStatusNotIn(cutoff, excluded);
    }

    /**
     * Returns the business types visible to the given role.
     * Returns null for ADMIN/OWNER (unrestricted) and for SHOP_ACCOUNTANT
     * (their query is special — handled separately via findShopAccountantBills).
     */
    private List<BusinessType> getAllowedBusinessTypes(UserRole role) {
        return switch (role) {
            case SHOP_ACCOUNTANT  -> null; // handled by dedicated query
            case ACCOUNTANT, MAIN_ACCOUNTANT -> List.of(
                    BusinessType.RAINCO, BusinessType.STATIONERY,
                    BusinessType.PLASTIC, BusinessType.HARDWARE);
            case ADMIN, OWNER -> null; // unrestricted
            case WORKER -> List.of(); // workers don't access the main bill list
        };
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

    @Transactional
    public BillResponse cancelBill(Long id) {
        Bill bill = findBillById(id);
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Bill is already cancelled");
        }
        if (bill.getStatus() == BillStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel a completed bill");
        }
        bill.setStatus(BillStatus.CANCELLED);
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public BillResponse markCompleted(Long id) {
        Bill bill = findBillById(id);
        if (bill.getStatus() == BillStatus.COMPLETED) {
            throw new RuntimeException("Bill is already completed");
        }
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Cannot complete a cancelled bill");
        }
        bill.setStatus(BillStatus.COMPLETED);
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public BillResponse updateBill(Long id, BillRequest request) {
        Bill bill = findBillById(id);

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
            bill.setCustomer(customer);
            bill.setCustomerName(customer.getName());
        } else if (request.getCustomerName() != null && !request.getCustomerName().isBlank()) {
            bill.setCustomerName(request.getCustomerName());
        }
        if (request.getArea() != null) {
            bill.setArea(request.getArea());
        }
        if (request.getBillDate() != null) {
            bill.setBillDate(request.getBillDate());
        }
        if (request.getNotes() != null) {
            bill.setNotes(request.getNotes());
        }
        if (request.getBusiness() != null) {
            bill.setBusiness(request.getBusiness());
        }
        if (request.getDivision() != null && !request.getDivision().isBlank()) {
            bill.setDivision(request.getDivision());
        }
        if (request.getBillType() != null) {
            bill.setBillType(request.getBillType());
        }
        if (request.getTotalAmount() != null) {
            bill.setTotalAmount(request.getTotalAmount());
            BigDecimal newBalance = request.getTotalAmount().subtract(bill.getAmountPaid());
            bill.setBalanceRemaining(newBalance.max(BigDecimal.ZERO));
            bill.setFullyPaid(newBalance.compareTo(BigDecimal.ZERO) <= 0);
        }
        if (request.getWorkerId() != null) {
            Worker worker = workerRepository.findById(request.getWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
            bill.setCurrentHolder(worker);
        }

        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public void deleteBill(Long id) {
        Bill bill = findBillById(id);

        boolean hasConfirmed = paymentRepository.existsByBillIdAndStatus(bill.getId(), com.multi.finance.enums.PaymentStatus.CONFIRMED);
        if (hasConfirmed) {
            throw new RuntimeException("Cannot delete a bill that has confirmed payments");
        }

        paymentRepository.deleteAll(paymentRepository.findByBillId(bill.getId()));
        billRepository.delete(bill);
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
                .customerId(bill.getCustomer() != null ? bill.getCustomer().getId() : null)
                .totalAmount(bill.getTotalAmount())
                .fullyPaid(bill.getFullyPaid())
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