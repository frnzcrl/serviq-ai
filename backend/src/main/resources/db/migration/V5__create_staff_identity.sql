-- Serviq staff identity foundation. Does not enable API authentication by itself.
-- Password hashes will be produced by the application using a slow password hasher.
CREATE TABLE serviq_staff_user (
    user_id UUID PRIMARY KEY,
    email_address VARCHAR(255) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_serviq_staff_email_not_empty CHECK (length(btrim(email_address)) > 3),
    CONSTRAINT chk_serviq_staff_name_not_empty CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT chk_serviq_staff_password_not_empty CHECK (length(btrim(password_hash)) > 0)
);

-- Unique case-insensitive staff email; application normalizes email before storing.
CREATE UNIQUE INDEX uq_serviq_staff_email_normalized
    ON serviq_staff_user (lower(btrim(email_address)));

CREATE TABLE serviq_staff_user_role (
    user_id UUID NOT NULL REFERENCES serviq_staff_user(user_id) ON DELETE CASCADE,
    role_code VARCHAR(24) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT chk_serviq_staff_role CHECK (role_code IN ('ADMIN', 'DISPATCHER', 'TECHNICIAN', 'VIEWER'))
);

-- Optional one-to-one identity link for staff who are also field technicians.
CREATE TABLE serviq_staff_technician_link (
    user_id UUID PRIMARY KEY REFERENCES serviq_staff_user(user_id) ON DELETE CASCADE,
    technician_id UUID NOT NULL UNIQUE REFERENCES serviq_technician(technician_id)
);

-- Append-only intent: application inserts audit events, never updates them.
CREATE TABLE serviq_staff_security_audit (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES serviq_staff_user(user_id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    target_user_id UUID REFERENCES serviq_staff_user(user_id) ON DELETE SET NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    details TEXT,
    CONSTRAINT chk_serviq_security_action CHECK (length(btrim(action)) > 0)
);

CREATE INDEX idx_serviq_security_audit_actor_time
    ON serviq_staff_security_audit(actor_user_id, occurred_at DESC);
CREATE INDEX idx_serviq_security_audit_time
    ON serviq_staff_security_audit(occurred_at DESC);
