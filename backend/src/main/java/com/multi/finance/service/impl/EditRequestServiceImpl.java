package com.multi.finance.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multi.finance.dto.request.BillRequest;
import com.multi.finance.dto.request.CreateEditRequestDto;
import com.multi.finance.dto.request.PaymentRequest;
import com.multi.finance.dto.response.EditRequestResponse;
import com.multi.finance.entity.EditRequest;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.Payment;
import com.multi.finance.entity.User;
import com.multi.finance.entity.BillNumberSkip;
import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.EditRequestStatus;
import com.multi.finance.enums.EditRequestType;
import com.multi.finance.repository.EditRequestRepository;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.PaymentRepository;
import com.multi.finance.repository.BillNumberSkipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EditRequestServiceImpl {

    private final EditRequestRepository editRequestRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final BillNumberSkipRepository billNumberSkipRepository;
    private final BillServiceImpl billService;
    private final PaymentServiceImpl paymentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public EditRequestResponse create(CreateEditRequestDto dto) {
        User caller = getCurrentUser();
        guardAmountProof(dto);

        EditRequest req = EditRequest.builder()
                .type(dto.getType())
                .targetId(dto.getTargetId())
                .targetRef(dto.getTargetRef())
                .requestedChanges(dto.getRequestedChanges())
                .reason(dto.getReason())
                .proofImageUrl(blankToNull(dto.getProofImageUrl()))
                .proofUploadedAt(blankToNull(dto.getProofImageUrl()) == null
                        ? null : LocalDateTime.now())
                .requestedBy(caller)
                .requestedAt(LocalDateTime.now())
                .status(EditRequestStatus.PENDING)
                .build();

        return toResponse(editRequestRepository.save(req));
    }

    /**
     * Requires a photograph when the request moves an amount.
     *
     * <p>Only then. Most edits correct how a bill reads — a misspelled shop, the wrong
     * area, a date a day out — and the typed reason is enough to judge them by. An
     * amount changes what the customer owes, and the admin approving it is being asked
     * to take somebody's word for a figure they cannot see.
     *
     * <p>Asking on every edit would attach a picture to a corrected spelling, and a rule
     * that fires on everything is one people learn to satisfy without reading.
     */
    private void guardAmountProof(CreateEditRequestDto dto) {
        if (!changesAnAmount(dto)) return;
        if (blankToNull(dto.getProofImageUrl()) != null) return;
        throw new RuntimeException(
                "Attach a photo of the bill — this request changes an amount, and the "
              + "admin approving it cannot see the paper.");
    }

    /**
     * Whether the requested changes move a figure.
     *
     * <p>Read out of the change payload rather than trusted from a flag the caller sets,
     * so a request cannot declare itself exempt.
     */
    private boolean changesAnAmount(CreateEditRequestDto dto) {
        String changes = dto.getRequestedChanges();
        if (changes == null) return false;
        try {
            JsonNode payload = objectMapper.readTree(changes);
            String field = dto.getType() == EditRequestType.BILL ? "totalAmount" : "amount";
            JsonNode requestedAmount = payload == null ? null : payload.get(field);
            if (requestedAmount == null || requestedAmount.isNull()) return false;
            if (!requestedAmount.isNumber()) {
                throw new RuntimeException("Requested amount must be a number");
            }

            BigDecimal currentAmount;
            if (dto.getType() == EditRequestType.BILL) {
                Bill bill = billRepository.findById(dto.getTargetId())
                        .orElseThrow(() -> new RuntimeException("Bill not found"));
                currentAmount = bill.getTotalAmount();
            } else {
                Payment payment = paymentRepository.findById(dto.getTargetId())
                        .orElseThrow(() -> new RuntimeException("Payment not found"));
                currentAmount = payment.getAmount();
            }

            // The form submits all fields, including the unchanged amount. Require
            // evidence only when the submitted amount differs from the saved record.
            return currentAmount == null || requestedAmount.decimalValue().compareTo(currentAmount) != 0;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Invalid requested changes", e);
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
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
    public EditRequestResponse approve(Long id, List<String> continuationNumbers) {
        EditRequest req = findById(id);
        User reviewer = getCurrentUser();

        if (req.getStatus() != EditRequestStatus.PENDING) {
            throw new RuntimeException("Request is not pending");
        }

        List<String> normalizedContinuations = normalizeContinuations(continuationNumbers);
        if (!normalizedContinuations.isEmpty()) {
            saveApprovedContinuations(req, reviewer, normalizedContinuations);
        }

        applyChanges(req);

        req.setStatus(EditRequestStatus.APPROVED);
        req.setReviewedBy(reviewer);
        req.setReviewedAt(LocalDateTime.now());
        return toResponse(editRequestRepository.save(req));
    }

    private List<String> normalizeContinuations(List<String> numbers) {
        if (numbers == null || numbers.isEmpty()) return List.of();
        return numbers.stream()
                .map(n -> n == null ? "" : n.trim())
                .filter(n -> !n.isBlank())
                .map(n -> {
                    if (!n.matches("[0-9]+") || n.chars().allMatch(c -> c == '0')) {
                        throw new RuntimeException("Continuation numbers must be positive whole numbers.");
                    }
                    return n.replaceFirst("^0+(?!$)", "");
                })
                .distinct()
                .toList();
    }

    private void saveApprovedContinuations(EditRequest request, User admin, List<String> numbers) {
        if (request.getType() != EditRequestType.BILL) {
            throw new RuntimeException("Continuation numbers can only be added to bill edit requests.");
        }
        Bill bill = billRepository.findById(request.getTargetId())
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        if (bill.getBillNumber() == null || !bill.getBillNumber().toUpperCase().startsWith("BK-")
                || (bill.getBillSource() != BillSource.MANUAL
                    && bill.getBillSource() != BillSource.MANUAL_BOOK)) {
            throw new RuntimeException("Continuation numbers apply only to BK physical-book bills.");
        }

        String business = bill.getBillSource() == BillSource.MANUAL_BOOK
                ? "PLASTIC" : bill.getBusiness().name();
        LocalDateTime now = LocalDateTime.now();
        List<BillNumberSkip> skips = numbers.stream().map(number -> {
            boolean alreadyUsed = billNumberSkipRepository
                    .existsByBusinessAndBillNumberAndStatusIn(business, number,
                            List.of("PENDING", "APPROVED"));
            if (alreadyUsed) {
                throw new RuntimeException("Continuation number " + number + " is already recorded.");
            }
            return BillNumberSkip.builder()
                    .business(business)
                    .billNumber(number)
                    .status("APPROVED")
                    .submittedBy(admin)
                    .reviewedBy(admin)
                    .reviewedAt(now)
                    .bill(bill)
                    .createdAt(now)
                    .build();
        }).toList();
        billNumberSkipRepository.saveAll(skips);
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
                .proofImageUrl(r.getProofImageUrl())
                .requestedByName(r.getRequestedBy().getFullName())
                .requestedAt(r.getRequestedAt())
                .status(r.getStatus())
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .rejectionReason(r.getRejectionReason())
                .build();
    }
}
