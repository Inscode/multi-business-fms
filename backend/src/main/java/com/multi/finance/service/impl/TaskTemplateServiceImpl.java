package com.multi.finance.service.impl;

import com.multi.finance.dto.request.TaskTemplateRequest;
import com.multi.finance.dto.response.TaskTemplateResponse;
import com.multi.finance.entity.TaskInstance;
import com.multi.finance.entity.TaskTemplate;
import com.multi.finance.enums.TaskFrequency;
import com.multi.finance.enums.TaskStatus;
import com.multi.finance.repository.TaskInstanceRepository;
import com.multi.finance.repository.TaskTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskTemplateServiceImpl {

    private final TaskTemplateRepository templateRepository;
    private final TaskInstanceRepository instanceRepository;

    @Transactional(readOnly = true)
    public List<TaskTemplateResponse> getAll() {
        return templateRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskTemplateResponse> getInactiveOnDemand() {
        return templateRepository.findByFrequencyAndActiveFalse(TaskFrequency.ON_DEMAND).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskTemplateResponse create(TaskTemplateRequest req) {
        TaskTemplate template = buildTemplate(req);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        templateRepository.save(template);

        // Always create today's instance immediately
        createInstance(template, LocalDate.now());

        // ONE_TIME: auto-deactivate so no future instances are generated
        if (template.getFrequency() == TaskFrequency.ONE_TIME) {
            template.setActive(false);
            templateRepository.save(template);
        }

        return toResponse(template);
    }

    @Transactional
    public TaskTemplateResponse update(Long id, TaskTemplateRequest req) {
        TaskTemplate template = findById(id);
        applyRequest(template, req);
        template.setUpdatedAt(LocalDateTime.now());
        return toResponse(templateRepository.save(template));
    }

    // Toggle active for DAILY templates (pause / resume)
    @Transactional
    public TaskTemplateResponse toggleActive(Long id) {
        TaskTemplate template = findById(id);
        template.setActive(!template.getActive());
        template.setUpdatedAt(LocalDateTime.now());
        return toResponse(templateRepository.save(template));
    }

    // Activate an ON_DEMAND template for today — creates today's instance
    @Transactional
    public TaskTemplateResponse activateOnDemand(Long id) {
        TaskTemplate template = findById(id);
        if (template.getFrequency() != TaskFrequency.ON_DEMAND) {
            throw new IllegalStateException("Only ON_DEMAND templates can be activated this way");
        }
        template.setActive(true);
        template.setUpdatedAt(LocalDateTime.now());
        templateRepository.save(template);

        LocalDate today = LocalDate.now();
        if (!instanceRepository.existsByTemplateAndDate(template, today)) {
            createInstance(template, today);
        }
        return toResponse(template);
    }

    // Deactivate an ON_DEMAND template — removes it from today's board
    @Transactional
    public TaskTemplateResponse deactivateOnDemand(Long id) {
        TaskTemplate template = findById(id);
        if (template.getFrequency() != TaskFrequency.ON_DEMAND) {
            throw new IllegalStateException("Only ON_DEMAND templates can be deactivated this way");
        }
        template.setActive(false);
        template.setUpdatedAt(LocalDateTime.now());
        return toResponse(templateRepository.save(template));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void createInstance(TaskTemplate template, LocalDate date) {
        if (instanceRepository.existsByTemplateAndDate(template, date)) return;
        instanceRepository.save(TaskInstance.builder()
                .template(template)
                .date(date)
                .status(TaskStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private TaskTemplate findById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task template not found: " + id));
    }

    private TaskTemplate buildTemplate(TaskTemplateRequest req) {
        return TaskTemplate.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .assignedRole(req.getAssignedRole())
                .frequency(req.getFrequency())
                .taskType(req.getTaskType())
                .businessUnit(req.getBusinessUnit())
                .dueTime(parseDueTime(req.getDueTime()))
                .urgencyLevel(req.getUrgencyLevel())
                .active(req.isActive())
                .build();
    }

    private void applyRequest(TaskTemplate t, TaskTemplateRequest req) {
        t.setTitle(req.getTitle());
        t.setDescription(req.getDescription());
        t.setAssignedRole(req.getAssignedRole());
        t.setFrequency(req.getFrequency());
        t.setTaskType(req.getTaskType());
        t.setBusinessUnit(req.getBusinessUnit());
        t.setDueTime(parseDueTime(req.getDueTime()));
        t.setUrgencyLevel(req.getUrgencyLevel());
        t.setActive(req.isActive());
    }

    private LocalTime parseDueTime(String dueTime) {
        if (dueTime == null || dueTime.isBlank()) return null;
        return LocalTime.parse(dueTime, DateTimeFormatter.ofPattern("HH:mm"));
    }

    public TaskTemplateResponse toResponse(TaskTemplate t) {
        return TaskTemplateResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .assignedRole(t.getAssignedRole())
                .frequency(t.getFrequency())
                .taskType(t.getTaskType())
                .businessUnit(t.getBusinessUnit())
                .dueTime(t.getDueTime() != null ? t.getDueTime().toString() : null)
                .urgencyLevel(t.getUrgencyLevel())
                .active(Boolean.TRUE.equals(t.getActive()))
                .createdAt(t.getCreatedAt())
                .build();
    }
}
