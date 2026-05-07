package com.multi.finance.service.impl;

import com.multi.finance.dto.request.AssignBillRequest;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.User;
import com.multi.finance.entity.Worker;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.UserRepository;
import com.multi.finance.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
                .status(worker != null ? "ASSIGNED" : "STOCK_ENTERED")
                .worker(worker)
                .enteredBy(currentUser)
                .billDate(request.getBillDate() != null ?
                        request.getBillDate() : LocalDate.now())
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return toResponse(billRepository.save(bill));
    }

    public List<BillResponse> getAllBills(String business) {
        if (business != null) {
            return billRepository.findByBusiness(business)
                    .stream().map(this::toResponse).toList();
        }
        return billRepository.findAll()
                .stream().map(this::toResponse).toList();
    }

    public List<BillResponse> getTodaysBills(String business) {
        return billRepository
                .findByBusinessAndBillDateAndStatusNot(
                        business, LocalDate.now(), "CANCELLED")
                .stream().map(this::toResponse).toList();
    }

    public List<BillResponse> getUnconfirmedBills(String business) {
        return billRepository
                .findUnconfirmedByBusinessAndDate(business, LocalDate.now())
                .stream().map(this::toResponse).toList();
    }

    public BillResponse assignBill(Long id, AssignBillRequest request) {
        Bill bill = getBillById(id);
        Worker worker = workerRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        bill.setWorker(worker);
        bill.setStatus("ASSIGNED");
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    public BillResponse markReceived(Long id) {
        Bill bill = getBillById(id);
        if (!bill.getStatus().equals("ASSIGNED")) {
            throw new RuntimeException("Bill must be ASSIGNED before marking received");
        }
        bill.setStatus("RECEIVED");
        bill.setReceivedBy(getCurrentUser());
        bill.setReceivedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

    public BillResponse confirmBill(Long id) {
        Bill bill = getBillById(id);
        if (!bill.getStatus().equals("RECEIVED")) {
            throw new RuntimeException("Bill must be RECEIVED before confirming");
        }
        bill.setStatus("CONFIRMED");
        bill.setConfirmedBy(getCurrentUser());
        bill.setConfirmedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        return toResponse(billRepository.save(bill));
    }

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
                .workerId(bill.getWorker() != null ? bill.getWorker().getId() : null)
                .workerName(bill.getWorker() != null ? bill.getWorker().getFullName() : null)
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