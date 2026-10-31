-- Work orders represent the execution of a service request.
-- One work order per request in this first implementation; multiple appointments
-- can be associated with the same work order as follow-up visits are scheduled.
CREATE TABLE serviq_work_order (
    work_order_id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES serviq_service_request(request_id),
    work_order_number BIGINT GENERATED ALWAYS AS IDENTITY,
    work_order_status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    diagnosis TEXT,
    resolution_notes TEXT,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_serviq_work_order_request UNIQUE (request_id),
    CONSTRAINT uq_serviq_work_order_number UNIQUE (work_order_number),
    CONSTRAINT chk_serviq_work_order_status CHECK (
        work_order_status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT chk_serviq_work_order_times CHECK (
        (started_at IS NULL OR started_at >= opened_at)
        AND (completed_at IS NULL OR completed_at >= opened_at)
        AND (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
    ),
    CONSTRAINT chk_serviq_work_order_completed CHECK (
        (work_order_status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (work_order_status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

-- Optional link to a visit. No appointment is required to open a work order.
-- An appointment may only be associated with one work order.
CREATE TABLE serviq_work_order_appointment (
    work_order_id UUID NOT NULL REFERENCES serviq_work_order(work_order_id),
    appointment_id UUID NOT NULL UNIQUE REFERENCES serviq_appointment(appointment_id),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (work_order_id, appointment_id)
);

-- Append-only history is enforced at the application layer with INSERT-only access.
CREATE TABLE serviq_work_order_status_history (
    status_history_id UUID PRIMARY KEY,
    work_order_id UUID NOT NULL REFERENCES serviq_work_order(work_order_id),
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    change_reason TEXT,
    changed_by VARCHAR(150) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_serviq_wo_previous_status CHECK (
        previous_status IS NULL OR previous_status IN (
            'OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_serviq_wo_new_status CHECK (
        new_status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT chk_serviq_wo_changed_by CHECK (length(btrim(changed_by)) > 0)
);

-- Monetary values are estimates / charges in one currency per work order.
-- Explicit unit prices and quantities preserve historical values.
CREATE TABLE serviq_work_order_line (
    line_id UUID PRIMARY KEY,
    work_order_id UUID NOT NULL REFERENCES serviq_work_order(work_order_id),
    line_type VARCHAR(12) NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(14,2) NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'PHP',
    line_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_serviq_wo_line_type CHECK (line_type IN ('LABOR', 'PART', 'FEE')),
    CONSTRAINT chk_serviq_wo_line_description CHECK (length(btrim(description)) > 0),
    CONSTRAINT chk_serviq_wo_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_serviq_wo_line_price CHECK (unit_price >= 0),
    CONSTRAINT chk_serviq_wo_line_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);

-- Service notes are separate from the final diagnosis/resolution.
CREATE TABLE serviq_work_order_note (
    note_id UUID PRIMARY KEY,
    work_order_id UUID NOT NULL REFERENCES serviq_work_order(work_order_id),
    note_text TEXT NOT NULL,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_serviq_wo_note_text CHECK (length(btrim(note_text)) > 0),
    CONSTRAINT chk_serviq_wo_note_author CHECK (length(btrim(created_by)) > 0)
);

CREATE INDEX idx_serviq_wo_status_opened
    ON serviq_work_order(work_order_status, opened_at DESC);
CREATE INDEX idx_serviq_wo_history_order_time
    ON serviq_work_order_status_history(work_order_id, changed_at DESC);
CREATE INDEX idx_serviq_wo_lines_order
    ON serviq_work_order_line(work_order_id);
CREATE INDEX idx_serviq_wo_notes_order_time
    ON serviq_work_order_note(work_order_id, created_at DESC);
