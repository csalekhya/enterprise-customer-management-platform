-- =====================================================================================
-- V2: reference data + a small demo dataset
--
-- Roles are reference data and must exist before the first login can resolve authorities.
-- The four demo users let a reviewer exercise every branch of the authorization matrix
-- without creating accounts by hand. Password hashes are BCrypt (cost 10).
--
--   admin@slatecrm.io     / Admin@12345      ADMIN
--   manager@slatecrm.io   / Manager@12345    SALES_MANAGER
--   rep@slatecrm.io       / Rep@12345        SALES_REP
--   viewer@slatecrm.io    / ReadOnly@12345   READ_ONLY
--
-- Demo rows are intentionally modest (5 accounts / 6 contacts / 6 deals): enough to make
-- every chart, filter and board render meaningfully, small enough to stay readable.
-- =====================================================================================

INSERT INTO roles (name) VALUES ('ADMIN'), ('SALES_MANAGER'), ('SALES_REP'), ('READ_ONLY');

INSERT INTO users (email, password_hash, first_name, last_name, enabled, created_at, updated_at, created_by, updated_by)
VALUES ('admin@slatecrm.io', '$2y$10$OkkQ/KcEi.No1Y2TNJYjF.dn4ox9gkivpZyD7nwnD46fSxC/sBaUO', 'Avery', 'Lindqvist', 1, NOW(6), NOW(6), 'system', 'system'),
       ('manager@slatecrm.io', '$2y$10$7NegCKtN/HHQy5tUJ4bbUuqP.d1Q2H.GhLgZw.59w8VZJYX4Whlc2', 'Dana', 'Whitfield', 1, NOW(6), NOW(6), 'system', 'system'),
       ('rep@slatecrm.io', '$2y$10$mmYXn9181RLSVBKQy9DfCuFNtjQLGOMVIyJ7Nkr6cdKYNLVDMtRgu', 'Marcus', 'Ito', 1, NOW(6), NOW(6), 'system', 'system'),
       ('viewer@slatecrm.io', '$2y$10$vtdFLAJs2yCjo9UnPVW4y.uI2NZH1ePVHCVgT1Ex3WvVEWnQMY7Ju', 'Priya', 'Raman', 1, NOW(6), NOW(6), 'system', 'system');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON (u.email = 'admin@slatecrm.io' AND r.name = 'ADMIN')
    OR (u.email = 'manager@slatecrm.io' AND r.name = 'SALES_MANAGER')
    OR (u.email = 'rep@slatecrm.io' AND r.name = 'SALES_REP')
    OR (u.email = 'viewer@slatecrm.io' AND r.name = 'READ_ONLY');

