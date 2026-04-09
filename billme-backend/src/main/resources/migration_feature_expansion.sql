-- BillMeX Zero-Regression Feature Expansion: Schema Updates & Backfill

-- 1. Updates to merchant_profiles
-- If columns were auto-added by JPA, this will ensure they have the proper default values.
UPDATE merchant_profiles SET allow_negative_stock = 1 WHERE allow_negative_stock IS NULL;

-- 2. Updates to products
UPDATE products SET stock_quantity = 0 WHERE stock_quantity IS NULL;
UPDATE products SET cost_price = 0.00 WHERE cost_price IS NULL;
UPDATE products SET low_stock_threshold = 5 WHERE low_stock_threshold IS NULL;

-- 3. Updates to invoices & invoice_items
-- legacy cogs remain NULL to identify them as 'UNKNOWN' in P&L reporting.
-- selected_bank_account_id remains NULL to fallback to legacy MerchantBankDetails config.

-- 4. Initial Bank Backfill (Optional)
-- This query intelligently clones details from the legacy table into the new one.
INSERT INTO merchant_bank_accounts (merchant_id, bank_name, account_holder_name, account_number, ifsc, is_default, is_verified, created_at)
SELECT merchant_profile_id, bank_name, account_holder_name, account_number, ifsc, 1, 1, NOW()
FROM merchant_bank_details
WHERE id NOT IN (SELECT merchant_id FROM merchant_bank_accounts);

-- ============================================================
-- 5. WITHDRAWAL BANK SELECTION — Safe Schema Migration
--    Adds bank_account_id and bank_name to transactions table.
--    Both columns are nullable → zero impact on existing rows.
--    Use IF NOT EXISTS guard for idempotent safe re-run.
-- ============================================================

-- MySQL / MariaDB (Railway uses MySQL):
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS bank_account_id BIGINT NULL COMMENT 'FK reference to merchant_bank_accounts.id — set only on WITHDRAWAL transactions',
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(255) NULL COMMENT 'Denormalized bank name snapshot at time of withdrawal — preserved even if bank account is deleted';

-- Backfill: existing WITHDRAWAL transactions get NULL values (no data loss, no regression)
-- No UPDATE needed — NULL is the correct state for pre-existing withdrawals (legacy behavior).

-- Index for fast withdrawal history lookup by bank account
CREATE INDEX IF NOT EXISTS idx_transactions_bank_account_id
    ON transactions (bank_account_id);

-- ============================================================
-- 7. REFUND REASON + STATUS TRACKING (Persistent audit fields)
-- ============================================================
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS refund_reason TEXT NULL,
    ADD COLUMN IF NOT EXISTS refund_category VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS refund_status ENUM('REQUESTED','APPROVED','REJECTED') NULL,
    ADD COLUMN IF NOT EXISTS refund_requested_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS refund_processed_at TIMESTAMP NULL;

-- ============================================================
-- 6. BANK ACCOUNT UNIFICATION — Backfill profile-only banks
--    Copies bank data from merchant_profiles into
--    merchant_bank_accounts for any merchant who completed
--    their profile but never used the Bank Accounts UI.
--
--    Safety guards:
--    • NULL / blank check   → skip incomplete profile banks
--    • NOT EXISTS duplicate → skip if accountNumber already
--                             present in merchant_bank_accounts
--    • is_default = 1       → profile bank was their only bank
--    • is_verified = 1      → merchant entered it directly
--    • Idempotent           → safe to re-run at any time
-- ============================================================

INSERT INTO merchant_bank_accounts
    (merchant_id, bank_name, account_holder_name, account_number, ifsc, is_default, is_verified, created_at)
SELECT
    mp.id,
    mp.bank_name,
    COALESCE(NULLIF(mp.account_holder_name, ''), mp.owner_name, 'Account Holder'),
    mp.account_number,
    mp.ifsc_code,
    1,      -- is_default: profile bank was their only bank
    1,      -- is_verified: merchant entered it deliberately
    NOW()
FROM merchant_profiles mp
WHERE
    mp.account_number IS NOT NULL AND TRIM(mp.account_number) != ''
    AND mp.bank_name   IS NOT NULL AND TRIM(mp.bank_name)    != ''
    AND mp.ifsc_code   IS NOT NULL AND TRIM(mp.ifsc_code)    != ''
    AND NOT EXISTS (
        SELECT 1
        FROM merchant_bank_accounts mba
        WHERE mba.merchant_id    = mp.id
          AND mba.account_number = mp.account_number
    );
