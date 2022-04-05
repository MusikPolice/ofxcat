/*
 * Create a default UNKNOWN category that we can use to ensure that transactions are never automatically categorized
 * as UNKNOWN
 */
INSERT INTO Category (id, name) VALUES (0, 'UNKNOWN');

/*
 * If there exists an UNKNOWN category, migrate all transactions associated with it over to the new one
 */
UPDATE CategorizedTransaction SET category_id = 0
    WHERE category_id = (SELECT id FROM Category WHERE name = 'UNKNOWN' AND id <> 0);

/*
 * Clean up the old UNKNOWN category
 */
DELETE FROM Category WHERE name = 'UNKNOWN' AND id <> 0;