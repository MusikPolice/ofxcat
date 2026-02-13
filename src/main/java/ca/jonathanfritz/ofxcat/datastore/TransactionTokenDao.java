package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAO for managing normalized tokens associated with categorized transactions.
 * Tokens are used for matching similar transactions during categorization.
 */
public class TransactionTokenDao {

    private static final Logger logger = LogManager.getLogger(TransactionTokenDao.class);

    /**
     * Inserts tokens for a transaction.
     *
     * @param t the database transaction to participate in
     * @param transactionId the ID of the CategorizedTransaction
     * @param tokens the set of normalized tokens to store
     */
    public void insertTokens(DatabaseTransaction t, long transactionId, Set<String> tokens) throws SQLException {
        if (tokens == null || tokens.isEmpty()) {
            logger.debug("No tokens to insert for transaction {}", transactionId);
            return;
        }

        logger.debug("Inserting {} tokens for transaction {}", tokens.size(), transactionId);
        final String insertStatement = "INSERT INTO TransactionToken (transaction_id, token) VALUES (?, ?);";

        for (String token : tokens) {
            t.execute(insertStatement, ps -> {
                ps.setLong(1, transactionId);
                ps.setString(2, token);
            });
        }
    }

    /**
     * Deletes all tokens for a transaction.
     *
     * @param t the database transaction to participate in
     * @param transactionId the ID of the CategorizedTransaction
     */
    public void deleteTokens(DatabaseTransaction t, long transactionId) throws SQLException {
        logger.debug("Deleting tokens for transaction {}", transactionId);
        final String deleteStatement = "DELETE FROM TransactionToken WHERE transaction_id = ?;";

        t.execute(deleteStatement, ps -> ps.setLong(1, transactionId));
    }

    /**
     * Retrieves all tokens for a transaction.
     *
     * @param t the database transaction to participate in
     * @param transactionId the ID of the CategorizedTransaction
     * @return the set of tokens for this transaction
     */
    public Set<String> getTokens(DatabaseTransaction t, long transactionId) throws SQLException {
        logger.debug("Retrieving tokens for transaction {}", transactionId);
        final String selectStatement = "SELECT token FROM TransactionToken WHERE transaction_id = ?;";

        return t.queryRaw(selectStatement,
                ps -> ps.setLong(1, transactionId),
                rs -> {
                    Set<String> tokens = new HashSet<>();
                    while (rs.next()) {
                        tokens.add(rs.getString("token"));
                    }
                    return tokens;
                });
    }

    /**
     * Checks if a transaction has any tokens stored.
     *
     * @param t the database transaction to participate in
     * @param transactionId the ID of the CategorizedTransaction
     * @return true if the transaction has tokens, false otherwise
     */
    public boolean hasTokens(DatabaseTransaction t, long transactionId) throws SQLException {
        logger.debug("Checking if transaction {} has tokens", transactionId);
        final String selectStatement = "SELECT COUNT(*) as count FROM TransactionToken WHERE transaction_id = ?;";

        return t.queryRaw(selectStatement,
                ps -> ps.setLong(1, transactionId),
                rs -> rs.next() && rs.getInt("count") > 0);
    }

    /**
     * Returns the number of tokens for a transaction.
     *
     * @param t the database transaction to participate in
     * @param transactionId the ID of the CategorizedTransaction
     * @return the count of tokens
     */
    public int getTokenCount(DatabaseTransaction t, long transactionId) throws SQLException {
        logger.debug("Getting token count for transaction {}", transactionId);
        final String selectStatement = "SELECT COUNT(*) as count FROM TransactionToken WHERE transaction_id = ?;";

        return t.queryRaw(selectStatement,
                ps -> ps.setLong(1, transactionId),
                rs -> rs.next() ? rs.getInt("count") : 0);
    }

    /**
     * Finds transactions that have matching tokens.
     * Excludes transactions in the UNKNOWN category.
     *
     * @param t the database transaction to participate in
     * @param tokens the set of tokens to match against
     * @return list of match results with transaction ID, category ID, matching token count, and total token count
     */
    public List<TokenMatchResult> findTransactionsWithMatchingTokens(DatabaseTransaction t, Set<String> tokens) throws SQLException {
        if (tokens == null || tokens.isEmpty()) {
            logger.debug("No tokens to search for");
            return Collections.emptyList();
        }

        logger.debug("Finding transactions matching tokens: {}", tokens);

        // Build the IN clause dynamically
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
        }

        // Use a CTE to compute total token counts, avoiding N+1 queries
        final String selectStatement = """
            WITH TokenCounts AS (
                SELECT transaction_id, COUNT(*) as total_tokens
                FROM TransactionToken
                GROUP BY transaction_id
            )
            SELECT
                ct.id as transaction_id,
                ct.category_id,
                COUNT(DISTINCT tt.token) as matching_tokens,
                tc.total_tokens
            FROM TransactionToken tt
            JOIN CategorizedTransaction ct ON tt.transaction_id = ct.id
            JOIN TokenCounts tc ON tc.transaction_id = ct.id
            WHERE tt.token IN (%s)
              AND ct.category_id != ?
            GROUP BY ct.id, ct.category_id, tc.total_tokens
            ORDER BY matching_tokens DESC
            """.formatted(placeholders);

        // Convert tokens to list for indexed access
        List<String> tokenList = new ArrayList<>(tokens);

        return t.queryRaw(selectStatement,
                ps -> {
                    int paramIndex = 1;
                    for (String token : tokenList) {
                        ps.setString(paramIndex++, token);
                    }
                    ps.setLong(paramIndex, Category.UNKNOWN.getId());
                },
                rs -> {
                    List<TokenMatchResult> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(new TokenMatchResult(
                                rs.getLong("transaction_id"),
                                rs.getLong("category_id"),
                                rs.getInt("matching_tokens"),
                                rs.getInt("total_tokens")
                        ));
                    }
                    return results;
                });
    }

    /**
     * Deletes all tokens from all transactions.
     * Used for re-migration when keyword rules are updated.
     *
     * @param t the database transaction to participate in
     */
    public void deleteAllTokens(DatabaseTransaction t) throws SQLException {
        logger.debug("Deleting all tokens from TransactionToken table");
        final String deleteStatement = "DELETE FROM TransactionToken;";
        t.execute(deleteStatement, ps -> {});
    }

    /**
     * Result of a token matching query.
     *
     * @param transactionId the ID of the matching transaction
     * @param categoryId the ID of the transaction's category
     * @param matchingTokenCount the number of tokens that matched
     * @param totalTokenCount the total number of tokens for this transaction
     */
    public record TokenMatchResult(long transactionId, long categoryId, int matchingTokenCount, int totalTokenCount) {}
}
