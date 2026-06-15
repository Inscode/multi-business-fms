CREATE TABLE task_templates (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    assigned_role VARCHAR(30) NOT NULL,
    frequency   VARCHAR(20)  NOT NULL,
    task_type   VARCHAR(30)  NOT NULL,
    business_unit VARCHAR(30),
    due_time    TIME,
    urgency_level VARCHAR(10) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE task_instances (
    id             BIGSERIAL PRIMARY KEY,
    template_id    BIGINT NOT NULL REFERENCES task_templates(id),
    date           DATE   NOT NULL,
    status         VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    completed_at   TIMESTAMP,
    moved_reason   TEXT,
    moved_to_date  DATE,
    created_at     TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_task_instances_template_date
    ON task_instances(template_id, date);

CREATE INDEX idx_task_instances_date     ON task_instances(date);
CREATE INDEX idx_task_instances_template ON task_instances(template_id);

CREATE TABLE task_attachments (
    id          BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES task_instances(id),
    image_url   TEXT   NOT NULL,
    uploaded_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE task_clarifications (
    id          BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES task_instances(id),
    author_name VARCHAR(100) NOT NULL,
    author_role VARCHAR(50)  NOT NULL,
    message     TEXT         NOT NULL,
    type        VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_task_clarifications_instance ON task_clarifications(instance_id);

CREATE TABLE clarification_images (
    id                BIGSERIAL PRIMARY KEY,
    clarification_id  BIGINT NOT NULL REFERENCES task_clarifications(id),
    image_url         TEXT   NOT NULL,
    created_at        TIMESTAMP DEFAULT NOW()
);
