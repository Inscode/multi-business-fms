package com.multi.finance.repository;

import com.multi.finance.entity.TaskClarification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskClarificationRepository extends JpaRepository<TaskClarification, Long> {

    @Query("""
        SELECT c FROM TaskClarification c
        LEFT JOIN FETCH c.images
        WHERE c.instance.id = :instanceId
        ORDER BY c.createdAt ASC
        """)
    List<TaskClarification> findByInstanceIdOrdered(@Param("instanceId") Long instanceId);
}
