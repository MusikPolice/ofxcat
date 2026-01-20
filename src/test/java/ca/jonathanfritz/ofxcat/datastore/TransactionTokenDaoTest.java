package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.TransactionToken;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTokenDaoTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;

    TransactionTokenDaoTest() {
        categoryDao = injector.getInstance(CategoryDao.class);
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        transactionTokenDao = injector.getInstance(TransactionTokenDao.class);
    }

    @Test
    void insertAndRetrieveTokens() throws SQLException {
        // Setup: Create a categorized transaction
        Category category = categoryDao.insert(new Category("RESTAURANTS")).orElseThrow();
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        CategorizedTransaction transaction = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElseThrow();

        // Setup: Tokens to insert
        Set<String> tokens = Set.of("starbucks", "coffee");

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Execute: Insert tokens
            transactionTokenDao.insertTokens(t, transaction.getId(), tokens);

            // Verify: Retrieve tokens
            Set<String> retrievedTokens = transactionTokenDao.getTokens(t, transaction.getId());
            assertEquals(tokens, retrievedTokens);
        }
    }

    @Test
    void deleteTokens_removesTokensForTransaction() throws SQLException {
        // Setup: Create a categorized transaction with tokens
        Category category = categoryDao.insert(new Category("GROCERIES")).orElseThrow();
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        CategorizedTransaction transaction = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElseThrow();

        Set<String> tokens = Set.of("walmart", "store");

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, transaction.getId(), tokens);

            // Verify tokens exist
            assertTrue(transactionTokenDao.hasTokens(t, transaction.getId()));
        }

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Execute: Delete tokens explicitly
            // Note: ON DELETE CASCADE is defined in the schema but SQLite foreign keys
            // are disabled (see DatabaseTransactionErrorHandlingTest), so explicit
            // deletion via deleteTokens() is required when removing transactions.
            transactionTokenDao.deleteTokens(t, transaction.getId());

            // Verify: Tokens are deleted
            assertFalse(transactionTokenDao.hasTokens(t, transaction.getId()));
        }
    }

    @Test
    void findTransactionsWithMatchingTokens() throws SQLException {
        // Setup: Create two categorized transactions with overlapping tokens
        Category restaurants = categoryDao.insert(new Category("RESTAURANTS")).orElseThrow();
        Category groceries = categoryDao.insert(new Category("GROCERIES")).orElseThrow();
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();

        CategorizedTransaction starbucksTxn = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), restaurants)
        ).orElseThrow();

        CategorizedTransaction walmartTxn = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), groceries)
        ).orElseThrow();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Insert tokens for both transactions
            transactionTokenDao.insertTokens(t, starbucksTxn.getId(), Set.of("starbucks", "coffee"));
            transactionTokenDao.insertTokens(t, walmartTxn.getId(), Set.of("walmart", "grocery"));

            // Execute: Find transactions matching "starbucks"
            List<TransactionTokenDao.TokenMatchResult> results = transactionTokenDao.findTransactionsWithMatchingTokens(
                    t, Set.of("starbucks", "coffee")
            );

            // Verify: Only starbucks transaction is found
            assertEquals(1, results.size());
            assertEquals(starbucksTxn.getId(), results.get(0).transactionId());
            assertEquals(restaurants.getId(), results.get(0).categoryId());
            assertEquals(2, results.get(0).matchingTokenCount());
        }
    }

    @Test
    void hasTokensReturnsTrueWhenTokensExist() throws SQLException {
        // Setup: Create a transaction with tokens
        Category category = categoryDao.insert(new Category("SHOPPING")).orElseThrow();
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        CategorizedTransaction transaction = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElseThrow();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Initially no tokens
            assertFalse(transactionTokenDao.hasTokens(t, transaction.getId()));

            // Insert tokens
            transactionTokenDao.insertTokens(t, transaction.getId(), Set.of("amazon", "shopping"));

            // Verify: hasTokens returns true
            assertTrue(transactionTokenDao.hasTokens(t, transaction.getId()));
        }
    }

    @Test
    void hasTokensReturnsFalseWhenNoTokens() throws SQLException {
        // Setup: Create a transaction WITHOUT tokens
        Category category = categoryDao.insert(new Category("UTILITIES")).orElseThrow();
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        CategorizedTransaction transaction = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElseThrow();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // Verify: hasTokens returns false
            assertFalse(transactionTokenDao.hasTokens(t, transaction.getId()));
        }
    }

    @Test
    void findTransactionsWithMatchingTokens_excludesUnknownCategory() throws SQLException {
        // Setup: Create transaction in UNKNOWN category
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        CategorizedTransaction unknownTxn = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), Category.UNKNOWN)
        ).orElseThrow();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, unknownTxn.getId(), Set.of("random", "merchant"));

            // Execute: Search for matching tokens
            List<TransactionTokenDao.TokenMatchResult> results = transactionTokenDao.findTransactionsWithMatchingTokens(
                    t, Set.of("random", "merchant")
            );

            // Verify: UNKNOWN category transactions are excluded
            assertTrue(results.isEmpty());
        }
    }

    @Test
    void findTransactionsWithMatchingTokens_returnsMultipleMatches() throws SQLException {
        // Setup: Create multiple transactions with same tokens
        Category restaurants = categoryDao.insert(new Category("RESTAURANTS")).orElseThrow();
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();

        CategorizedTransaction txn1 = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), restaurants)
        ).orElseThrow();

        CategorizedTransaction txn2 = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), restaurants)
        ).orElseThrow();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, txn1.getId(), Set.of("starbucks"));
            transactionTokenDao.insertTokens(t, txn2.getId(), Set.of("starbucks", "coffee"));

            // Execute: Find transactions with "starbucks"
            List<TransactionTokenDao.TokenMatchResult> results = transactionTokenDao.findTransactionsWithMatchingTokens(
                    t, Set.of("starbucks")
            );

            // Verify: Both transactions match
            assertEquals(2, results.size());
        }
    }

    @Test
    void getTokenCount_returnsCorrectCount() throws SQLException {
        // Setup: Create transaction with multiple tokens
        Category category = categoryDao.insert(new Category("ENTERTAINMENT")).orElseThrow();
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        CategorizedTransaction transaction = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElseThrow();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, transaction.getId(), Set.of("netflix", "streaming", "entertainment"));

            // Execute & Verify
            assertEquals(3, transactionTokenDao.getTokenCount(t, transaction.getId()));
        }
    }
}
