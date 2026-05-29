CREATE TABLE edit_requests (
    id                 BIGSERIAL PRIMARY KEY,
    type               VARCHAR(20)  NOT NULL,
    target_id          BIGINT       NOT NULL,
    target_ref         VARCHAR(100) NOT NULL,
    requested_changes  TEXT         NOT NULL,
    reason             TEXT,
    requested_by_id    BIGINT       NOT NULL REFERENCES users(id),
    requested_at       TIMESTAMP    NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by_id     BIGINT REFERENCES users(id),
    reviewed_at        TIMESTAMP,
    rejection_reason   TEXT
);

CREATE INDEX idx_edit_requests_status ON edit_requests(status);
CREATE INDEX idx_edit_requests_type_target ON edit_requests(type, target_id);