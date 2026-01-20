CREATE TABLE TransactionToken (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL REFERENCES CategorizedTransaction(id) ON DELETE CASCADE,
    token TEXT NOT NULL
);

CREATE INDEX idx_transaction_token_token ON TransactionToken(token);

CREATE INDEX idx_transaction_token_transaction_id ON TransactionToken(transaction_id);
