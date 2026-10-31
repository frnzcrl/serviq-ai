
-- GiST support for UUID equality in exclusion constraints.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE serviq_technician (
    technician_id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email_address VARCHAR(255),
    phone_number VARCHAR(30),
    time_zone VARCHAR(80) NOT NULL DEFAULT 'Asia/Manila',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_technician_name
        CHECK (length(btrim(full_name)) > 0)
);

CREATE TABLE serviq_technician_skill (
    technician_id UUID NOT NULL
        REFERENCES serviq_technician(technician_id),
    asset_category VARCHAR(60) NOT NULL,

    PRIMARY KEY (technician_id, asset_category)
);

CREATE TABLE serviq_technician_working_hours (
    working_hours_id UUID PRIMARY KEY,
    technician_id UUID NOT NULL
        REFERENCES serviq_technician(technician_id),
    day_of_week SMALLINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    CONSTRAINT chk_working_day
        CHECK (day_of_week BETWEEN 1 AND 7),

    CONSTRAINT chk_working_hours
        CHECK (start_time < end_time),

    CONSTRAINT uq_technician_working_period
        UNIQUE (technician_id, day_of_week, start_time, end_time)
);

CREATE TABLE serviq_technician_time_off (
    time_off_id UUID PRIMARY KEY,
    technician_id UUID NOT NULL
        REFERENCES serviq_technician(technician_id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255),

    CONSTRAINT chk_time_off_period
        CHECK (starts_at < ends_at)
);

CREATE TABLE serviq_appointment (
    appointment_id UUID PRIMARY KEY,
    request_id UUID NOT NULL
        REFERENCES serviq_service_request(request_id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    appointment_status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_appointment_period
        CHECK (starts_at < ends_at),

    CONSTRAINT chk_appointment_status
        CHECK (appointment_status IN (
            'SCHEDULED', 'CONFIRMED',
            'IN_PROGRESS', 'COMPLETED',
            'CANCELLED'
        ))
);

CREATE TABLE serviq_appointment_technician (
    assignment_id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL
        REFERENCES serviq_appointment(appointment_id),
    technician_id UUID NOT NULL
        REFERENCES serviq_technician(technician_id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_assignment_period
        CHECK (starts_at < ends_at),

    CONSTRAINT uq_appointment_technician
        UNIQUE (appointment_id, technician_id),

    CONSTRAINT ex_technician_booking_overlap
        EXCLUDE USING gist (
            technician_id WITH =,
            tstzrange(starts_at, ends_at, '[)') WITH &&
        )
        WHERE (active)
);

CREATE INDEX idx_technician_active
    ON serviq_technician(technician_id)
    WHERE active;

CREATE INDEX idx_technician_skill_category
    ON serviq_technician_skill(asset_category);

CREATE INDEX idx_working_hours_technician_day
    ON serviq_technician_working_hours(
        technician_id, day_of_week
    );

CREATE INDEX idx_time_off_technician_period
    ON serviq_technician_time_off(
        technician_id, starts_at, ends_at
    );

CREATE INDEX idx_appointment_request
    ON serviq_appointment(request_id);

CREATE INDEX idx_appointment_schedule
    ON serviq_appointment(
        starts_at, appointment_status
    );

CREATE INDEX idx_assignment_appointment
    ON serviq_appointment_technician(appointment_id);
