package com.multi.finance.service.impl;

import com.multi.finance.dto.request.CollectionNoteBulkRequest;
import com.multi.finance.dto.request.CollectionNoteRequest;
import com.multi.finance.dto.response.CollectionNoteResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.CollectionNote;
import com.multi.finance.entity.User;
import com.multi.finance.enums.CollectionNoteStatus;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.CollectionNoteRepository;
import com.multi.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectionNoteServiceImpl {

    private final CollectionNoteRepository collectionNoteRepository;
    private final BillRepository billRepository;
    private final UserRepository userRepository;

    @Transactional
    public CollectionNoteResponse create(CollectionNoteRequest request) {
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        User owner = getCurrentUser();

        CollectionNote note = CollectionNote.builder()
                .bill(bill)
                .amount(request.getAmount())
                .paymentType(request.getPaymentType())
                .status(CollectionNoteStatus.PENDING)
                .collectedBy(owner)
                .collectedAt(LocalDateTime.now())
                .notes(request.getNotes())
                .chequeNumber(request.getChequeNumber())
                .bankName(request.getBankName())
                .branchName(request.getBranchName())
                .chequeDate(request.getChequeDate())
                .referenceNumber(request.getReferenceNumber())
                .build();

        return toResponse(collectionNoteRepository.save(note));
    }

    @Transactional
    public List<CollectionNoteResponse> createBulk(CollectionNoteBulkRequest request) {
        User owner = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        return request.getBills().stream().map(entry -> {
            Bill bill = billRepository.findById(entry.getBillId())
                    .orElseThrow(() -> new RuntimeException("Bill not found: " + entry.getBillId()));

            CollectionNote note = CollectionNote.builder()
                    .bill(bill)
                    .amount(entry.getAmount())
                    .paymentType(request.getPaymentType())
                    .status(CollectionNoteStatus.PENDING)
                    .collectedBy(owner)
                    .collectedAt(now)
                    .notes(request.getNotes())
                    .chequeNumber(request.getChequeNumber())
                    .bankName(request.getBankName())
                    .branchName(request.getBranchName())
                    .chequeDate(request.getChequeDate())
                    .referenceNumber(request.getReferenceNumber())
                    .build();

            return toResponse(collectionNoteRepository.save(note));
        }).toList();
    }

    // Owner sees their own collection notes
    @Transactional(readOnly = true)
    public List<CollectionNoteResponse> getMyNotes() {
        User owner = getCurrentUser();
        return collectionNoteRepository.findByCollectedById(owner.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Accountant sees all PENDING notes that need formal entry
    @Transactional(readOnly = true)
    public List<CollectionNoteResponse> getPendingNotes() {
        return collectionNoteRepository.findByStatus(CollectionNoteStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CollectionNoteResponse> getAll() {
        return collectionNoteRepository.findAll().stream()
                .sorted((a, b) -> b.getCollectedAt().compareTo(a.getCollectedAt()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CollectionNoteResponse> getByBillId(Long billId) {
        return collectionNoteRepository.findByBillId(billId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CollectionNoteResponse update(Long id, CollectionNoteRequest request) {
        CollectionNote note = collectionNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collection note not found"));
        note.setAmount(request.getAmount());
        note.setPaymentType(request.getPaymentType());
        if (request.getNotes() != null) note.setNotes(request.getNotes());
        return toResponse(collectionNoteRepository.save(note));
    }

    @Transactional
    public void delete(Long id) {
        collectionNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Collection note not found"));
        collectionNoteRepository.deleteById(id);
    }

    public CollectionNoteResponse toResponse(CollectionNote note) {
        return CollectionNoteResponse.builder()
                .id(note.getId())
                .billId(note.getBill().getId())
                .billNumber(note.getBill().getBillNumber())
                .customerName(note.getBill().getCustomerName())
                .area(note.getBill().getArea())
                .billBalance(note.getBill().getBalanceRemaining())
                .amount(note.getAmount())
                .paymentType(note.getPaymentType())
                .status(note.getStatus())
                .collectedByName(note.getCollectedBy().getFullName())
                .collectedAt(note.getCollectedAt())
                .notes(note.getNotes())
                .chequeNumber(note.getChequeNumber())
                .bankName(note.getBankName())
                .branchName(note.getBranchName())
                .chequeDate(note.getChequeDate())
                .referenceNumber(note.getReferenceNumber())
                .sourceEntryId(note.getSourceEntryId())
                .build();
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}