-- How a bill reached the customer, and which lorry round it went on.
--
-- Bills leave in one of three ways: on a route round to an area, as an immediate
-- delivery, or collected from the store. Only the first needs grouping — a round is a
-- real event with a lorry, a date and fifteen or twenty drops, and the question the
-- admin actually asks is "how many bills went on that lorry and to whom". That is a
-- record, not a filter over area and date.
--
-- A run covers every business: one lorry carries Rainco, Stationery and Plastic
-- together, so the bills on it do too.

-- The recurring rounds — Bandarawela, Badulla, Haputale, Diyatalawa and the rest.
-- A managed list rather than free text: the same route spelt three ways would split
-- the counts the whole feature exists to produce.
CREATE TABLE IF NOT EXISTS route_areas (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_route_areas_name
    ON route_areas (UPPER(name));

-- One trip: the day it goes out, the areas it covers, and the bills it carried.
--
-- Areas are a list rather than a single column because one lorry often does two or
-- three rounds together — Bandarawela, Haputale and Diyatalawa on the same trip. A
-- single area per run would force that into three records and split one load's counts.
CREATE TABLE IF NOT EXISTS delivery_runs (
    id            BIGSERIAL PRIMARY KEY,
    planned_date  DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    notes         VARCHAR(300),

    -- Who is entering bills into it, so a run left open overnight is attributable.
    opened_by     VARCHAR(100),
    opened_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    closed_by     VARCHAR(100),
    closed_at     TIMESTAMP,

    CONSTRAINT delivery_runs_status_check
        CHECK (status IN ('OPEN','DISPATCHED','COMPLETED','CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_runs_date ON delivery_runs (planned_date DESC);

-- The areas one trip covers.
CREATE TABLE IF NOT EXISTS delivery_run_areas (
    run_id        BIGINT NOT NULL REFERENCES delivery_runs(id) ON DELETE CASCADE,
    route_area_id BIGINT NOT NULL REFERENCES route_areas(id),
    PRIMARY KEY (run_id, route_area_id)
);

CREATE INDEX IF NOT EXISTS idx_delivery_run_areas_area
    ON delivery_run_areas (route_area_id);

-- A run created by an earlier version carried one area in a column. Move it across
-- before the column goes, so nothing already entered loses its route.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'delivery_runs' AND column_name = 'route_area_id') THEN
        INSERT INTO delivery_run_areas (run_id, route_area_id)
        SELECT id, route_area_id FROM delivery_runs WHERE route_area_id IS NOT NULL
        ON CONFLICT DO NOTHING;

        ALTER TABLE delivery_runs DROP COLUMN route_area_id;
    END IF;
END $$;

-- ── The bill's side ──────────────────────────────────────────────────────────
-- Existing bills predate the distinction, so they are left UNSPECIFIED rather than
-- guessed into a mode that would then be reported as fact.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS delivery_mode   VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED',
    ADD COLUMN IF NOT EXISTS delivery_run_id BIGINT;

ALTER TABLE bills DROP CONSTRAINT IF EXISTS bills_delivery_mode_check;
ALTER TABLE bills ADD CONSTRAINT bills_delivery_mode_check
    CHECK (delivery_mode IN ('UNSPECIFIED','ROUTE','IMMEDIATE','STORE_PICKUP'));

ALTER TABLE bills DROP CONSTRAINT IF EXISTS fk_bills_delivery_run;
ALTER TABLE bills ADD CONSTRAINT fk_bills_delivery_run
    FOREIGN KEY (delivery_run_id) REFERENCES delivery_runs(id);

CREATE INDEX IF NOT EXISTS idx_bills_delivery_run ON bills (delivery_run_id);

-- The table may already exist, created by Hibernate from the entity on an earlier
-- start. Hibernate emits the columns without defaults, so an insert that omits them
-- fails on NOT NULL — these make the table behave the same however it was created.
ALTER TABLE route_areas ALTER COLUMN active     SET DEFAULT TRUE;
ALTER TABLE route_areas ALTER COLUMN sort_order SET DEFAULT 0;
ALTER TABLE route_areas ALTER COLUMN created_at SET DEFAULT NOW();

-- The rounds themselves.
--
-- Taken from the area list the customer form already offers, so a customer's area and
-- a lorry round are drawn from one vocabulary. Two lists would drift, and a round whose
-- name does not match its customers cannot be counted against them.
--
-- Deliberately NOT seeded from the areas found on bills: that sweep pulled in every
-- place a bill was ever addressed to — Jaffna, Colombo 3, Matara, one bill each —
-- which are customers who happened to be somewhere, not rounds.
--
-- Maintained afterwards in Deliveries > Routes.
INSERT INTO route_areas (name, active, sort_order, created_at) VALUES
    ('Ambagasdowa', TRUE, 10, NOW()),
    ('Badalkumbura', TRUE, 20, NOW()),
    ('Badulla', TRUE, 30, NOW()),
    ('Bandarawela', TRUE, 40, NOW()),
    ('Beragala', TRUE, 50, NOW()),
    ('Bogakumbura', TRUE, 60, NOW()),
    ('Boralanda', TRUE, 70, NOW()),
    ('Demodara', TRUE, 80, NOW()),
    ('Diyatalawa', TRUE, 90, NOW()),
    ('Ella', TRUE, 100, NOW()),
    ('Etampitiya', TRUE, 110, NOW()),
    ('Haldummulla', TRUE, 120, NOW()),
    ('Hali-Ela', TRUE, 130, NOW()),
    ('Hasalaka', TRUE, 140, NOW()),
    ('Haputale', TRUE, 150, NOW()),
    ('Hopton', TRUE, 160, NOW()),
    ('Kandaketiya', TRUE, 170, NOW()),
    ('Keppatipola', TRUE, 180, NOW()),
    ('Kumbalwela', TRUE, 190, NOW()),
    ('Lunugala', TRUE, 200, NOW()),
    ('Lunuwatta', TRUE, 210, NOW()),
    ('Mahiyanganaya', TRUE, 220, NOW()),
    ('Meegahakivula', TRUE, 230, NOW()),
    ('Passara', TRUE, 240, NOW()),
    ('Uva-Paranagama', TRUE, 250, NOW()),
    ('Welimada', TRUE, 260, NOW())
ON CONFLICT DO NOTHING;
