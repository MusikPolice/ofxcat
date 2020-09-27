CREATE TABLE DescriptionCategory (
    description TEXT NOT NULL,
    category_id INTEGER REFERENCES Category (id) ON DELETE CASCADE
);