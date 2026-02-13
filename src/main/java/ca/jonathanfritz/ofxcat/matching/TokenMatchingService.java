package ca.jonathanfritz.ofxcat.matching;

import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for finding matching categories based on normalized tokens.
 * Uses SQL-based token lookup for efficient matching.
 */
public class TokenMatchingService {

    private final Connection connection;
    private final TransactionTokenDao transactionTokenDao;
    private final CategoryDao categoryDao;
    private final TokenNormalizer tokenNormalizer;
    private final TokenMatchingConfig config;

    private static final Logger logger = LogManager.getLogger(TokenMatchingService.class);

    @Inject
    public TokenMatchingService(
            Connection connection,
            TransactionTokenDao transactionTokenDao,
            CategoryDao categoryDao,
            TokenNormalizer tokenNormalizer,
            TokenMatchingConfig config
    ) {
        this.connection = connection;
        this.transactionTokenDao = transactionTokenDao;
        this.categoryDao = categoryDao;
        this.tokenNormalizer = tokenNormalizer;
        this.config = config;
    }

    /**
     * Finds categories that match the given description string.
     * The description is normalized to tokens before matching.
     *
     * @param description the transaction description to match
     * @return list of matching categories ranked by overlap ratio
     */
    public List<CategoryMatch> findMatchingCategoriesForDescription(String description) {
        Set<String> tokens = tokenNormalizer.normalize(description);
        return findMatchingCategories(tokens);
    }

    /**
     * Finds categories that match the given set of tokens.
     *
     * @param searchTokens the tokens to match against stored transactions
     * @return list of matching categories ranked by overlap ratio, aggregated by category
     */
    public List<CategoryMatch> findMatchingCategories(Set<String> searchTokens) {
        if (searchTokens == null || searchTokens.isEmpty()) {
            logger.debug("No tokens to search for, returning empty list");
            return Collections.emptyList();
        }

        logger.debug("Finding matching categories for tokens: {}", searchTokens);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Step 1: Find all transactions with matching tokens
            List<TransactionTokenDao.TokenMatchResult> matchResults =
                    transactionTokenDao.findTransactionsWithMatchingTokens(t, searchTokens);

            if (matchResults.isEmpty()) {
                logger.debug("No matching transactions found");
                return Collections.emptyList();
            }

            // Step 2: Calculate overlap ratio and aggregate by category in a single pass.
            // Only fetch each Category once, keeping the best overlap ratio for each.
            Map<Long, CategoryMatch> aggregated = new HashMap<>();
            for (TransactionTokenDao.TokenMatchResult result : matchResults) {
                double overlapRatio = calculateOverlapRatio(
                        result.matchingTokenCount(), searchTokens.size(), result.totalTokenCount()
                );

                if (overlapRatio < config.getOverlapThreshold()) {
                    continue;
                }

                long categoryId = result.categoryId();
                CategoryMatch existing = aggregated.get(categoryId);

                if (existing != null) {
                    // Already have this category - update only if new overlap ratio is better
                    if (overlapRatio > existing.overlapRatio()) {
                        aggregated.put(categoryId, new CategoryMatch(existing.category(), overlapRatio));
                    }
                } else {
                    // First time seeing this category - fetch it from DB
                    categoryDao.select(categoryId).ifPresent(category ->
                            aggregated.put(categoryId, new CategoryMatch(category, overlapRatio))
                    );
                }
            }

            // Step 3: Sort by overlap ratio descending
            return aggregated.values().stream()
                    .sorted(Comparator.comparingDouble(CategoryMatch::overlapRatio).reversed())
                    .collect(Collectors.toList());

        } catch (SQLException e) {
            logger.error("Failed to find matching categories", e);
            return Collections.emptyList();
        }
    }

    /**
     * Calculates the overlap ratio between search tokens and stored tokens.
     * The ratio is computed as: matchingTokens / min(searchTokens, storedTokens)
     *
     * This formula ensures that:
     * - Searching with a subset of stored tokens can still match (e.g., "shoppers drug" matches "shoppers drug mart")
     * - Searching with a superset of stored tokens can still match (e.g., "shoppers drug mart" matches "shoppers drug")
     *
     * @param matchingTokenCount the number of tokens that matched
     * @param searchTokenCount the number of tokens in the search
     * @param storedTokenCount the number of tokens stored for the transaction
     * @return the overlap ratio between 0.0 and 1.0
     */
    private double calculateOverlapRatio(int matchingTokenCount, int searchTokenCount, int storedTokenCount) {
        int minTokens = Math.min(searchTokenCount, storedTokenCount);
        if (minTokens == 0) {
            return 0.0;
        }
        return (double) matchingTokenCount / minTokens;
    }

    /**
     * Result of a category matching operation.
     *
     * @param category the matched category
     * @param overlapRatio the ratio of matching tokens (0.0 to 1.0)
     */
    public record CategoryMatch(Category category, double overlapRatio) {}
}
