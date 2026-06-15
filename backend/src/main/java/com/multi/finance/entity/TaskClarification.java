package com.multi.finance.entity;

import com.multi.finance.enums.ClarificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_clarifications")
public class TaskClarification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private TaskInstance instance;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_role", nullable = false)
    private String authorRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClarificationType type;

    @OneToMany(mappedBy = "clarification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClarificationImage> images = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
