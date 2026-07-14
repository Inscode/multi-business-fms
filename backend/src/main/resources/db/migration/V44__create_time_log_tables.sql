-- Worker daily attendance status (present/absent/holiday marker)
CREATE TABLE worker_attendance (
    id BIGSERIAL PRIMARY KEY,
    worker_id BIGINT NOT NULL REFERENCES workers(id),
    attendance_date DATE NOT NULL,
    attendance_type VARCHAR(20) NOT NULL,   -- PRESENT, HALF_DAY, ABSENT, HOLIDAY
    notes TEXT,
    recorded_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_worker_attendance UNIQUE (worker_id, attendance_date)
);

-- Individual clock-in / clock-out entries per worker per day.
-- Multiple entries per day are supported (handles lunch/short breaks).
-- Worked minutes for a day = SUM(clock_out - clock_in) for all completed entries.
CREATE TABLE worker_time_entries (
    id BIGSERIAL PRIMARY KEY,
    worker_id  BIGINT NOT NULL REFERENCES workers(id),
    entry_date DATE NOT NULL,
    clock_in   TIME NOT NULL,
    clock_out  TIME,                        -- NULL = still clocked in / not yet closed
    notes TEXT,
    recorded_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Company holidays (applies to all or selected workers)
CREATE TABLE company_holidays (
    id BIGSERIAL PRIMARY KEY,
    holiday_date DATE NOT NULL,
    name VARCHAR(200) NOT NULL,
    holiday_type VARCHAR(30) NOT NULL,      -- POYA, NATIONAL, PUBLIC, OTHER
    applies_to_all BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Workers for whom a non-all holiday applies
CREATE TABLE company_holiday_workers (
    holiday_id BIGINT NOT NULL REFERENCES company_holidays(id) ON DELETE CASCADE,
    worker_id  BIGINT NOT NULL REFERENCES workers(id),
    PRIMARY KEY (holiday_id, worker_id)
);

CREATE INDEX idx_worker_attendance_date   ON worker_attendance(attendance_date);
CREATE INDEX idx_worker_attendance_worker ON worker_attendance(worker_id);
CREATE INDEX idx_time_entries_worker_date ON worker_time_entries(worker_id, entry_date);
CREATE INDEX idx_time_entries_date        ON worker_time_entries(entry_date);
CREATE INDEX idx_company_holidays_date    ON company_holidays(holiday_date);
