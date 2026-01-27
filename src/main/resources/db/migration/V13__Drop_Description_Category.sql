-- Drop the DescriptionCategory table as it is no longer used.
-- Categorization now relies on CategorizedTransaction for exact matching
-- and TransactionToken for token-based matching.

DROP TABLE IF EXISTS DescriptionCategory;
