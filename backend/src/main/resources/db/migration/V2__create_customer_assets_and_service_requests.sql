-- V2: Customer addresses, assets, and service intake

-- Customers may share an email address or have no email.
ALTER TABLE serviq_customer
    DROP CONSTRAINT uq_serviq_customer_email;

ALTER TABLE serviq_customer
    ALTER COLUMN email_address DROP NOT NULL;

-- Used by composite foreign keys to enforce ownership.
ALTER TABLE serviq_customer
    ADD CONSTRAINT uq_customer_id_pair
    UNIQUE (customer_id, full_name);

-- Remove this temporary redundant constraint below;
-- customer_id is already the primary key.
ALTER TABLE serviq_customer
    DROP CONSTRAINT uq_customer_id_pair;

-- Customer service locations
CREATE TABLE serviq_customer_address (
    address_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    address_label VARCHAR(80),
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120),
    postal_code VARCHAR(30),
    country_code CHAR(2) NOT NULL DEFAULT 'PH',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_address_customer
        FOREIGN KEY (customer_id)
        REFERENCES serviq_customer(customer_id),

    CONSTRAINT uq_address_customer
        UNIQUE (customer_id, address_id)
);

-- Customer-owned appliances and equipment
CREATE TABLE serviq_service_asset (
    asset_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    asset_category VARCHAR(60) NOT NULL,
    manufacturer VARCHAR(120),
    model_number VARCHAR(120),
    serial_number VARCHAR(150),
    asset_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_asset_customer
        FOREIGN KEY (customer_id)
        REFERENCES serviq_customer(customer_id),

    CONSTRAINT uq_asset_customer
        UNIQUE (customer_id, asset_id)
);

-- Service requests
CREATE TABLE serviq_service_request (
    request_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    address_id UUID NOT NULL,
    asset_id UUID,
    issue_summary VARCHAR(200) NOT NULL,
    issue_description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    request_status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    intake_channel VARCHAR(30) NOT NULL DEFAULT 'STAFF',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_request_customer
        FOREIGN KEY (customer_id)
        REFERENCES serviq_customer(customer_id),

    CONSTRAINT fk_request_customer_address
        FOREIGN KEY (customer_id, address_id)
        REFERENCES serviq_customer_address(customer_id, address_id),

    CONSTRAINT fk_request_customer_asset
        FOREIGN KEY (customer_id, asset_id)
        REFERENCES serviq_service_asset(customer_id, asset_id),

    CONSTRAINT chk_request_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),

    CONSTRAINT chk_request_status
        CHECK (request_status IN (
            'NEW', 'TRIAGED', 'SCHEDULED',
            'IN_PROGRESS', 'RESOLVED', 'CANCELLED'
        )),

    CONSTRAINT chk_intake_channel
        CHECK (intake_channel IN (
            'STAFF', 'CUSTOMER', 'AI_ASSISTED'
        )),

    CONSTRAINT chk_request_summary_not_blank
        CHECK (length(btrim(issue_summary)) > 0),

    CONSTRAINT chk_request_description_not_blank
        CHECK (length(btrim(issue_description)) > 0)
);

-- Query-oriented indexes
CREATE INDEX idx_customer_email_lookup
    ON serviq_customer (lower(email_address))
    WHERE email_address IS NOT NULL;

CREATE INDEX idx_address_customer
    ON serviq_customer_address(customer_id);

CREATE INDEX idx_asset_customer
    ON serviq_service_asset(customer_id);

CREATE INDEX idx_request_customer_created
    ON serviq_service_request(customer_id, created_at DESC);

CREATE INDEX idx_request_status_created
    ON serviq_service_request(request_status, created_at DESC);

CREATE INDEX idx_request_address_fk
    ON serviq_service_request(customer_id, address_id);

CREATE INDEX idx_request_asset_fk
    ON serviq_service_request(customer_id, asset_id)
    WHERE asset_id IS NOT NULL;
