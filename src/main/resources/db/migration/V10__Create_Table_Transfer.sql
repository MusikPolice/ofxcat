CREATE TABLE "Transfer" (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER REFERENCES CategorizedTransaction (id) ON DELETE CASCADE,
    sink_id INTEGER REFERENCES CategorizedTransaction (id) ON DELETE CASCADE
);
