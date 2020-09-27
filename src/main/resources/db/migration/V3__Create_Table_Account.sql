CREATE TABLE Account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bank_number TEXT,
    account_number TEXT NOT NULL,
    account_type TEXT,
    name TEXT NOT NULL
);