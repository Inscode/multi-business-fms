package com.multi.finance.service.impl;

import com.multi.finance.dto.request.UpdateTaskStatusRequest;
import com.multi.finance.dto.response.TaskInstanceResponse;
import com.multi.finance.dto.response.TaskTemplateResponse;
import com.multi.finance.entity.TaskAttachment;
import com.multi.finance.entity.TaskInstance;
import com.multi.finance.entity.TaskTemplate;
import com.multi.finance.enums.TaskFrequency;
import com.multi.finance.enums.TaskStatus;
import com.multi.finance.repository.TaskAttachmentRepository;
import com.multi.finance.repository.TaskInstanceRepository;
import com.multi.finance.repository.TaskTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskInstanceServiceImpl {

    private final TaskInstanceRepository instanceRepository;
    private final TaskTemplateRepository templateRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final TaskTemplateServiceImpl templateService;

    /**
     * Returns instances for a given date.
     * For today: auto-generates DAILY instances from active templates if not yet created.
     */
    @Transactional
    public List<TaskInstanceResponse> getForDate(LocalDate date) {
        if (date.equals(LocalDate.now())) {
            generateDailyInstancesIfNeeded(date);
        }
        return instanceRepository.findAllForDate(date).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskInstanceResponse> getHistory(LocalDate from, LocalDate to) {
        return instanceRepository.findForDateRange(from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskInstanceResponse updateStatus(Long id, UpdateTaskStatusRequest req) {
        TaskInstance instance = findById(id);

        instance.setStatus(req.getStatus());

        if (req.getStatus() == TaskStatus.COMPLETED) {
            instance.setCompletedAt(LocalDateTime.now());
            instance.setMovedReason(null);
            instance.setMovedToDate(null);
        } else if (req.getStatus() == TaskStatus.MOVED) {
            LocalDate moveTo = req.getMovedToDate() != null
                    ? req.getMovedToDate()
                    : LocalDate.now().plusDays(1);
            instance.setMovedReason(req.getMovedReason());
            instance.setMovedToDate(moveTo);
            instance.setCompletedAt(null);
        } else if (req.getStatus() == TaskStatus.PENDING) {
            // Undo — reset all fields
            instance.setCompletedAt(null);
            instance.setMovedReason(null);
            instance.setMovedToDate(null);
        }

        return toResponse(instanceRepository.save(instance));
    }

    @Transactional
    public TaskInstanceResponse addAttachment(Long instanceId, String imageUrl) {
        TaskInstance instance = findById(instanceId);
        TaskAttachment att = TaskAttachment.builder()
                .instance(instance)
                .imageUrl(imageUrl)
                .uploadedAt(LocalDateTime.now())
                .build();
        attachmentRepository.save(att);
        instance.getAttachments().add(att);
        return toResponse(instance);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void generateDailyInstancesIfNeeded(LocalDate date) {
        List<TaskTemplate> dailyTemplates =
                templateRepository.findByFrequencyAndActiveTrue(TaskFrequency.DAILY);
        for (TaskTemplate template : dailyTemplates) {
            if (!instanceRepository.existsByTemplateAndDate(template, date)) {
                instanceRepository.save(TaskInstance.builder()
                        .template(template)
                        .date(date)
                        .status(TaskStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
    }

    private TaskInstance findById(Long id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task instance not found: " + id));
    }

    public TaskInstanceResponse toResponse(TaskInstance i) {
        return TaskInstanceResponse.builder()
                .id(i.getId())
                .template(templateService.toResponse(i.getTemplate()))
                .date(i.getDate())
                .status(i.getStatus())
                .completedAt(i.getCompletedAt())
                .movedReason(i.getMovedReason())
                .movedToDate(i.getMovedToDate())
                .attachmentUrls(i.getAttachments().stream()
                        .map(TaskAttachment::getImageUrl)
                        .toList())
                .build();
    }
}
