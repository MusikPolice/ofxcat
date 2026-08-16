-- Clear all stored tokens so they are re-generated on next startup using the current
-- TokenNormalizer stop word list. This removes stale tokens (e.g. "payment", "bill")
-- that were stored before those words were added to the stop word list, which caused
-- incorrect bridges between unrelated vendors during token-overlap clustering.
DELETE FROM TransactionToken;
