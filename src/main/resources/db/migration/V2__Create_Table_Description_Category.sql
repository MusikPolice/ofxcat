CREATE TABLE DescriptionCategory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    description TEXT NOT NULL,
    category_id INTEGER REFERENCES Category (id) ON DELETE CASCADE
);