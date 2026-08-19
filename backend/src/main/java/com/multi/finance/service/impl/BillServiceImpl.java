package com.multi.finance.service.impl;

import com.multi.finance.dto.response.BillDeleteImpact;
import com.multi.finance.repository.DeliveryRunRepository;
import com.multi.finance.enums.DeliveryMode;
import com.multi.finance.entity.DeliveryRun;
import com.multi.finance.service.BillBalance;
import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.request.BulkAssignBillRequest;
import com.multi.finance.dto.request.BulkBillIdsRequest;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.dto.response.SkipReviewResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.BillNumberSkip;
import com.multi.finance.entity.BillReview;
import com.multi.finance.entity.Customer;
import com.multi.finance.entity.User;
import com.multi.finance.entity.Worker;
import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.BillNumberSkipRepository;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.BillReturnRepository;
import com.multi.finance.repository.BillReviewRepository;
import com.multi.finance.repository.CustomerRepository;
import com.multi.finance.repository.PaymentRepository;
import com.multi.finance.repository.UserRepository;
import com.multi.finance.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.multi.finance.enums.BillType;
import com.multi.finance.dto.response.AgingAreaSummary;
import com.multi.finance.dto.response.AgingCustomerEntry;
import com.multi.finance.dto.response.AgingReportResponse;
import com.multi.finance.dto.response.BillSequenceGapResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillServiceImpl {

    private final BillRepository billRepository;
    private final com.multi.finance.repository.CollectionNoteRepository collectionNoteRepository;
    private final com.multi.finance.repository.WorkerPaymentEntryRepository workerPaymentEntryRepository;
    private final WorkerRepository workerRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final BillReviewRepository billReviewRepository;
    private final BillNumberSkipRepository billNumberSkipRepository;
    private final DeliveryRunRepository deliveryRunRepository;
    private final BillReturnRepository billReturnRepository;
    private final com.multi.finance.invoicing.repository.SystemSettingsRepository settingsRepository;

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
            // Raised by the invoicing module, never typed in here.
            case INVOICE -> throw new RuntimeException(
                    "Invoice bills are created from the invoicing module, not entered by hand");
            case DRAFT  -> generateDraftNumber(request.getBusiness());
            case SYSTEM -> {
                String num = "SYS-" + stripLeadingZeros(request.getBillNumber());
                if (billRepository.existsActiveByBillNumberAndBusiness(num, request.getBusiness()))
                    throw new RuntimeException("Bill number " + num + " already exists in this business");
                yield num;
            }
            case MANUAL -> {
                // PLASTIC and STATIONERY use the shared physical book (BK- prefix)
                boolean sharedBook = request.getBusiness() == BusinessType.PLASTIC
                                  || request.getBusiness() == BusinessType.STATIONERY;
                String num = (sharedBook ? "BK-" : "MAN-") + stripLeadingZeros(request.getBillNumber());
                if (billRepository.existsActiveByBillNumberAndBusiness(num, request.getBusiness()))
                    throw new RuntimeException("Bill number " + num + " already exists in this business");
                yield num;
            }
            case MANUAL_BOOK -> {
                // RAINCO uses the shared physical book (BK- prefix)
                String num = "BK-" + stripLeadingZeros(request.getBillNumber());
                if (billRepository.existsActiveByBillNumberAndBusiness(num, request.getBusiness()))
                    throw new RuntimeException("Bill number " + num + " already exists in this business");
                yield num;
            }
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

        // The round decides the area, so a route bill can never disagree with the
        // lorry it went on. Immediate and store pickups keep whatever area was typed.
        DeliveryRun run = null;
        DeliveryMode mode = request.getDeliveryMode() != null
                ? request.getDeliveryMode() : DeliveryMode.UNSPECIFIED;
        if (request.getDeliveryRunId() != null) {
            run = deliveryRunRepository.findByIdWithArea(request.getDeliveryRunId())
                    .orElseThrow(() -> new RuntimeException("Delivery run not found"));
            if (run.getStatus() == com.multi.finance.enums.DeliveryRunStatus.CANCELLED) {
                throw new RuntimeException("That run was cancelled — bills cannot join it.");
            }
            mode = DeliveryMode.ROUTE;
        }

        // With no round open, how the goods went has to be said. Left to default, the
        // bill would sit at UNSPECIFIED — outside every delivery figure, and invisible,
        // since nothing reports a bill nobody classified. A round being open is the one
        // case where it need not be asked: the round already answers it.
        //
        // Drafts are exempt. A draft is a placeholder for a bill not yet written, so
        // there is nothing yet to have delivered.
        if (mode == DeliveryMode.UNSPECIFIED
                && request.getBillSource() != BillSource.DRAFT) {
            throw new RuntimeException(
                    "Say how this bill goes out — immediate or store pickup. Open a "
                  + "delivery run first if it is going on a lorry.");
        }
        // The bill keeps its own area even on a route run: a trip covering Bandarawela,
        // Haputale and Diyatalawa cannot say which of them this customer is in.
        String area = request.getArea() != null ? request.getArea().trim().toUpperCase() : null;

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .business(request.getBusiness())
                .division(request.getDivision())
                .billType(request.getBillType())
                .billSource(request.getBillSource())
                .customerName(customerName)
                .customer(customer)
                .totalAmount(request.getTotalAmount())
                .area(area)
                .deliveryMode(mode)
                .deliveryRun(run)
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

        BillResponse response = toResponse(billRepository.save(bill));

        // Save skipped continuation page numbers for MANUAL / MANUAL_BOOK bills
        // ADMIN → APPROVED immediately; ACC/MAIN_ACCOUNTANT → PENDING (admin reviews in Reviews tab)
        boolean isBookSourceBill = request.getBillSource() == BillSource.MANUAL
                                || request.getBillSource() == BillSource.MANUAL_BOOK;
        boolean canSubmitSkips = isBookSourceBill
                && request.getSkippedBillNumbers() != null
                && !request.getSkippedBillNumbers().isEmpty()
                && (currentUser.getRole() == UserRole.ADMIN
                    || currentUser.getRole() == UserRole.ACCOUNTANT
                    || currentUser.getRole() == UserRole.MAIN_ACCOUNTANT);

        if (canSubmitSkips) {
            String skipStatus = currentUser.getRole() == UserRole.ADMIN ? "APPROVED" : "PENDING";
            Bill savedBill = billRepository.findById(response.getId()).orElse(null);
            // MANUAL_BOOK (RAINCO) skips are stored under PLASTIC — the canonical key for the shared book
            String businessName = request.getBillSource() == BillSource.MANUAL_BOOK
                    ? "PLASTIC"
                    : request.getBusiness().name();
            List<BillNumberSkip> skips = request.getSkippedBillNumbers().stream()
                    .map(String::trim)
                    .filter(n -> !n.isBlank())
                    .map(n -> BillNumberSkip.builder()
                            .business(businessName)
                            .billNumber(stripLeadingZeros(n))
                            .status(skipStatus)
                            .submittedBy(currentUser)
                            .bill(savedBill)
                            .createdAt(LocalDateTime.now())
                            .build())
                    .toList();
            billNumberSkipRepository.saveAll(skips);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<BillResponse> globalSearch(String q) {
        if (q == null || q.isBlank() || q.length() < 2) return List.of();
        return billRepository.globalSearch(q.trim(),
                org.springframework.data.domain.PageRequest.of(0, 30))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BillResponse> getAllBills(BusinessType business, BillStatus status,
                                          boolean excludeCompleted, LocalDate from, LocalDate to) {
        // Real users cannot access the DEMO business — it exists only for portfolio demo sessions
        if (business == BusinessType.DEMO) return List.of();
        User caller = getCurrentUser();
        boolean doExclude = excludeCompleted && status == null;
        List<BillStatus> excluded = List.of(BillStatus.COMPLETED, BillStatus.AWAITING_CONFIRMATION, BillStatus.CANCELLED);

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
        List<BillStatus> excluded = List.of(BillStatus.COMPLETED, BillStatus.AWAITING_CONFIRMATION, BillStatus.CANCELLED);
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
     * Returns the real business types visible to the given role.
     * DEMO is excluded for all real users — demo sessions use fake JWTs
     * intercepted on the frontend before reaching this service.
     * Returns null only for SHOP_ACCOUNTANT (handled by dedicated queries).
     */
    private List<BusinessType> getAllowedBusinessTypes(UserRole role) {
        return switch (role) {
            case SHOP_ACCOUNTANT  -> null; // handled by dedicated query
            case ACCOUNTANT, MAIN_ACCOUNTANT -> List.of(
                    BusinessType.RAINCO, BusinessType.STATIONERY,
                    BusinessType.PLASTIC, BusinessType.HARDWARE);
            case ADMIN, OWNER -> List.of(
                    BusinessType.RAINCO, BusinessType.RETAIL_SHOP, BusinessType.STATIONERY,
                    BusinessType.PLASTIC, BusinessType.HARDWARE);
            case WORKER -> List.of();
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
        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        return toResponse(assignBillInternal(bill, worker, caller));
    }

    /** Assigns the same worker to a batch of bills in one transaction — if any bill fails validation, the whole batch rolls back. */
    @Transactional
    public List<BillResponse> bulkAssignBills(BulkAssignBillRequest request) {
        User caller = getCurrentUser();
        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        List<Bill> assigned = new ArrayList<>();
        for (Long billId : request.getBillIds()) {
            Bill bill = findBillById(billId);
            assigned.add(assignBillInternal(bill, worker, caller));
        }
        return assigned.stream().map(this::toResponse).toList();
    }

    private Bill assignBillInternal(Bill bill, Worker worker, User caller) {
        if (bill.getStatus() == BillStatus.COMPLETED ||
                bill.getStatus() == BillStatus.AWAITING_CONFIRMATION ||
                bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException(
                    "Cannot assign bill " + bill.getBillNumber() + " — it is completed or cancelled");
        }

        if (caller.getRole() == UserRole.SHOP_ACCOUNTANT) {
            List<BillStatus> allowed = List.of(
                    BillStatus.ASSIGNED,
                    BillStatus.SHOP_RECEIVED,
                    BillStatus.SHOP_WORKER_ASSIGNED
            );
            if (!allowed.contains(bill.getStatus())) {
                throw new RuntimeException(
                        "Shop accountant can only assign bills with status: ASSIGNED, SHOP_RECEIVED or SHOP_WORKER_ASSIGNED " +
                        "(bill " + bill.getBillNumber() + ")");
            }
        }

        BillStatus newStatus = (caller.getRole() == UserRole.SHOP_ACCOUNTANT)
                ? BillStatus.SHOP_WORKER_ASSIGNED
                : BillStatus.ASSIGNED;

        bill.setCurrentHolder(worker);
        bill.setStatus(newStatus);
        bill.setUpdatedAt(LocalDateTime.now());
        return billRepository.save(bill);
    }

    @Transactional
    public BillResponse markShopReceived(Long id) {
        Bill bill = findBillById(id);
        User caller = getCurrentUser();
        return toResponse(markShopReceivedInternal(bill, caller));
    }

    /** Marks a batch of bills as shop received in one transaction — any failure rolls back the whole batch. */
    @Transactional
    public List<BillResponse> bulkMarkShopReceived(BulkBillIdsRequest request) {
        User caller = getCurrentUser();
        List<Bill> updated = new ArrayList<>();
        for (Long billId : request.getBillIds()) {
            Bill bill = findBillById(billId);
            updated.add(markShopReceivedInternal(bill, caller));
        }
        return updated.stream().map(this::toResponse).toList();
    }

    private Bill markShopReceivedInternal(Bill bill, User caller) {
        if (bill.getStatus() == BillStatus.COMPLETED ||
                bill.getStatus() == BillStatus.AWAITING_CONFIRMATION ||
                bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Cannot change status of bill " + bill.getBillNumber() + " — it is completed or cancelled");
        }

        switch (caller.getRole()) {
            case SHOP_ACCOUNTANT -> {
                if (bill.getStatus() != BillStatus.ASSIGNED &&
                        bill.getStatus() != BillStatus.SHOP_WORKER_ASSIGNED) {
                    throw new RuntimeException(
                            "Shop accountant can only mark ASSIGNED or SHOP_WORKER_ASSIGNED bills as shop received " +
                            "(bill " + bill.getBillNumber() + ")");
                }
            }
            case ACCOUNTANT, MAIN_ACCOUNTANT, ADMIN -> {
                // allowed from any non-terminal status — already checked above
            }
            default -> throw new RuntimeException("Not authorized to mark bill as shop received");
        }

        bill.setStatus(BillStatus.SHOP_RECEIVED);
        bill.setUpdatedAt(LocalDateTime.now());
        return billRepository.save(bill);
    }

    @Transactional
    public BillResponse markReceived(Long id) {
        Bill bill = findBillById(id);
        return toResponse(markReceivedInternal(bill));
    }

    /** Marks a batch of bills as store received in one transaction — any failure rolls back the whole batch. */
    @Transactional
    public List<BillResponse> bulkMarkReceived(BulkBillIdsRequest request) {
        List<Bill> updated = new ArrayList<>();
        for (Long billId : request.getBillIds()) {
            Bill bill = findBillById(billId);
            updated.add(markReceivedInternal(bill));
        }
        return updated.stream().map(this::toResponse).toList();
    }

    private Bill markReceivedInternal(Bill bill) {
        if (bill.getStatus() != BillStatus.ASSIGNED &&
                bill.getStatus() != BillStatus.SHOP_RECEIVED) {
            throw new RuntimeException(
                    "Bill " + bill.getBillNumber() + " must be ASSIGNED or SHOP_RECEIVED to mark as store received");
        }

        bill.setStatus(BillStatus.STORE_RECEIVED);
        bill.setReceivedBy(getCurrentUser());
        bill.setReceivedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        return billRepository.save(bill);
    }

    @Transactional
    /**
     * Voids a bill, recording why.
     *
     * <p>The bill keeps its number: it is still a record of something that happened,
     * and the number is part of that record. Nothing else will be issued under it, and
     * the sequence shows it as used rather than as a gap somebody forgot.
     *
     * <p>A bill entered in error is deleted instead — that removes the record and
     * releases the number, which is a different intention and has a different effect.
     *
     * <p>The reason is required because a cancelled bill sits in the run forever, and
     * whoever finds it later has only this to explain it.
     */
    public BillResponse cancelBill(Long id, String reason, String by) {
        Bill bill = findBillById(id);
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("Bill is already cancelled");
        }
        if (bill.getStatus() == BillStatus.COMPLETED
                || bill.getStatus() == BillStatus.AWAITING_CONFIRMATION) {
            throw new RuntimeException("Cannot cancel a fully paid bill");
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException(
                    "Give a reason for cancelling this bill — it stays in the run under "
                  + "its number, so the record has to say why.");
        }
        // Money already collected against a bill about to be voided is a contradiction
        // worth stopping on rather than leaving for someone to find in the payments list.
        if (bill.getAmountPaid() != null
                && bill.getAmountPaid().compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new RuntimeException(
                    "This bill has Rs " + bill.getAmountPaid().toPlainString()
                  + " already paid against it. Reverse the payment before cancelling.");
        }

        bill.setStatus(BillStatus.CANCELLED);
        bill.setCancelReason(reason.trim());
        bill.setCancelledBy(by);
        bill.setCancelledAt(LocalDateTime.now());
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
            bill.setArea(request.getArea().trim().toUpperCase());
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
        if (request.getBillSource() != null) {
            bill.setBillSource(request.getBillSource());
        }
        if (request.getBillNumber() != null && !request.getBillNumber().isBlank()) {
            String newNum = request.getBillNumber().trim();
            // Only update if actually changed — skip duplicate check for same value
            if (!newNum.equals(bill.getBillNumber())) {
                if (billRepository.existsActiveByBillNumberAndBusiness(newNum, bill.getBusiness()))
                    throw new RuntimeException("Bill number " + newNum + " already exists in this business");
                bill.setBillNumber(newNum);
            }
        }
        if (request.getTotalAmount() != null) {
            bill.setTotalAmount(request.getTotalAmount());
            BillBalance.recompute(bill);
        }
        if (request.getWorkerId() != null) {
            Worker worker = workerRepository.findById(request.getWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
            bill.setCurrentHolder(worker);
        }

        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    /**
     * Deletes a bill entered in error, along with its own paperwork.
     *
     * <p>The paperwork goes by database cascade rather than by clearing table after
     * table here: about fifteen tables hang off a bill, and enumerating them in Java is
     * what caused deletes to fail on whichever one was forgotten.
     *
     * <p>What is refused is anything representing money that has already moved or work
     * somebody else has done — a confirmed payment, a worker's collection, a collection
     * note. Those must be unwound deliberately, not swept away by deleting the bill
     * they happen to point at. The reasons are gathered and reported together, so the
     * accountant learns everything blocking them in one go.
     */
    /**
     * Hides a bill from the aging report, or puts it back.
     *
     * <p>Admin only, and never a delete: the balance stays owed and stays on the bill.
     * All this says is that it is not chaseable debt worth reporting, so the aging
     * report stops overstating what can actually be collected. Who did it and why are
     * kept, so the decision can be explained months later or simply undone.
     */
    @Transactional
    public BillResponse setAgingVisibility(Long id, boolean excluded, String reason, String by) {
        Bill bill = findBillById(id);

        if (excluded && (reason == null || reason.isBlank())) {
            throw new RuntimeException(
                    "Give a reason for keeping this bill off the aging report — it stays owed, "
                  + "so somebody will ask why it is missing.");
        }

        bill.setExcludedFromAging(excluded);
        bill.setAgingExclusionReason(excluded ? reason : null);
        bill.setAgingExcludedBy(excluded ? by : null);
        bill.setAgingExcludedAt(excluded ? LocalDateTime.now() : null);
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    /** Bills currently hidden from the aging report, so they can be reviewed and restored. */
    @Transactional(readOnly = true)
    public List<BillResponse> getAgingExcludedBills(com.multi.finance.enums.BusinessType business) {
        return billRepository.findExcludedFromAging(business)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * What deleting this bill would take with it.
     *
     * <p>Deletion is allowed even when money is attached, because a bill entered
     * wrongly has to be removable — but never silently. The caller shows this first,
     * so the confirmation names the records rather than warning in the abstract.
     */
    @Transactional(readOnly = true)
    public BillDeleteImpact deleteImpact(Long id) {
        Bill bill = findBillById(id);
        List<String> warnings = new ArrayList<>();

        var payments = paymentRepository.findByBillId(bill.getId());
        int confirmed = (int) payments.stream()
                .filter(p -> p.getStatus() == com.multi.finance.enums.PaymentStatus.CONFIRMED)
                .count();
        java.math.BigDecimal confirmedAmt = payments.stream()
                .filter(p -> p.getStatus() == com.multi.finance.enums.PaymentStatus.CONFIRMED)
                .map(com.multi.finance.entity.Payment::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        int unconfirmed = payments.size() - confirmed;

        int workerEntries = workerPaymentEntryRepository.findByBillId(bill.getId()).size();
        int notes = collectionNoteRepository.findByBillId(bill.getId()).size();

        var returns = billReturnRepository.findByBillIdOrderBySubmittedAtDesc(bill.getId());
        int approvedReturns = (int) returns.stream()
                .filter(r -> r.getStatus() == com.multi.finance.enums.ReturnStatus.APPROVED).count();
        int openReturns = (int) returns.stream().filter(r -> r.getStatus().isOpen()).count();

        // Money first: it is the part that cannot be reconstructed from memory.
        if (confirmed > 0) {
            warnings.add(confirmed + " confirmed payment(s) totalling Rs "
                    + confirmedAmt.setScale(2, java.math.RoundingMode.HALF_UP)
                    + " will be deleted. That money will no longer appear anywhere in the system.");
        }
        if (unconfirmed > 0) {
            warnings.add(unconfirmed + " unconfirmed payment(s) will be deleted.");
        }
        if (workerEntries > 0) {
            warnings.add(workerEntries + " worker collection entr"
                    + (workerEntries == 1 ? "y" : "ies")
                    + " will be deleted, so the worker's record of collecting it goes too.");
        }
        if (notes > 0) {
            warnings.add(notes + " collection note(s) will be deleted.");
        }
        if (approvedReturns > 0) {
            warnings.add(approvedReturns + " approved return(s) will be deleted. Stock already "
                    + "moved for them stays moved — the movement is not reversed.");
        }
        if (openReturns > 0) {
            warnings.add(openReturns + " return(s) still awaiting review will be deleted.");
        }
        if (bill.getDeliveryRun() != null) {
            warnings.add("It will be removed from the "
                    + bill.getDeliveryRun().areaLabel() + " round, whose counts will drop by one.");
        }

        return BillDeleteImpact.builder()
                .billId(bill.getId())
                .billNumber(bill.getBillNumber())
                .customerName(bill.getCustomerName())
                .totalAmount(bill.getTotalAmount())
                .confirmedPayments(confirmed)
                .confirmedAmount(confirmedAmt)
                .unconfirmedPayments(unconfirmed)
                .workerEntries(workerEntries)
                .collectionNotes(notes)
                .approvedReturns(approvedReturns)
                .openReturns(openReturns)
                .onDeliveryRun(bill.getDeliveryRun() != null)
                .deliveryRunLabel(bill.getDeliveryRun() != null
                        ? bill.getDeliveryRun().areaLabel() : null)
                .warnings(warnings)
                .clean(warnings.isEmpty())
                .build();
    }

    @Transactional
    public void deleteBill(Long id) {
        deleteBill(id, false);
    }

    /**
     * Removes a bill and everything hanging off it.
     *
     * <p>Unlike cancelling, this destroys the record — which is why it also releases the
     * number for reuse: nothing is left holding it. A bill typed in error should not sit
     * in the run forever, and the sequence should not carry a permanent hole for it.
     *
     * @param force required once anything of consequence is attached. Without it the
     *              call is refused and the impact returned, so the person deciding sees
     *              what they are about to destroy before they agree to it.
     */
    @Transactional
    public void deleteBill(Long id, boolean force) {
        Bill bill = findBillById(id);

        if (!force) {
            BillDeleteImpact impact = deleteImpact(id);
            if (!impact.isClean()) {
                throw new RuntimeException(
                        "This bill has records attached: " + String.join(" ", impact.getWarnings()));
            }
        }

        List<String> blockers = new ArrayList<>();

        if (paymentRepository.existsByBillIdAndStatus(
                bill.getId(), com.multi.finance.enums.PaymentStatus.CONFIRMED)) {
            blockers.add("it has confirmed payments");
        }
        int workerEntries = workerPaymentEntryRepository.findByBillId(bill.getId()).size();
        if (workerEntries > 0) {
            blockers.add(workerEntries + " worker collection entr"
                    + (workerEntries == 1 ? "y is" : "ies are") + " recorded against it");
        }
        int notes = collectionNoteRepository.findByBillId(bill.getId()).size();
        if (notes > 0) {
            blockers.add(notes + " collection note" + (notes == 1 ? " is" : "s are") + " linked to it");
        }
        boolean creditedReturn = billReturnRepository
                .findByBillIdOrderBySubmittedAtDesc(bill.getId()).stream()
                .anyMatch(r -> r.getStatus() == com.multi.finance.enums.ReturnStatus.APPROVED);
        if (creditedReturn) {
            blockers.add("an approved return has already credited it and moved stock");
        }

        // Nothing is refused outright any more: the impact was shown and agreed to.
        // Payments and returns are removed explicitly; the rest follows by cascade.
        blockers.clear();

        // Unconfirmed payments and open returns are the bill's own and go with it. The
        // rest of its records follow by cascade.
        paymentRepository.deleteAll(paymentRepository.findByBillId(bill.getId()));
        billReturnRepository.deleteAll(
                billReturnRepository.findByBillIdOrderBySubmittedAtDesc(bill.getId()));
        billRepository.delete(bill);
    }

    @Transactional(readOnly = true)
    public List<BillResponse> getLinkingBills() {
        return billRepository.findByWillBeLinkedTrueOrderByBillDateDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<SkipReviewResponse> getPendingSkips() {
        return billNumberSkipRepository.findAllPending().stream()
                .map(s -> SkipReviewResponse.builder()
                        .id(s.getId())
                        .business(s.getBusiness())
                        .skippedBillNumber(s.getBillNumber())
                        .relatedBillNumber(s.getBill() != null ? s.getBill().getBillNumber() : null)
                        .customerName(s.getBill() != null ? s.getBill().getCustomerName() : null)
                        .submittedByName(s.getSubmittedBy() != null ? s.getSubmittedBy().getUsername() : null)
                        .submittedAt(s.getCreatedAt())
                        .build())
                .toList();
    }

    public void approveSkip(Long id) {
        BillNumberSkip skip = billNumberSkipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Skip not found"));
        User admin = getCurrentUser();
        skip.setStatus("APPROVED");
        skip.setReviewedBy(admin);
        skip.setReviewedAt(LocalDateTime.now());
        billNumberSkipRepository.save(skip);
    }

    public void rejectSkip(Long id) {
        BillNumberSkip skip = billNumberSkipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Skip not found"));
        User admin = getCurrentUser();
        skip.setStatus("REJECTED");
        skip.setReviewedBy(admin);
        skip.setReviewedAt(LocalDateTime.now());
        billNumberSkipRepository.save(skip);
    }

    // Returns next 5 suggested bill numbers for the given business+source combo.
    // Shared physical book (BK- prefix): PLASTIC MANUAL + STATIONERY MANUAL + RAINCO MANUAL_BOOK.
    // Takes GREATEST across bills table and skip table so continuation pages don't re-appear.
    public List<BillNumberOption> getNextBillNumbers(BusinessType business, BillSource billSource) {
        boolean isSharedBook = billSource == BillSource.MANUAL_BOOK
                || (billSource == BillSource.MANUAL
                    && (business == BusinessType.PLASTIC || business == BusinessType.STATIONERY));

        List<Integer> used;
        List<Integer> approvedSkips;

        if (isSharedBook) {
            used = billRepository.findUsedSharedBookBillNumbers();
            approvedSkips = billNumberSkipRepository.findApprovedSharedBookSkippedNumbers();
        } else if (billSource == BillSource.MANUAL) {
            used = billRepository.findUsedManualBillNumbers(business.name());
            approvedSkips = billNumberSkipRepository.findApprovedSkippedNumbers(business.name());
        } else {
            used = billRepository.findUsedSystemBillNumbers(business.name());
            approvedSkips = List.of(); // skips only apply to MANUAL/MANUAL_BOOK bills
        }

        int count;
        if (billSource == BillSource.SYSTEM
                && (business == BusinessType.RAINCO || business == BusinessType.STATIONERY)) {
            count = 20;
        } else if (isSharedBook) {
            count = 10;
        } else {
            count = 5; // RAINCO MANUAL and all other combos
        }

        java.util.Set<Integer> taken = new java.util.HashSet<>(used);
        taken.addAll(approvedSkips);

        int max = taken.stream().mapToInt(Integer::intValue).max().orElse(0);

        // Where this run actually begins. Numbers below it predate the books in use and
        // are not worth reporting as missing.
        int floor = sequenceFloor(business, billSource, isSharedBook);

        List<BillNumberOption> options = new ArrayList<>();

        // Holes at or above the floor. Previously the list started above the maximum, so a
        // number nobody entered vanished for good and no one could tell it had been missed.
        // They are listed first, and flagged, because a gap is a question to answer rather
        // than simply the next number to take.
        int lowest = Math.max(floor, taken.stream().mapToInt(Integer::intValue).min().orElse(floor));
        for (int n = lowest; n < max; n++) {
            if (!taken.contains(n)) options.add(new BillNumberOption(n, true));
        }

        // A run with nothing in it yet still starts at its floor rather than at 1.
        int next = Math.max(max, floor - 1);
        for (int i = 1; i <= count; i++) options.add(new BillNumberOption(next + i, false));
        return options;
    }

    /**
     * The first number worth tracking for a run, from {@code bill_seq_floor_*} in settings.
     * Zero when nothing is configured, which keeps the old behaviour of reporting every gap.
     */
    private int sequenceFloor(BusinessType business, BillSource billSource, boolean isSharedBook) {
        String key = isSharedBook
                ? "bill_seq_floor_SHARED_BOOK"
                : "bill_seq_floor_" + business.name() + "_"
                  + (billSource == BillSource.MANUAL ? "MANUAL" : "SYSTEM");
        return settingsRepository.findByKey(key)
                .map(setting -> {
                    try { return Integer.parseInt(setting.getValue().trim()); }
                    catch (NumberFormatException e) { return 0; }
                })
                .orElse(0);
    }

    /**
     * A number offered in the create-bill dropdown.
     *
     * @param missing true when it sits in a hole below the highest number used — never
     *                entered and not an approved continuation-page skip
     */
    public record BillNumberOption(int number, boolean missing) {}

    private int coalesce(Integer value) {
        return value == null ? 0 : value;
    }

    private static final int OVERDUE_DAYS = 45;

    @Transactional(readOnly = true)
    public AgingReportResponse getAgingReport(BusinessType business) {
        LocalDate today = LocalDate.now();
        List<BillStatus> excluded = List.of(BillStatus.COMPLETED, BillStatus.AWAITING_CONFIRMATION, BillStatus.CANCELLED);

        List<Bill> bills = billRepository.findByBusinessAndStatusNotInOrderByCreatedAtDesc(business, excluded)
                .stream()
                .filter(b -> b.getBalanceRemaining() != null
                        && b.getBalanceRemaining().compareTo(BigDecimal.ZERO) > 0
                        && !Boolean.TRUE.equals(b.getWillBeLinked()))
                // Paid off is paid off, whatever the status happens to say. A return
                // credit can settle a bill without anything moving it to COMPLETED.
                .filter(b -> !Boolean.TRUE.equals(b.getFullyPaid()))
                // Hidden by an admin. The printable export already honoured this; the
                // on-screen report did not, so the two disagreed.
                .filter(b -> !Boolean.TRUE.equals(b.getExcludedFromAging()))
                // Collected on another bill. Chasing this one would be chasing the same
                // money twice, and the customer only owes it once.
                .filter(b -> b.getSettledOn() == null)
                .collect(Collectors.toList());

        // Bulk-fetch last confirmed payment dates — single query, no N+1
        List<Long> billIds = bills.stream().map(Bill::getId).collect(Collectors.toList());
        Map<Long, LocalDate> lastPaymentByBillId = new java.util.HashMap<>();
        if (!billIds.isEmpty()) {
            paymentRepository.findLastConfirmedDatesByBillIds(billIds).forEach(row -> {
                Long   bid  = (Long)      row[0];
                LocalDate dt = (LocalDate) row[1];
                lastPaymentByBillId.put(bid, dt);
            });
        }

        record CKey(String name, Long cid, String area) {}

        Map<CKey, List<Bill>> byCustomer = bills.stream().collect(
                Collectors.groupingBy(b -> new CKey(
                        b.getCustomerName(),
                        b.getCustomer() != null ? b.getCustomer().getId() : null,
                        b.getArea() != null ? b.getArea() : "")));

        List<AgingCustomerEntry> entries = new ArrayList<>();
        for (Map.Entry<CKey, List<Bill>> e : byCustomer.entrySet()) {
            CKey key = e.getKey();
            List<Bill> cb = e.getValue();

            BigDecimal total      = BigDecimal.ZERO;
            BigDecimal overdue    = BigDecimal.ZERO;
            BigDecimal cur        = BigDecimal.ZERO;
            BigDecimal d3160      = BigDecimal.ZERO;
            BigDecimal d6190      = BigDecimal.ZERO;
            BigDecimal d91        = BigDecimal.ZERO;
            // cash buckets
            BigDecimal cashPending  = BigDecimal.ZERO;
            BigDecimal cashFollowUp = BigDecimal.ZERO;   // 1–7 days
            BigDecimal cashUrgent   = BigDecimal.ZERO;   // 8–14 days
            BigDecimal cashSerious  = BigDecimal.ZERO;   // 15+ days
            LocalDate  oldest  = today;
            LocalDate  lastPmt = null;

            for (Bill b : cb) {
                BigDecimal bal = b.getBalanceRemaining();
                LocalDate  d   = b.getBillDate() != null ? b.getBillDate()
                        : b.getCreatedAt().toLocalDate();
                long age = ChronoUnit.DAYS.between(d, today);

                total = total.add(bal);
                if (d.isBefore(oldest)) oldest = d;

                LocalDate pmtDate = lastPaymentByBillId.get(b.getId());
                if (pmtDate != null && (lastPmt == null || pmtDate.isAfter(lastPmt))) lastPmt = pmtDate;

                if (b.getBillType() == BillType.CASH) {
                    cashPending = cashPending.add(bal);
                    if      (age == 0)       { /* today — normal, no bucket */ }
                    else if (age <= 7)       cashFollowUp = cashFollowUp.add(bal);
                    else if (age <= 14)      cashUrgent   = cashUrgent.add(bal);
                    else                     cashSerious  = cashSerious.add(bal);
                } else {
                    // CREDIT bill — standard aging
                    if (age >= OVERDUE_DAYS) overdue = overdue.add(bal);
                    if      (age <= 30) cur   = cur.add(bal);
                    else if (age <= 60) d3160 = d3160.add(bal);
                    else if (age <= 90) d6190 = d6190.add(bal);
                    else                d91   = d91.add(bal);
                }
            }

            entries.add(AgingCustomerEntry.builder()
                    .customerName(key.name())
                    .customerId(key.cid())
                    .area(key.area().isBlank() ? null : key.area())
                    .totalOutstanding(total)
                    .overdue(overdue)
                    .current(cur)
                    .days31to60(d3160)
                    .days61to90(d6190)
                    .days91plus(d91)
                    .cashPending(cashPending)
                    .cashFollowUp(cashFollowUp)
                    .cashUrgent(cashUrgent)
                    .cashSerious(cashSerious)
                    .billCount(cb.size())
                    .oldestBillDate(oldest)
                    .lastPaymentDate(lastPmt)
                    .build());
        }

        entries.sort(Comparator.comparing(AgingCustomerEntry::getTotalOutstanding).reversed());
        List<AgingCustomerEntry> top20 = entries.stream().limit(20).collect(Collectors.toList());

        // Area summaries — aggregate from customer entries
        Map<String, List<AgingCustomerEntry>> byArea = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getArea() != null ? e.getArea() : "Unknown"));

        List<AgingAreaSummary> areaSummaries = byArea.entrySet().stream().map(ae -> {
            List<AgingCustomerEntry> customers = ae.getValue().stream()
                    .sorted(Comparator.comparing(AgingCustomerEntry::getTotalOutstanding).reversed())
                    .collect(Collectors.toList());
            BigDecimal areaTotal      = sum(customers, AgingCustomerEntry::getTotalOutstanding);
            BigDecimal areaOverdue    = sum(customers, AgingCustomerEntry::getOverdue);
            BigDecimal areaCur        = sum(customers, AgingCustomerEntry::getCurrent);
            BigDecimal area3160       = sum(customers, AgingCustomerEntry::getDays31to60);
            BigDecimal area6190       = sum(customers, AgingCustomerEntry::getDays61to90);
            BigDecimal area91         = sum(customers, AgingCustomerEntry::getDays91plus);
            BigDecimal areaCashPending  = sum(customers, AgingCustomerEntry::getCashPending);
            BigDecimal areaCashSerious  = sum(customers, AgingCustomerEntry::getCashSerious);
            return AgingAreaSummary.builder()
                    .area(ae.getKey())
                    .totalOutstanding(areaTotal)
                    .overdue(areaOverdue)
                    .current(areaCur)
                    .days31to60(area3160)
                    .days61to90(area6190)
                    .days91plus(area91)
                    .cashPending(areaCashPending)
                    .cashSerious(areaCashSerious)
                    .customerCount(customers.size())
                    .billCount(customers.stream().mapToInt(AgingCustomerEntry::getBillCount).sum())
                    .customers(customers)
                    .build();
        }).sorted(Comparator.comparing(AgingAreaSummary::getTotalOutstanding).reversed())
                .collect(Collectors.toList());

        BigDecimal grandTotal       = sum(entries, AgingCustomerEntry::getTotalOutstanding);
        BigDecimal grandOverdue     = sum(entries, AgingCustomerEntry::getOverdue);
        BigDecimal grandCashPending = sum(entries, AgingCustomerEntry::getCashPending);
        BigDecimal grandCashSerious = sum(entries, AgingCustomerEntry::getCashSerious);

        return AgingReportResponse.builder()
                .grandTotalOutstanding(grandTotal)
                .grandOverdue(grandOverdue)
                .grandCashPending(grandCashPending)
                .grandCashSerious(grandCashSerious)
                .totalCustomers(entries.size())
                .totalBills(bills.size())
                .topCustomers(top20)
                .allCustomers(entries)
                .byArea(areaSummaries)
                .build();
    }

    private static BigDecimal sum(List<AgingCustomerEntry> list,
                                  java.util.function.Function<AgingCustomerEntry, BigDecimal> fn) {
        return list.stream().map(fn).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Where the gap scan starts for a series.
     *
     * <p>Each series was already running long before it was kept here, so a scan from
     * one reports every number issued in the years before as missing. The report is read
     * to chase bills nobody entered, and a list of thousands that were never going to be
     * in it is a list nobody reads.
     *
     * <p>These are the first numbers actually entered, per business and per series, and
     * they have to be given rather than derived: taking the lowest number present would
     * move the floor every time the earliest bill was corrected or deleted, quietly
     * hiding real gaps at the bottom of the range.
     */
    private int gapScanStart(BusinessType business, BillSource source) {
        // Plastic's own system series is short and recent; Rainco's SYS numbers are in
        // the thirteen thousands, and scanning plastic from there finds nothing at all.
        if (business == BusinessType.PLASTIC && source == BillSource.SYSTEM) {
            return 273;
        }
        return switch (source) {
            case SYSTEM -> 13200;
            case MANUAL -> 300;
            default     -> 1;
        };
    }

    @Transactional(readOnly = true)
    public List<BillSequenceGapResponse> findSequenceGaps(BusinessType business) {
        List<Bill> bills = billRepository.findByBusinessOrderByCreatedAtDesc(business)
                .stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED && b.getBillNumber() != null)
                .collect(Collectors.toList());

        Map<BillSource, List<Bill>> bySource = bills.stream()
                .collect(Collectors.groupingBy(Bill::getBillSource));

        List<BillSequenceGapResponse> result = new ArrayList<>();

        for (Map.Entry<BillSource, List<Bill>> entry : bySource.entrySet()) {
            BillSource source = entry.getKey();
            List<Bill> sourceBills = entry.getValue();
            int minStart = gapScanStart(business, source);

            String prefix = "";
            int maxPadding = 0;
            List<Integer> nums = new ArrayList<>();

            for (Bill bill : sourceBills) {
                // Strip duplication suffix (e.g. "-DUP-411") before extracting the sequence number
                String num = bill.getBillNumber().replaceAll("-DUP-\\d+$", "");
                int lastDash = num.lastIndexOf('-');
                if (lastDash < 0) continue;
                String pref   = num.substring(0, lastDash + 1);
                String suffix = num.substring(lastDash + 1);
                if (!suffix.matches("\\d+")) continue;
                int val = Integer.parseInt(suffix);
                if (val < minStart) continue;           // skip bills before scan window
                if (prefix.isEmpty()) prefix = pref;
                maxPadding = Math.max(maxPadding, suffix.length());
                if (!nums.contains(val)) nums.add(val);
            }

            if (nums.isEmpty()) continue;
            nums.sort(Comparator.naturalOrder());

            int first = nums.get(0);
            int last  = nums.get(nums.size() - 1);
            final String finalPrefix  = prefix;
            final int    finalPadding = maxPadding;

            List<String> missing = new ArrayList<>();
            for (int i = 0; i < nums.size() - 1; i++) {
                int curr = nums.get(i);
                int next = nums.get(i + 1);
                for (int m = curr + 1; m < next; m++) {
                    missing.add(finalPrefix + String.format("%0" + finalPadding + "d", m));
                }
            }

            result.add(BillSequenceGapResponse.builder()
                    .billSource(source.name())
                    .totalBills(nums.size())
                    .firstNumber(first)
                    .lastNumber(last)
                    .missingCount(missing.size())
                    .missingNumbers(missing)
                    .build());
        }

        result.sort(Comparator.comparing(BillSequenceGapResponse::getBillSource));
        return result;
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

    // ── Bill Review ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BillResponse> getUnreviewedBills() {
        User caller = getCurrentUser();
        Set<Long> reviewed = billReviewRepository.findReviewedBillIdsByUserId(caller.getId());
        return billRepository.findAll().stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED && !reviewed.contains(b.getId()))
                .sorted(Comparator.comparing(Bill::getCreatedAt).reversed())
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreviewedCount() {
        User caller = getCurrentUser();
        Set<Long> reviewed = billReviewRepository.findReviewedBillIdsByUserId(caller.getId());
        return billRepository.countByStatusNot(BillStatus.CANCELLED) - reviewed.size();
    }

    @Transactional
    public void markBillsReviewed(List<Long> billIds) {
        User caller = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        for (Long billId : billIds) {
            if (!billReviewRepository.existsByBillIdAndReviewedById(billId, caller.getId())) {
                Bill bill = billRepository.findById(billId)
                        .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
                billReviewRepository.save(BillReview.builder()
                        .bill(bill)
                        .reviewedBy(caller)
                        .reviewedAt(now)
                        .build());
            }
        }
    }

    @Transactional
    public void markAllBillsReviewed() {
        User caller = getCurrentUser();
        Set<Long> alreadyReviewed = billReviewRepository.findReviewedBillIdsByUserId(caller.getId());
        LocalDateTime now = LocalDateTime.now();
        billRepository.findAll().stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED && !alreadyReviewed.contains(b.getId()))
                .forEach(bill -> billReviewRepository.save(BillReview.builder()
                        .bill(bill)
                        .reviewedBy(caller)
                        .reviewedAt(now)
                        .build()));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private String stripLeadingZeros(String raw) {
        if (raw == null) return raw;
        try { return String.valueOf(Long.parseLong(raw.trim())); }
        catch (NumberFormatException e) { return raw.trim(); }
    }

    private String generateDraftNumber(BusinessType business) {
        Integer max = billRepository.findMaxDraftSequenceByBusiness(business);
        int next = (max == null ? 0 : max) + 1;
        String candidate;
        do {
            candidate = String.format("DFT-%d", next++);
        } while (billRepository.existsActiveByBillNumberAndBusiness(candidate, business));
        return candidate;
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
                .willBeLinked(bill.getWillBeLinked())
                .stockCleared(bill.getStockCleared())
                .collectionOnly(bill.getCollectionOnly())
                .settledOnBillId(bill.getSettledOn() != null ? bill.getSettledOn().getId() : null)
                .settledOnBillNumber(bill.getSettledOn() != null
                        ? bill.getSettledOn().getBillNumber() : null)
                .settledOnStatus(bill.getSettledOn() != null && bill.getSettledOn().getStatus() != null
                        ? bill.getSettledOn().getStatus().name() : null)
                .settledOnNote(bill.getSettledOnNote())
                .settledOnBy(bill.getSettledOnBy())
                .cancelReason(bill.getCancelReason())
                .cancelledBy(bill.getCancelledBy())
                .cancelledAt(bill.getCancelledAt())
                .excludedFromAging(bill.getExcludedFromAging())
                .agingExclusionReason(bill.getAgingExclusionReason())
                .agingExcludedBy(bill.getAgingExcludedBy())
                .deliveryMode(bill.getDeliveryMode() != null ? bill.getDeliveryMode().name() : null)
                .deliveryRunId(bill.getDeliveryRun() != null ? bill.getDeliveryRun().getId() : null)
                .deliveryRunArea(bill.getDeliveryRun() != null
                        ? bill.getDeliveryRun().areaLabel() : null)
                .deliveryRunDate(bill.getDeliveryRun() != null
                        ? bill.getDeliveryRun().getPlannedDate() : null)
                .build();
    }

    @Transactional
    public BillResponse markStockCleared(Long id) {
        Bill bill = findBillById(id);
        if (Boolean.TRUE.equals(bill.getStockCleared())) {
            throw new RuntimeException("Bill #" + bill.getBillNumber() + " is already marked as stock reduced");
        }
        bill.setStockCleared(true);
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    /**
     * Which hand-written bills exist to be collected on, per business.
     *
     * <p>Rainco is written both on loose manual bills and in the book; stationery only
     * on manual bills. Offering a kind that business never issues is a wrong pick
     * waiting to happen, so each is given only what it actually uses.
     */
    private static List<BillSource> settleSourcesFor(BusinessType business) {
        if (business == BusinessType.STATIONERY) {
            return List.of(BillSource.MANUAL);
        }
        return List.of(BillSource.MANUAL, BillSource.MANUAL_BOOK);
    }

    /**
     * Records that this bill's money is collected on another one.
     *
     * <p>The same sale gets billed twice: once by hand at the shop, once here to keep
     * the record and move the stock. Only one of the two is collected. Cancelling the
     * system copy used to be the answer, but that says the sale never happened — it did,
     * and the stock went out on it.
     *
     * <p>Linking says the true thing instead. The bill stays real and keeps its lines;
     * it simply stops being chased, and closes itself when the bill it points at is
     * paid off.
     *
     * <p>Not the same as the end-of-month stock linking, where one system bill covers
     * many manual ones. This is one bill to one bill, and it is about the money.
     */
    @Transactional
    public BillResponse linkSettlement(Long id, Long targetId, String note, String by) {
        Bill bill = findBillById(id);
        if (targetId == null) {
            throw new RuntimeException("Choose the bill this one is collected on.");
        }
        if (targetId.equals(id)) {
            throw new RuntimeException("A bill cannot be collected on itself.");
        }
        Bill target = findBillById(targetId);

        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException("This bill is cancelled — there is nothing left to collect.");
        }
        if (target.getStatus() == BillStatus.CANCELLED) {
            throw new RuntimeException(
                    "Bill " + target.getBillNumber() + " is cancelled, so nothing will be "
                  + "collected on it.");
        }
        // Only a hand-written bill can stand in for a system one — that is the whole
        // situation being recorded. Pointing at another system bill would just move the
        // same problem along.
        if (!settleSourcesFor(bill.getBusiness()).contains(target.getBillSource())) {
            throw new RuntimeException(
                    "Bill " + target.getBillNumber() + " is not a hand-written bill for "
                  + bill.getBusiness() + ". Link only to a manual bill"
                  + (bill.getBusiness() == BusinessType.STATIONERY ? "." : " or a book bill."));
        }
        // The two settle against different books, so a link across businesses would take
        // a balance off one set of totals and never put it onto the other.
        if (target.getBusiness() != bill.getBusiness()) {
            throw new RuntimeException(
                    "Bill " + target.getBillNumber() + " is a " + target.getBusiness()
                  + " bill. Link only within the same business.");
        }
        // One hop only. A chain would leave the money two links away from the bill
        // being chased, and nobody reading either banner would find it.
        if (target.getSettledOn() != null) {
            throw new RuntimeException(
                    "Bill " + target.getBillNumber() + " is itself collected on "
                  + target.getSettledOn().getBillNumber() + ". Link to that one instead.");
        }
        if (!billRepository.findBySettledOnId(id).isEmpty()) {
            throw new RuntimeException(
                    "Other bills are already collected on this one, so it cannot be pointed "
                  + "elsewhere. Unlink those first.");
        }
        // Money already taken here means this bill is being collected after all, and the
        // link would hide a balance that is genuinely owed on it.
        if (bill.getAmountPaid() != null
                && bill.getAmountPaid().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException(
                    "This bill already has Rs " + bill.getAmountPaid().toPlainString()
                  + " collected on it. Reverse those payments before linking.");
        }

        bill.setSettledOn(target);
        bill.setSettledOnAt(LocalDateTime.now());
        bill.setSettledOnBy(by);
        bill.setSettledOnNote(note == null || note.isBlank() ? null : note.trim());

        // The target may already be settled, in which case this one is finished the
        // moment it is linked rather than at the next payment.
        if (target.getStatus() == BillStatus.COMPLETED
                || target.getStatus() == BillStatus.AWAITING_CONFIRMATION) {
            bill.setStatus(target.getStatus());
        }
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    /**
     * Undoes the link, putting the bill back where it was.
     *
     * <p>A bill closed only because the other one was paid goes back to open — its own
     * balance was never actually collected, and leaving it COMPLETED would write off
     * money nobody received.
     */
    @Transactional
    public BillResponse unlinkSettlement(Long id) {
        Bill bill = findBillById(id);
        if (bill.getSettledOn() == null) {
            throw new RuntimeException("This bill is not linked to another.");
        }
        boolean closedByTheLink =
                (bill.getStatus() == BillStatus.COMPLETED
                 || bill.getStatus() == BillStatus.AWAITING_CONFIRMATION)
                && !Boolean.TRUE.equals(bill.getFullyPaid());

        bill.setSettledOn(null);
        bill.setSettledOnAt(null);
        bill.setSettledOnBy(null);
        bill.setSettledOnNote(null);
        if (closedByTheLink) {
            bill.setStatus(BillStatus.STORE_RECEIVED);
        }
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    /**
     * The hand-written bills this one could be collected on.
     *
     * <p>Every unlinked manual bill of the same business — Rainco manual and book,
     * stationery manual. Not filtered by customer: the hand-written bill and the system
     * copy are written by different people at different times and the names seldom match
     * character for character, so filtering on them would hide the bill being looked for.
     * The admin knows which number they wrote and searches the list for it.
     */
    @Transactional(readOnly = true)
    public List<BillResponse> getSettleCandidates(Long id) {
        Bill bill = findBillById(id);
        return billRepository
                .findSettleCandidates(id, bill.getBusiness(), settleSourcesFor(bill.getBusiness()))
                .stream().map(this::toResponse).toList();
    }

    /** The bills being collected on this one — the other half of the banner. */
    @Transactional(readOnly = true)
    public List<BillResponse> getSettledByBills(Long id) {
        return billRepository.findBySettledOnId(id).stream().map(this::toResponse).toList();
    }
}