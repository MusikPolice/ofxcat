CREATE TABLE "Transaction" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT,
    "date" DATE NOT NULL,
    amount DOUBLE NOT NULL,
    description TEXT NOT NULL,
    account_id INTEGER REFERENCES Account (id) ON DELETE CASCADE,
    category_id INTEGER REFERENCES Category (id) ON DELETE CASCADE
);
