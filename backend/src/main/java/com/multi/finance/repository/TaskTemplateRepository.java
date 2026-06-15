package com.multi.finance.repository;

import com.multi.finance.entity.TaskTemplate;
import com.multi.finance.enums.TaskFrequency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, Long> {

    List<TaskTemplate> findByFrequencyAndActiveTrue(TaskFrequency frequency);

    List<TaskTemplate> findByFrequencyAndActiveFalse(TaskFrequency frequency);
}
