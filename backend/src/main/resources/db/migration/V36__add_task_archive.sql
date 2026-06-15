-- Archive table: mirrors task_instances but includes the full template snapshot
-- so archived records remain self-contained even if a template is later edited.
CREATE TABLE task_instances_archive (
    id              BIGINT       PRIMARY KEY,
    template_id     BIGINT,
    template_title  VARCHAR(255) NOT NULL,
    assigned_role   VARCHAR(30),
    frequency       VARCHAR(20),
    task_type       VARCHAR(30),
    business_unit   VARCHAR(30),
    date            DATE         NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    completed_at    TIMESTAMP,
    moved_reason    TEXT,
    moved_to_date   DATE,
    archived_at     TIMESTAMP    NOT NULL DEFAULT now(),
    created_at      TIMESTAMP
);

CREATE INDEX idx_archive_date         ON task_instances_archive (date);
CREATE INDEX idx_archive_template_id  ON task_instances_archive (template_id);
CREATE INDEX idx_archive_archived_at  ON task_instances_archive (archived_at);
