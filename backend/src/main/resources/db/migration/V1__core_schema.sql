-- =====================================================================================
-- V1: core schema for the Enterprise Customer Management Platform
--
-- Conventions
--   * snake_case identifiers, singular column names, plural table names
--   * every business table carries the four audit columns stamped by BaseAuditEntity
--   * enums are persisted as VARCHAR (EnumType.STRING) rather than ordinals so that
--     reordering a Java enum can never silently re-label existing rows
--   * DATETIME(6) everywhere so Instant values round-trip at microsecond precision
--
-- Index strategy (see the notes above each index for the query it serves)
--   Hibernate runs with ddl-auto=validate, so this file is the single source of truth
--   for the physical model. Column lengths below intentionally mirror the @Column
--   declarations exactly - a mismatch fails fast at boot instead of at runtime.
-- =====================================================================================

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    first_name    VARCHAR(80)  NOT NULL,
    last_name     VARCHAR(80)  NOT NULL,
    enabled       BIT(1)       NOT NULL DEFAULT 1,
    last_login_at DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(180) NULL,
    updated_by    VARCHAR(180) NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The composite PK already indexes (user_id, role_id); this covers the reverse lookup
-- "which users hold role X", used by the admin user list filter.
CREATE INDEX idx_user_roles_role ON user_roles (role_id);

CREATE TABLE accounts (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    name           VARCHAR(160)   NOT NULL,
    industry       VARCHAR(40)    NOT NULL,
    tier           VARCHAR(20)    NOT NULL,
    annual_revenue DECIMAL(15, 2) NULL,
    website        VARCHAR(255)   NULL,
    status         VARCHAR(20)    NOT NULL,
    owner_user_id  BIGINT         NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    created_by     VARCHAR(180)   NULL,
    updated_by     VARCHAR(180)   NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Accounts list default sort is by name; a prefix search (`name LIKE 'acme%'`) can use this.
CREATE INDEX idx_accounts_name ON accounts (name);
-- "My accounts" board: filter by owner, then by status. Owner first because it is the more
-- selective predicate (hundreds of owners vs three statuses).
CREATE INDEX idx_accounts_owner_status ON accounts (owner_user_id, status);
-- Segment reporting: tier buckets ordered by revenue, satisfied without touching the heap.
CREATE INDEX idx_accounts_tier_revenue ON accounts (tier, annual_revenue DESC);

CREATE TABLE customers (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    account_id      BIGINT       NOT NULL,
    first_name      VARCHAR(80)  NOT NULL,
    last_name       VARCHAR(80)  NOT NULL,
    email           VARCHAR(180) NOT NULL,
    phone           VARCHAR(32)  NULL,
    job_title       VARCHAR(120) NULL,
    lifecycle_stage VARCHAR(20)  NOT NULL,
    owner_user_id   BIGINT       NOT NULL,
    tags            VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      VARCHAR(180) NULL,
    updated_by      VARCHAR(180) NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT fk_customers_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_customers_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Hottest query in the product: the contacts tab of an account, funnel-filtered.
-- account_id is the leading column so the same index also serves the unfiltered tab.
CREATE INDEX idx_customers_account_stage ON customers (account_id, lifecycle_stage);
-- Ownership checks and the "my contacts" view; also keeps the SALES_REP scope filter index-only.
CREATE INDEX idx_customers_owner_stage ON customers (owner_user_id, lifecycle_stage);
-- Contact de-duplication on import and the exact-match arm of the global search box.
CREATE INDEX idx_customers_email ON customers (email);
-- Recently added contacts widget on the dashboard (created_at range scan).
CREATE INDEX idx_customers_created_at ON customers (created_at);

CREATE TABLE interactions (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    customer_id        BIGINT        NOT NULL,
    type               VARCHAR(20)   NOT NULL,
    subject            VARCHAR(180)  NOT NULL,
    body               VARCHAR(4000) NULL,
    occurred_at        DATETIME(6)   NOT NULL,
    created_by_user_id BIGINT        NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    created_by         VARCHAR(180)  NULL,
    updated_by         VARCHAR(180)  NULL,
    CONSTRAINT pk_interactions PRIMARY KEY (id),
    CONSTRAINT fk_interactions_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT fk_interactions_author FOREIGN KEY (created_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Timeline render: newest-first history for one contact. The DESC key order lets MySQL 8
-- walk the index backwards-free, so the paged timeline needs no filesort.
CREATE INDEX idx_interactions_customer_occurred ON interactions (customer_id, occurred_at DESC);
-- Dashboard "activity in the last 30 days" aggregate: a pure range scan over one column.
CREATE INDEX idx_interactions_occurred_at ON interactions (occurred_at);

CREATE TABLE opportunities (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    account_id          BIGINT         NOT NULL,
    name                VARCHAR(160)   NOT NULL,
    amount              DECIMAL(15, 2) NOT NULL,
    stage               VARCHAR(20)    NOT NULL,
    probability         INT            NOT NULL,
    expected_close_date DATE           NULL,
    owner_user_id       BIGINT         NOT NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(180)   NULL,
    updated_by          VARCHAR(180)   NULL,
    CONSTRAINT pk_opportunities PRIMARY KEY (id),
    CONSTRAINT fk_opportunities_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_opportunities_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT ck_opportunities_probability CHECK (probability BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Kanban board and the forecast-by-close-date report read through this pair.
CREATE INDEX idx_opportunities_stage_close ON opportunities (stage, expected_close_date);
-- Account detail page: all deals for one company, biggest first.
CREATE INDEX idx_opportunities_account_amount ON opportunities (account_id, amount DESC);
-- Rep quota view.
CREATE INDEX idx_opportunities_owner_stage ON opportunities (owner_user_id, stage);

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    BIT(1)      NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Bulk revoke on logout-everywhere and password change.
CREATE INDEX idx_refresh_tokens_user_revoked ON refresh_tokens (user_id, revoked);
-- Nightly sweep of expired rows.
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
