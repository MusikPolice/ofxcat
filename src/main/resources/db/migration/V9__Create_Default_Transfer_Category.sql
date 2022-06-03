/*
 * Create a default TRANSFER category that we can use to categorize transactions representing a transfer of funds from
 * one account to another
 */
INSERT INTO Category (id, name) VALUES (1, 'TRANSFER');

/*
 * If there exists a TRANSFER category, migrate all transactions associated with it over to the new one
 */
UPDATE CategorizedTransaction SET category_id = 1
    WHERE category_id = (SELECT id FROM Category WHERE name = 'TRANSFER' AND id <> 1);

/*
 * Clean up the old TRANSFER category
 */
DELETE FROM Category WHERE name = 'TRANSFER' AND id <> 1;