package ca.jonathanfritz.ofxcat.matching;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenMatchingServiceTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;
    private final TokenNormalizer tokenNormalizer;
    private TokenMatchingService tokenMatchingService;

    private Account account;
    private Category restaurants;
    private Category groceries;

    TokenMatchingServiceTest() {
        categoryDao = injector.getInstance(CategoryDao.class);
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        transactionTokenDao = injector.getInstance(TransactionTokenDao.class);
        tokenNormalizer = new TokenNormalizer();
    }

    @BeforeEach
    void setUpTestData() {
        // Create default config and service
        TokenMatchingConfig config = TokenMatchingConfig.defaults();
        tokenMatchingService = new TokenMatchingService(
                connection, transactionTokenDao, categoryDao, tokenNormalizer, config
        );

        // Set up common test data
        account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        restaurants = categoryDao.insert(new Category("RESTAURANTS")).orElseThrow();
        groceries = categoryDao.insert(new Category("GROCERIES")).orElseThrow();
    }

    @Test
    void exactTokenMatchReturnsCategory() throws SQLException {
        // Setup: Create a transaction with exact matching tokens
        createTransactionWithTokens(
                "STARBUCKS #4756", restaurants, Set.of("starbucks")
        );

        // Execute: Search for matching categories
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(
                Set.of("starbucks")
        );

        // Verify: Exact match found
        assertEquals(1, matches.size());
        assertEquals(restaurants.getId(), matches.getFirst().category().getId());
        assertEquals(1.0, matches.getFirst().overlapRatio(), 0.001);
    }

    @Test
    void subsetTokenMatchReturnsCategory() throws SQLException {
        // Setup: Create a transaction with more tokens than the search
        createTransactionWithTokens(
                "SHOPPERS DRUG MART", groceries, Set.of("shoppers", "drug", "mart")
        );

        // Execute: Search with a subset of tokens
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(
                Set.of("shoppers", "drug")
        );

        // Verify: Match found with partial overlap
        assertEquals(1, matches.size());
        assertEquals(groceries.getId(), matches.getFirst().category().getId());
        // 2 matching tokens / min(2 search tokens, 3 stored tokens) = 2/2 = 1.0
        assertEquals(1.0, matches.getFirst().overlapRatio(), 0.001);
    }

    @Test
    void noMatchReturnsEmpty() throws SQLException {
        // Setup: Create a transaction with different tokens
        createTransactionWithTokens(
                "STARBUCKS", restaurants, Set.of("starbucks")
        );

        // Execute: Search with completely different tokens
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(
                Set.of("walmart", "grocery")
        );

        // Verify: No match found
        assertTrue(matches.isEmpty());
    }

    @Test
    void multipleMatchesRankedByOverlap() throws SQLException {
        // Setup: Create transactions with varying token overlap
        createTransactionWithTokens(
                "TIM HORTONS COFFEE", restaurants, Set.of("tim", "hortons", "coffee")
        );
        createTransactionWithTokens(
                "STARBUCKS COFFEE", restaurants, Set.of("starbucks", "coffee")
        );

        // Execute: Search with "coffee" - both should match
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(
                Set.of("coffee")
        );

        // Verify: Both transactions are a match, but because both are in the same category,
        // we should only see one category match with an exact overlap ratio.
        assertEquals(1, matches.size());
        assertEquals(restaurants.getId(), matches.getFirst().category().getId());
        assertEquals(1.0, matches.getFirst().overlapRatio(), 0.001);
    }

    @Test
    void respectsThresholdConfiguration() throws SQLException {
        // Setup: Create a transaction with 3 tokens
        createTransactionWithTokens(
                "SHOPPERS DRUG MART", groceries, Set.of("shoppers", "drug", "mart")
        );

        // Search with 2 tokens where only 1 matches: "shoppers" matches, "pharmacy" doesn't
        // Overlap ratio = 1 / min(2, 3) = 1/2 = 0.5
        final Set<String> searchTokens = Set.of("shoppers", "pharmacy");

        // Execute with strict threshold (1.0 = 100% match required)
        final TokenMatchingConfig strictConfig = TokenMatchingConfig.builder()
                .overlapThreshold(1.0)
                .build();
        final TokenMatchingService strictService = new TokenMatchingService(
                connection, transactionTokenDao, categoryDao, tokenNormalizer, strictConfig
        );

        List<TokenMatchingService.CategoryMatch> matches = strictService.findMatchingCategories(
                searchTokens  // 1 of 2 tokens matches
        );

        // Verify: No match with strict threshold (50% overlap < 100% threshold)
        assertTrue(matches.isEmpty());

        // Execute with loose threshold (0.5 = 50% match required)
        final TokenMatchingConfig looseConfig = TokenMatchingConfig.builder()
                .overlapThreshold(0.5)
                .build();
        final TokenMatchingService looseService = new TokenMatchingService(
                connection, transactionTokenDao, categoryDao, tokenNormalizer, looseConfig
        );

        matches = looseService.findMatchingCategories(
                searchTokens  // 1 of 2 tokens matches
        );

        // Verify: Match found with loose threshold (50% overlap >= 50% threshold)
        assertEquals(1, matches.size());
        assertEquals(groceries.getId(), matches.getFirst().category().getId());
        assertEquals(0.5, matches.getFirst().overlapRatio(), 0.001);
    }

    @Test
    void emptyTokensReturnsEmpty() {
        // Execute: Search with empty tokens
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(
                Set.of()
        );

        // Verify: Empty result
        assertTrue(matches.isEmpty());
    }

    @Test
    void nullTokensReturnsEmpty() {
        // Execute: Search with null tokens
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(null);

        // Verify: Empty result
        assertTrue(matches.isEmpty());
    }

    @Test
    void ignoresUnknownCategory() throws SQLException {
        final Set<String> searchTokens = Set.of("random", "merchant");

        // Setup: Create a transaction in UNKNOWN category
        createTransactionWithTokens("Ignored Transaction", Category.UNKNOWN, searchTokens);

        // Execute: Search with the exact same tokens as the transaction
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(
                searchTokens
        );

        // Verify: UNKNOWN category is not returned
        assertTrue(matches.isEmpty());
    }

    @Test
    void starbucksLocationsMatchSameCategory() throws SQLException {
        // Setup: Create transactions for different Starbucks locations
        createTransactionWithTokens(
                "STARBUCKS #4756", restaurants, Set.of("starbucks")
        );

        // Execute: Match a different Starbucks location
        Set<String> newTokens = tokenNormalizer.normalize("STARBUCKS 800-782-7282");
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(newTokens);

        // Verify: Same category matched
        assertEquals(1, matches.size());
        assertEquals(restaurants.getId(), matches.getFirst().category().getId());
        assertEquals(1.0, matches.getFirst().overlapRatio(), 0.001);
    }

    @Test
    void walmartLocationsMatchSameCategory() throws SQLException {
        // Setup: Create transaction for one Walmart location
        createTransactionWithTokens(
                "WAL-MART #1155", groceries, Set.of("walmart")
        );

        // Execute: Match a different Walmart location
        Set<String> newTokens = tokenNormalizer.normalize("WAL-MART #3045");
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(newTokens);

        // Verify: Same category matched
        assertEquals(1, matches.size());
        assertEquals(groceries.getId(), matches.getFirst().category().getId());
        assertEquals(1.0, matches.getFirst().overlapRatio(), 0.001);
    }

    @Test
    void aggregatesMatchesByCategory() throws SQLException {
        // Setup: Create multiple transactions for same category
        createTransactionWithTokens(
                "STARBUCKS #4756", restaurants, Set.of("starbucks")
        );
        createTransactionWithTokens(
                "STARBUCKS #1234", restaurants, Set.of("starbucks")
        );

        // Execute: Search for starbucks
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategories(
                Set.of("starbucks")
        );

        // Verify: Only one category match (aggregated, even though there were two matches)
        assertEquals(1, matches.size());
        assertEquals(restaurants.getId(), matches.getFirst().category().getId());
        assertEquals(1.0, matches.getFirst().overlapRatio(), 0.001);
    }

    @Test
    void findMatchingCategoriesForDescription() throws SQLException {
        // Setup: Create a transaction
        createTransactionWithTokens(
                "STARBUCKS #4756", restaurants, Set.of("starbucks")
        );

        // Execute: Search using a description string
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategoriesForDescription(
                "STARBUCKS 800-782-7282"
        );

        // Verify: Match found
        assertEquals(1, matches.size());
        assertEquals(restaurants.getId(), matches.getFirst().category().getId());
        assertEquals(1.0, matches.getFirst().overlapRatio(), 0.001);
    }

    // Helper method to create a transaction with tokens
    private void createTransactionWithTokens(
            String description, Category category, Set<String> tokens) throws SQLException {

        final Transaction txn = Transaction.newBuilder(TestUtils.createRandomTransaction(account))
                .setDescription(description)
                .build();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final CategorizedTransaction categorizedTxn = categorizedTransactionDao.insert(t,
                    new CategorizedTransaction(txn, category)
            ).orElseThrow();

            transactionTokenDao.insertTokens(t, categorizedTxn.getId(), tokens);
        }
    }
}
