package com.multi.finance.service.impl;

import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.User;
import com.multi.finance.entity.Worker;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.repository.BillRepository;
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
    private final UserRepository userRepository;

    public BillResponse createBill(BillRequest request) {
        User currentUser = getCurrentUser();

        Worker worker = null;
        if (request.getWorkerId() != null) {
            worker = workerRepository.findById(request.getWorkerId())
                    .orElseThrow(() -> new RuntimeException("Worker not found"));
        }

        Bill bill = Bill.builder()
                .billNumber(request.getBillNumber())
                .business(request.getBusiness())
                .division(request.getDivision())
                .billType(request.getBillType())
                .billSource(request.getBillSource())
                .customerName(request.getCustomerName())
                .totalAmount(request.getTotalAmount())
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

    @Transactional(readOnly=true)
    public List<BillResponse> getAllBills(BusinessType business) {
        if (business != null) {
            return billRepository.findByBusiness(business)
                    .stream().map(this::toResponse).toList();
        }
        return billRepository.findAll()
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
        Bill bill = getBillById(id);
        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        bill.setCurrentHolder(worker);
        bill.setStatus(BillStatus.ASSIGNED);
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional
    public BillResponse markReceived(Long id) {
        Bill bill = getBillById(id);
        if (!bill.getStatus().equals(BillStatus.ASSIGNED)) {
            throw new RuntimeException("Bill must be ASSIGNED before marking received");
        }
        bill.setStatus(BillStatus.RECEIVED);
        bill.setReceivedBy(getCurrentUser());
        bill.setReceivedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }


    @Transactional
    public BillResponse confirmBill(Long id) {
        Bill bill = getBillById(id);
        if (!bill.getStatus().equals(BillStatus.ASSIGNED) &&
                !bill.getStatus().equals(BillStatus.RECEIVED)) {
            throw new RuntimeException("Bill must be ASSIGNED or RECEIVED before confirming");
        }
        bill.setStatus(BillStatus.CONFIRMED);
        bill.setConfirmedBy(getCurrentUser());
        bill.setConfirmedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    @Transactional(readOnly = true)
    private Bill getBillById(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private BillResponse toResponse(Bill bill) {
        return BillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .business(bill.getBusiness())
                .division(bill.getDivision())
                .billType(bill.getBillType())
                .billSource(bill.getBillSource())
                .customerName(bill.getCustomerName())
                .totalAmount(bill.getTotalAmount())
                .status(bill.getStatus())
                .workerId(bill.getCurrentHolder() != null ? bill.getCurrentHolder().getId() : null)
                .workerName(bill.getCurrentHolder() != null ? bill.getCurrentHolder().getFullName() : null)
                .enteredByName(bill.getEnteredBy() != null ? bill.getEnteredBy().getFullName() : null)
                .receivedByName(bill.getReceivedBy() != null ? bill.getReceivedBy().getFullName() : null)
                .receivedAt(bill.getReceivedAt())
                .confirmedByName(bill.getConfirmedBy() != null ? bill.getConfirmedBy().getFullName() : null)
                .confirmedAt(bill.getConfirmedAt())
                .billDate(bill.getBillDate())
                .notes(bill.getNotes())
                .createdAt(bill.getCreatedAt())
                .build();
    }
}