package com.multi.finance.service.impl;

import com.multi.finance.dto.request.TaskClarificationRequest;
import com.multi.finance.dto.response.TaskClarificationResponse;
import com.multi.finance.entity.*;
import com.multi.finance.enums.ClarificationType;
import com.multi.finance.enums.TaskAssignedRole;
import com.multi.finance.enums.TaskStatus;
import com.multi.finance.enums.UserRole;
import com.multi.finance.repository.TaskClarificationRepository;
import com.multi.finance.repository.TaskInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskClarificationServiceImpl {

    private final TaskClarificationRepository clarificationRepository;
    private final TaskInstanceRepository instanceRepository;

    @Transactional(readOnly = true)
    public List<TaskClarificationResponse> getForInstance(Long instanceId) {
        return clarificationRepository.findByInstanceIdOrdered(instanceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskClarificationResponse addClarification(Long instanceId, TaskClarificationRequest req) {
        TaskInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("Task instance not found: " + instanceId));

        User currentUser = getCurrentUser();
        assertCanInteract(currentUser, instance, req.getType());

        TaskClarification clarification = TaskClarification.builder()
                .instance(instance)
                .authorName(currentUser.getFullName())
                .authorRole(currentUser.getRole().name())
                .message(req.getMessage())
                .type(req.getType())
                .createdAt(LocalDateTime.now())
                .build();

        // Attach images
        if (req.getImageUrls() != null && !req.getImageUrls().isEmpty()) {
            List<ClarificationImage> images = req.getImageUrls().stream()
                    .map(url -> ClarificationImage.builder()
                            .clarification(clarification)
                            .imageUrl(url)
                            .createdAt(LocalDateTime.now())
                            .build())
                    .toList();
            clarification.getImages().addAll(images);
        }

        clarificationRepository.save(clarification);

        // Update instance status based on type
        if (req.getType() == ClarificationType.QUESTION) {
            instance.setStatus(TaskStatus.AWAITING_CLARIFICATION);
        } else {
            instance.setStatus(TaskStatus.PENDING);
        }
        instanceRepository.save(instance);

        return toResponse(clarification);
    }

    // ── Permission check ──────────────────────────────────────────────────────

    private void assertCanInteract(User user, TaskInstance instance, ClarificationType type) {
        UserRole role = user.getRole();
        if (role == UserRole.ADMIN || role == UserRole.OWNER) return;

        TaskAssignedRole assigned = instance.getTemplate().getAssignedRole();
        boolean allowed = switch (assigned) {
            case MAIN_ACCOUNTANT -> role == UserRole.MAIN_ACCOUNTANT;
            case ACCOUNTANT      -> role == UserRole.ACCOUNTANT;
            case DELIVERY        -> role == UserRole.WORKER;
        };

        if (!allowed) {
            throw new SecurityException("You are not authorized to interact with this task's clarifications");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private TaskClarificationResponse toResponse(TaskClarification c) {
        return TaskClarificationResponse.builder()
                .id(c.getId())
                .instanceId(c.getInstance().getId())
                .authorName(c.getAuthorName())
                .authorRole(c.getAuthorRole())
                .message(c.getMessage())
                .type(c.getType())
                .imageUrls(c.getImages().stream()
                        .map(ClarificationImage::getImageUrl)
                        .toList())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