-- ------------------------------------------------------------------------------------
-- Demo accounts
-- ------------------------------------------------------------------------------------
INSERT INTO accounts (name, industry, tier, annual_revenue, website, status, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT 'Northwind Logistics', 'LOGISTICS', 'PLATINUM', 48200000.00, 'https://northwind-logistics.example.com', 'ACTIVE', u.id, NOW(6), NOW(6), 'system', 'system'
FROM users u WHERE u.email = 'manager@slatecrm.io';

INSERT INTO accounts (name, industry, tier, annual_revenue, website, status, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT 'Helix Biosciences', 'HEALTHCARE', 'GOLD', 21750000.00, 'https://helixbio.example.com', 'ACTIVE', u.id, NOW(6), NOW(6), 'system', 'system'
FROM users u WHERE u.email = 'rep@slatecrm.io';

INSERT INTO accounts (name, industry, tier, annual_revenue, website, status, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT 'Cobalt Financial Group', 'FINANCIAL_SERVICES', 'PLATINUM', 96400000.00, 'https://cobaltfg.example.com', 'ACTIVE', u.id, NOW(6), NOW(6), 'system', 'system'
FROM users u WHERE u.email = 'manager@slatecrm.io';

INSERT INTO accounts (name, industry, tier, annual_revenue, website, status, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT 'Junction Retail Co', 'RETAIL', 'SILVER', 7300000.00, 'https://junctionretail.example.com', 'PROSPECT', u.id, NOW(6), NOW(6), 'system', 'system'
FROM users u WHERE u.email = 'rep@slatecrm.io';

INSERT INTO accounts (name, industry, tier, annual_revenue, website, status, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT 'Atlas Manufacturing', 'MANUFACTURING', 'BRONZE', 3150000.00, 'https://atlasmfg.example.com', 'CHURNED', u.id, NOW(6), NOW(6), 'system', 'system'
FROM users u WHERE u.email = 'admin@slatecrm.io';

-- ------------------------------------------------------------------------------------
-- Demo contacts
-- ------------------------------------------------------------------------------------
INSERT INTO customers (account_id, first_name, last_name, email, phone, job_title, lifecycle_stage, owner_user_id, tags, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Elena', 'Fischer', 'elena.fischer@northwind-logistics.example.com', '+1-716-555-0142', 'VP Operations', 'CHAMPION', a.owner_user_id, 'decision-maker,renewal', NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Northwind Logistics';

INSERT INTO customers (account_id, first_name, last_name, email, phone, job_title, lifecycle_stage, owner_user_id, tags, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Tobias', 'Grant', 'tobias.grant@northwind-logistics.example.com', '+1-716-555-0188', 'Director of IT', 'CUSTOMER', a.owner_user_id, 'technical', NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Northwind Logistics';

INSERT INTO customers (account_id, first_name, last_name, email, phone, job_title, lifecycle_stage, owner_user_id, tags, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Aisha', 'Bello', 'aisha.bello@helixbio.example.com', '+1-617-555-0110', 'Head of Clinical Data', 'QUALIFIED', a.owner_user_id, 'compliance', NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Helix Biosciences';

INSERT INTO customers (account_id, first_name, last_name, email, phone, job_title, lifecycle_stage, owner_user_id, tags, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Victor', 'Moreau', 'victor.moreau@cobaltfg.example.com', '+1-212-555-0175', 'Chief Revenue Officer', 'CUSTOMER', a.owner_user_id, 'executive,expansion', NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Cobalt Financial Group';

INSERT INTO customers (account_id, first_name, last_name, email, phone, job_title, lifecycle_stage, owner_user_id, tags, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Naomi', 'Clarke', 'naomi.clarke@junctionretail.example.com', '+1-312-555-0133', 'Merchandising Lead', 'LEAD', a.owner_user_id, 'inbound', NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Junction Retail Co';

INSERT INTO customers (account_id, first_name, last_name, email, phone, job_title, lifecycle_stage, owner_user_id, tags, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Owen', 'Hartley', 'owen.hartley@atlasmfg.example.com', '+1-419-555-0190', 'Plant Manager', 'LEAD', a.owner_user_id, 'win-back', NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Atlas Manufacturing';

-- ------------------------------------------------------------------------------------
-- Demo pipeline
-- ------------------------------------------------------------------------------------
INSERT INTO opportunities (account_id, name, amount, stage, probability, expected_close_date, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Fleet telematics rollout', 480000.00, 'NEGOTIATION', 75, DATE_ADD(CURDATE(), INTERVAL 24 DAY), a.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Northwind Logistics';

INSERT INTO opportunities (account_id, name, amount, stage, probability, expected_close_date, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Warehouse analytics add-on', 125000.00, 'PROPOSAL', 50, DATE_ADD(CURDATE(), INTERVAL 45 DAY), a.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Northwind Logistics';

INSERT INTO opportunities (account_id, name, amount, stage, probability, expected_close_date, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Clinical trial data platform', 310000.00, 'DISCOVERY', 20, DATE_ADD(CURDATE(), INTERVAL 80 DAY), a.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Helix Biosciences';

INSERT INTO opportunities (account_id, name, amount, stage, probability, expected_close_date, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Enterprise licence renewal', 920000.00, 'WON', 100, DATE_SUB(CURDATE(), INTERVAL 12 DAY), a.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Cobalt Financial Group';

INSERT INTO opportunities (account_id, name, amount, stage, probability, expected_close_date, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Advisor portal integration', 265000.00, 'PROPOSAL', 45, DATE_ADD(CURDATE(), INTERVAL 33 DAY), a.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Cobalt Financial Group';

INSERT INTO opportunities (account_id, name, amount, stage, probability, expected_close_date, owner_user_id, created_at, updated_at, created_by, updated_by)
SELECT a.id, 'Store ops pilot', 48000.00, 'LOST', 0, DATE_SUB(CURDATE(), INTERVAL 30 DAY), a.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM accounts a WHERE a.name = 'Junction Retail Co';

-- ------------------------------------------------------------------------------------
-- Demo interactions (recent, so the 30-day activity KPI is non-zero on first boot)
-- ------------------------------------------------------------------------------------
INSERT INTO interactions (customer_id, type, subject, body, occurred_at, created_by_user_id, created_at, updated_at, created_by, updated_by)
SELECT c.id, 'MEETING', 'Quarterly business review', 'Reviewed SLA attainment and agreed to scope the telematics rollout for next quarter.', DATE_SUB(NOW(6), INTERVAL 3 DAY), c.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM customers c WHERE c.email = 'elena.fischer@northwind-logistics.example.com';

INSERT INTO interactions (customer_id, type, subject, body, occurred_at, created_by_user_id, created_at, updated_at, created_by, updated_by)
SELECT c.id, 'CALL', 'Security questionnaire walkthrough', 'Walked through SSO and audit-log requirements; sent the completed questionnaire.', DATE_SUB(NOW(6), INTERVAL 6 DAY), c.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM customers c WHERE c.email = 'tobias.grant@northwind-logistics.example.com';

INSERT INTO interactions (customer_id, type, subject, body, occurred_at, created_by_user_id, created_at, updated_at, created_by, updated_by)
SELECT c.id, 'EMAIL', 'Follow-up: validation plan', 'Shared the 21 CFR Part 11 validation outline and a sandbox invite.', DATE_SUB(NOW(6), INTERVAL 9 DAY), c.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM customers c WHERE c.email = 'aisha.bello@helixbio.example.com';

INSERT INTO interactions (customer_id, type, subject, body, occurred_at, created_by_user_id, created_at, updated_at, created_by, updated_by)
SELECT c.id, 'NOTE', 'Expansion signal', 'Mentioned a new advisor portal initiative funded for next fiscal year.', DATE_SUB(NOW(6), INTERVAL 2 DAY), c.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM customers c WHERE c.email = 'victor.moreau@cobaltfg.example.com';

INSERT INTO interactions (customer_id, type, subject, body, occurred_at, created_by_user_id, created_at, updated_at, created_by, updated_by)
SELECT c.id, 'CALL', 'Discovery call', 'Inbound from the pricing page. Team of 40 stores, evaluating three vendors.', DATE_SUB(NOW(6), INTERVAL 14 DAY), c.owner_user_id, NOW(6), NOW(6), 'system', 'system'
FROM customers c WHERE c.email = 'naomi.clarke@junctionretail.example.com';
