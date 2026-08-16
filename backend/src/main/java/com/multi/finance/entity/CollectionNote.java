package com.multi.finance.entity;

import com.multi.finance.enums.CollectionNoteStatus;
import com.multi.finance.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "collection_notes")
public class CollectionNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionNoteStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by", nullable = false)
    private User collectedBy;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    private String notes;

    // Cheque / bank-transfer details (all optional)
    @Column(name = "cheque_number")
    private String chequeNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "cheque_date")
    private java.time.LocalDate chequeDate;

    @Column(name = "reference_number")
    private String referenceNumber;

    /**
     * Photograph of the bill taken when the collection was marked. Optional: this
     * screen is used by admins and owners, who normally hold the paperwork. It is
     * carried onto the payment when the note is confirmed, so the evidence follows
     * the money rather than being stranded on the note.
     */
    @Column(name = "receipt_image_url", columnDefinition = "TEXT")
    private String receiptImageUrl;

    @Column(name = "receipt_uploaded_at")
    private java.time.LocalDateTime receiptUploadedAt;

    // Link back to the worker entry that generated this note (nullable for manually created notes)
    @Column(name = "source_entry_id")
    private Long sourceEntryId;
}