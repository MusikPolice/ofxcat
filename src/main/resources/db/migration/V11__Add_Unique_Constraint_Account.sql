-- Add unique constraint on (bank_number, account_number) to prevent duplicate accounts
-- Account numbers are unique within a bank, so we need both columns for uniqueness
CREATE UNIQUE INDEX idx_account_bank_account_unique ON Account(bank_number, account_number);
