package com.multi.finance.entity;

import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private PaymentGroup group;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "cheque_number")
    private String chequeNumber;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    private String notes;

    @Column(name = "is_partial", nullable = false)
    private Boolean isPartial;

    @Column(name = "return_reason")
    private String returnReason;

    /**
     * The bill photographed when the payment was entered. Required of an accountant:
     * they are recording money already collected, and this is the only thing tying the
     * figure to the paper the customer signed.
     */
    @Column(name = "receipt_image_url", columnDefinition = "TEXT")
    private String receiptImageUrl;

    @Column(name = "receipt_uploaded_at")
    private LocalDateTime receiptUploadedAt;

    /**
     * The admin's own photograph, taken on confirmation. Optional and deliberately
     * separate: one records what the accountant saw, the other what the admin saw.
     */
    @Column(name = "confirm_image_url", columnDefinition = "TEXT")
    private String confirmImageUrl;

    @Column(name = "confirm_uploaded_at")
    private LocalDateTime confirmUploadedAt;

    // Set when this payment was pre-collected by the owner
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_note_id")
    private CollectionNote collectionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by_worker_id")
    private Worker collectedByWorker;

    @Column(name = "collector_note")
    private String collectorNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}