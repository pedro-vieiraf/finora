-- Hibernate's ddl-auto=update does not alter existing check constraints when
-- an enum gains a new value. Keep the account status constraint in sync with
-- AccountStatus here. The statements are idempotent and safe to run on every
-- startup, including against a freshly created schema.
ALTER TABLE IF EXISTS accounts
  DROP CONSTRAINT IF EXISTS accounts_status_check;

ALTER TABLE IF EXISTS accounts
  ADD CONSTRAINT accounts_status_check
  CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'));

ALTER TABLE IF EXISTS accounts
  ADD COLUMN IF NOT EXISTS source VARCHAR(255) NOT NULL DEFAULT 'MANUAL';

-- Recurrence support. Hibernate creates the tables and columns from the
-- entities; these idempotent statements keep existing databases in sync and
-- add the indexes used by the scheduler and by idempotency checks.
ALTER TABLE IF EXISTS transactions
  ADD COLUMN IF NOT EXISTS recurrence_id UUID;

ALTER TABLE IF EXISTS transactions
  ADD COLUMN IF NOT EXISTS occurrence_date DATE;

ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS custom_description VARCHAR(160);

CREATE UNIQUE INDEX IF NOT EXISTS uk_transactions_recurrence_occurrence
  ON transactions (recurrence_id, occurrence_date);

CREATE INDEX IF NOT EXISTS idx_recurrences_due
  ON recurrences (status, next_occurrence);

-- Credit card support. Hibernate creates the tables and columns from the
-- entities; these idempotent statements keep check constraints and indexes in
-- sync with the domain rules.
ALTER TABLE IF EXISTS credit_cards
  DROP CONSTRAINT IF EXISTS credit_cards_status_check;

ALTER TABLE IF EXISTS credit_cards
  ADD CONSTRAINT credit_cards_status_check
  CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'));

ALTER TABLE IF EXISTS credit_cards
  DROP CONSTRAINT IF EXISTS credit_cards_closing_day_check;

ALTER TABLE IF EXISTS credit_cards
  ADD CONSTRAINT credit_cards_closing_day_check
  CHECK (closing_day BETWEEN 1 AND 31);

ALTER TABLE IF EXISTS credit_cards
  DROP CONSTRAINT IF EXISTS credit_cards_due_day_check;

ALTER TABLE IF EXISTS credit_cards
  ADD CONSTRAINT credit_cards_due_day_check
  CHECK (due_day BETWEEN 1 AND 31);

ALTER TABLE IF EXISTS credit_cards
  DROP CONSTRAINT IF EXISTS credit_cards_limit_check;

ALTER TABLE IF EXISTS credit_cards
  ADD CONSTRAINT credit_cards_limit_check
  CHECK (credit_limit > 0);

ALTER TABLE IF EXISTS credit_card_invoices
  DROP CONSTRAINT IF EXISTS credit_card_invoices_status_check;

ALTER TABLE IF EXISTS credit_card_invoices
  ADD CONSTRAINT credit_card_invoices_status_check
  CHECK (status IN ('OPEN', 'CLOSED', 'PARTIALLY_PAID', 'PAID'));

ALTER TABLE IF EXISTS credit_card_invoices
  DROP CONSTRAINT IF EXISTS credit_card_invoices_amounts_check;

ALTER TABLE IF EXISTS credit_card_invoices
  ADD CONSTRAINT credit_card_invoices_amounts_check
  CHECK (
    total_amount >= 0
    AND paid_amount >= 0
    AND paid_amount <= total_amount
  );

ALTER TABLE IF EXISTS credit_card_invoices
  DROP CONSTRAINT IF EXISTS credit_card_invoices_dates_check;

ALTER TABLE IF EXISTS credit_card_invoices
  ADD CONSTRAINT credit_card_invoices_dates_check
  CHECK (
    cycle_start <= cycle_end
    AND cycle_end = closing_date
    AND due_date > closing_date
  );

ALTER TABLE IF EXISTS credit_card_purchases
  DROP CONSTRAINT IF EXISTS credit_card_purchases_status_check;

ALTER TABLE IF EXISTS credit_card_purchases
  ADD CONSTRAINT credit_card_purchases_status_check
  CHECK (status IN ('ACTIVE', 'CANCELLED'));

ALTER TABLE IF EXISTS credit_card_purchases
  DROP CONSTRAINT IF EXISTS credit_card_purchases_amount_check;

ALTER TABLE IF EXISTS credit_card_purchases
  ADD CONSTRAINT credit_card_purchases_amount_check
  CHECK (total_amount > 0 AND installment_count > 0);

ALTER TABLE IF EXISTS credit_card_installments
  DROP CONSTRAINT IF EXISTS credit_card_installments_amount_check;

ALTER TABLE IF EXISTS credit_card_installments
  ADD CONSTRAINT credit_card_installments_amount_check
  CHECK (amount > 0 AND installment_number > 0);

ALTER TABLE IF EXISTS credit_card_invoice_payments
  DROP CONSTRAINT IF EXISTS credit_card_invoice_payments_amount_check;

ALTER TABLE IF EXISTS credit_card_invoice_payments
  ADD CONSTRAINT credit_card_invoice_payments_amount_check
  CHECK (amount > 0);

CREATE UNIQUE INDEX IF NOT EXISTS uk_credit_card_invoices_card_closing
  ON credit_card_invoices (card_id, closing_date);

CREATE INDEX IF NOT EXISTS idx_credit_card_purchases_card_date
  ON credit_card_purchases (card_id, purchase_date);

CREATE INDEX IF NOT EXISTS idx_credit_card_installments_invoice
  ON credit_card_installments (invoice_id);

CREATE INDEX IF NOT EXISTS idx_credit_card_invoice_payments_invoice
  ON credit_card_invoice_payments (invoice_id, paid_at);

-- Initial Open Finance imports and the provider identifiers used for reconciliation.
CREATE TABLE IF NOT EXISTS open_finance_account_links (
  account_id UUID PRIMARY KEY REFERENCES accounts (id),
  item_id UUID NOT NULL,
  provider_account_id UUID NOT NULL UNIQUE,
  cutoff_date DATE NOT NULL
);

ALTER TABLE open_finance_account_links
  ADD COLUMN IF NOT EXISTS last_sync_started_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS open_finance_transaction_links (
  transaction_id UUID NOT NULL REFERENCES transactions (id),
  account_id UUID NOT NULL REFERENCES open_finance_account_links (account_id),
  provider_transaction_id TEXT NOT NULL,
  UNIQUE (account_id, provider_transaction_id)
);

ALTER TABLE open_finance_transaction_links
  DROP CONSTRAINT IF EXISTS open_finance_transaction_links_pkey;
