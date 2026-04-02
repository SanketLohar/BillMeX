-- ============================================================
-- BillMeX Transaction Backfill Migration (Final Safety Version)
-- ============================================================
-- Purpose: Restore data integrity by linking existing transactions 
-- to merchant and customer wallets via the invoice relationship.
-- 🛡️ Deterministic: Only updates NULL wallet relationships.
-- ============================================================

-- 1. Backfill Merchant (Receiver) Wallets for ALL transactions linked to an invoice
-- Links transaction to the wallet of the user identified as the merchant on the linked invoice.
UPDATE transactions t
JOIN invoices i ON t.invoice_id = i.id
JOIN merchant_profiles mp ON i.merchant_id = mp.id
JOIN wallets w ON w.user_id = mp.user_id
SET t.receiver_wallet_id = w.id
WHERE t.receiver_wallet_id IS NULL;

-- 2. Backfill Customer (Sender) Wallets for FACE_PAY transactions (Internal transfers)
-- Links transaction to the wallet of the user identified as the customer on the linked invoice.
UPDATE transactions t
JOIN invoices i ON t.invoice_id = i.id
JOIN customer_profiles cp ON i.customer_id = cp.id
JOIN wallets w ON w.user_id = cp.user_id
SET t.sender_wallet_id = w.id
WHERE t.sender_wallet_id IS NULL 
  AND t.transaction_type = 'FACE_PAY';

-- 3. (Audit) Verification queries
-- Check for remaining orphaned transactions with an invoice but no receiver wallet:
-- SELECT COUNT(*) FROM transactions WHERE receiver_wallet_id IS NULL AND invoice_id IS NOT NULL;
