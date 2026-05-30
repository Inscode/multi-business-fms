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
}