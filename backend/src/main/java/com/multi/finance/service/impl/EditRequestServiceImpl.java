package com.multi.finance.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.request.CreateEditRequestDto;
import com.multi.finance.dto.request.PaymentRequest;
import com.multi.finance.dto.response.EditRequestResponse;
import com.multi.finance.entity.EditRequest;
import com.multi.finance.entity.User;
import com.multi.finance.enums.EditRequestStatus;
import com.multi.finance.enums.EditRequestType;
import com.multi.finance.repository.EditRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EditRequestServiceImpl {

    private final EditRequestRepository editRequestRepository;
    private final BillServiceImpl billService;
    private final PaymentServiceImpl paymentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EditRequestResponse create(CreateEditRequestDto dto) {
        User caller = getCurrentUser();

        EditRequest req = EditRequest.builder()
                .type(dto.getType())
                .targetId(dto.getTargetId())
                .targetRef(dto.getTargetRef())
                .requestedChanges(dto.getRequestedChanges())
                .reason(dto.getReason())
                .requestedBy(caller)
                .requestedAt(LocalDateTime.now())
                .status(EditRequestStatus.PENDING)
                .build();

        return toResponse(editRequestRepository.save(req));
    }

    @Transactional(readOnly = true)
    public List<EditRequestResponse> getAll() {
        return editRequestRepository.findAllByOrderByRequestedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EditRequestResponse> getPending() {
        return editRequestRepository.findByStatusOrderByRequestedAtDesc(EditRequestStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public EditRequestResponse approve(Long id) {
        EditRequest req = findById(id);
        User reviewer = getCurrentUser();

        if (req.getStatus() != EditRequestStatus.PENDING) {
            throw new RuntimeException("Request is not pending");
        }

        applyChanges(req);

        req.setStatus(EditRequestStatus.APPROVED);
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());
        return toResponse(editRequestRepository.save(req));
    }

    @Transactional
    public EditRequestResponse reject(Long id, String rejectionReason) {
        EditRequest req = findById(id);
        User reviewer = getCurrentUser();

        if (req.getStatus() != EditRequestStatus.PENDING) {
            throw new RuntimeException("Request is not pending");
        }

        req.setStatus(EditRequestStatus.REJECTED);
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());
        req.setRejectionReason(rejectionReason);
        return toResponse(editRequestRepository.save(req));
    }

    private void applyChanges(EditRequest req) {
        try {
            Map<String, Object> changes = objectMapper.readValue(
                    req.getRequestedChanges(), new TypeReference<>() {});

            if (req.getType() == EditRequestType.BILL) {
                BillRequest billReq = objectMapper.convertValue(changes, BillRequest.class);
                billService.updateBill(req.getTargetId(), billReq);
            } else {
                PaymentRequest payReq = objectMapper.convertValue(changes, PaymentRequest.class);
                paymentService.updatePayment(req.getTargetId(), payReq);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply changes: " + e.getMessage());
        }
    }

    private EditRequest findById(Long id) {
        return editRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Edit request not found"));
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private EditRequestResponse toResponse(EditRequest r) {
        return EditRequestResponse.builder()
                .id(r.getId())
                .type(r.getType())
                .targetId(r.getTargetId())
                .targetRef(r.getTargetRef())
                .requestedChanges(r.getRequestedChanges())
                .reason(r.getReason())
                .requestedByName(r.getRequestedBy().getFullName())
                .requestedAt(r.getRequestedAt())
                .status(r.getStatus())
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .rejectionReason(r.getRejectionReason())
                .build();
    }
}