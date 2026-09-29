package com.multi.finance.entity;

import com.multi.finance.enums.EditRequestStatus;
import com.multi.finance.enums.EditRequestType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "edit_requests")
public class EditRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EditRequestType type;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private String targetRef;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String requestedChanges;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EditRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * The bill photographed, for a request that changes an amount.
     *
     * <p>Required only then. An amount changes what the customer owes, and the admin
     * approving it is otherwise taking somebody's word for a figure they cannot see. A
     * corrected spelling needs no such thing, and demanding one on every edit teaches
     * people to attach whatever is nearest.
     */
    @Column(name = "proof_image_url", columnDefinition = "TEXT")
    private String proofImageUrl;

    @Column(name = "proof_uploaded_at")
    private LocalDateTime proofUploadedAt;
}