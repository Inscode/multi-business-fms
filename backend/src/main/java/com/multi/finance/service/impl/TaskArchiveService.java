package com.multi.finance.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskArchiveService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Runs every day at 01:30 AM.
     * Moves task instances older than 30 days whose status is COMPLETED or MOVED
     * into task_instances_archive, then deletes them from the live table.
     * PENDING / AWAITING_CLARIFICATION tasks are never archived regardless of age.
     */
    @Scheduled(cron = "0 30 1 * * *")
    @Transactional
    public void archiveOldInstances() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        log.info("Task archiver running — cutoff date: {}", cutoff);

        // 1. Copy qualifying rows into the archive table (single INSERT … SELECT)
        int archived = em.createNativeQuery("""
                INSERT INTO task_instances_archive
                    (id, template_id, template_title, assigned_role, frequency,
                     task_type, business_unit, date, status, completed_at,
                     moved_reason, moved_to_date, archived_at, created_at)
                SELECT
                    i.id,
                    i.template_id,
                    t.title,
                    t.assigned_role,
                    t.frequency,
                    t.task_type,
                    t.business_unit,
                    i.date,
                    i.status,
                    i.completed_at,
                    i.moved_reason,
                    i.moved_to_date,
                    now(),
                    i.created_at
                FROM task_instances i
                JOIN task_templates t ON t.id = i.template_id
                WHERE i.date < :cutoff
                  AND i.status IN ('COMPLETED', 'MOVED')
                ON CONFLICT (id) DO NOTHING
                """)
                .setParameter("cutoff", cutoff)
                .executeUpdate();

        if (archived == 0) {
            log.info("Task archiver: nothing to archive.");
            return;
        }

        // 2. Delete the rows we just copied
        int deleted = em.createNativeQuery("""
                DELETE FROM task_instances
                WHERE date < :cutoff
                  AND status IN ('COMPLETED', 'MOVED')
                """)
                .setParameter("cutoff", cutoff)
                .executeUpdate();

        log.info("Task archiver: archived {} and removed {} task instances older than {}.",
                archived, deleted, cutoff);
    }
}
