/* Setting default value to Unknown b/c we're adding this column late.
 * Normally this would be bad practice, but I'm the only one using this software right now, so it's ok
 */
ALTER TABLE "CategorizedTransaction" ADD fitId TEXT NOT NULL DEFAULT 'Unknown';