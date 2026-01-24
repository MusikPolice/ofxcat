package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.DescriptionCategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.matching.KeywordRule;
import ca.jonathanfritz.ofxcat.matching.KeywordRulesConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenMigrationServiceTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final DescriptionCategoryDao descriptionCategoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;

    private Account testAccount;

    public TokenMigrationServiceTest() {
        categoryDao = injector.getInstance(CategoryDao.class);
        descriptionCategoryDao = injector.getInstance(DescriptionCategoryDao.class);
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        transactionTokenDao = new TransactionTokenDao();
    }

    @BeforeEach
    void setUp() {
        testAccount = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        assertNotNull(testAccount);
    }

    @Test
    void migrateEmptyDatabase() {
        // Given: empty database (no transactions)
        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: report shows no work done
        assertEquals(0, report.getProcessedCount());
        assertEquals(0, report.getRecategorizedCount());
        assertEquals(0, report.getSkippedCount());
        assertFalse(report.hasRecategorizations());
    }

    @Test
    void migrateTransactionWithoutTokens() throws SQLException {
        // Given: a transaction exists without tokens
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        CategorizedTransaction txn = insertTransactionWithoutTokens("AMAZON MARKETPLACE", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: the transaction was processed and has tokens now
        assertEquals(1, report.getProcessedCount());
        assertEquals(0, report.getRecategorizedCount());
        assertEquals(0, report.getSkippedCount());

        // Verify tokens were stored
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            assertTrue(transactionTokenDao.hasTokens(t, txn.getId()));
            Set<String> tokens = transactionTokenDao.getTokens(t, txn.getId());
            assertTrue(tokens.contains("amazon"));
            assertTrue(tokens.contains("marketplace"));
        }
    }

    @Test
    void migrateSkipsTransactionsWithExistingTokens() throws SQLException {
        // Given: a transaction with tokens already stored
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        insertTransactionWithTokens("STARBUCKS COFFEE", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: the transaction was skipped (already has tokens)
        assertEquals(0, report.getProcessedCount());
        assertEquals(0, report.getRecategorizedCount());
        assertEquals(0, report.getSkippedCount());
    }

    @Test
    void isMigrationNeededReturnsTrueWhenTransactionsMissingTokens() throws SQLException {
        // Given: a transaction without tokens
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        insertTransactionWithoutTokens("WALMART GROCERY", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When/Then:
        assertTrue(migrationService.isMigrationNeeded());
    }

    @Test
    void isMigrationNeededReturnsFalseWhenAllHaveTokens() throws SQLException {
        // Given: a transaction with tokens
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        insertTransactionWithTokens("WALMART GROCERY", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When/Then:
        assertFalse(migrationService.isMigrationNeeded());
    }

    @Test
    void migrateRecategorizesWhenKeywordRuleMatches() throws SQLException {
        // Given: a transaction in "Unknown" category
        CategorizedTransaction txn = insertTransactionWithoutTokens("COSTCO WHOLESALE", Category.UNKNOWN);

        // And: keyword rules that match "costco" to "Groceries"
        KeywordRulesConfig config = createKeywordRulesConfig(true,
                new KeywordRule(List.of("costco"), "Groceries"));

        TokenMigrationService migrationService = createMigrationService(config);

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: the transaction was processed and recategorized
        assertEquals(1, report.getProcessedCount());
        assertEquals(1, report.getRecategorizedCount());
        assertTrue(report.hasRecategorizations());

        // Verify the recategorization details
        MigrationReport.RecategorizationEntry entry = report.getRecategorizations().getFirst();
        assertEquals("COSTCO WHOLESALE", entry.description());
        assertEquals("UNKNOWN", entry.oldCategory());
        assertEquals("Groceries", entry.newCategory());  // Report stores name from keyword rule

        // Verify the transaction now has the new category (Category uppercases names)
        CategorizedTransaction updated = categorizedTransactionDao.select(txn.getId()).orElse(null);
        assertNotNull(updated);
        assertEquals("GROCERIES", updated.getCategory().getName());
    }

    @Test
    void migrateDoesNotRecategorizeWhenAutoCategorizeDisabled() throws SQLException {
        // Given: a transaction in "Unknown" category
        CategorizedTransaction txn = insertTransactionWithoutTokens("COSTCO WHOLESALE", Category.UNKNOWN);

        // And: keyword rules that match "costco" but auto_categorize is disabled
        KeywordRulesConfig config = createKeywordRulesConfig(false,
                new KeywordRule(List.of("costco"), "Groceries"));

        TokenMigrationService migrationService = createMigrationService(config);

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: the transaction was processed but not recategorized
        assertEquals(1, report.getProcessedCount());
        assertEquals(0, report.getRecategorizedCount());
        assertFalse(report.hasRecategorizations());

        // Verify the transaction still has the original category
        CategorizedTransaction updated = categorizedTransactionDao.select(txn.getId()).orElse(null);
        assertNotNull(updated);
        assertEquals("UNKNOWN", updated.getCategory().getName());
    }

    @Test
    void migrateDoesNotRecategorizeWhenCategoryAlreadyMatches() throws SQLException {
        // Given: a transaction already in "Groceries" category
        Category groceries = categoryDao.insert(new Category("Groceries")).orElse(null);
        insertTransactionWithoutTokens("COSTCO WHOLESALE", groceries);

        // And: keyword rules that also match to "Groceries"
        KeywordRulesConfig config = createKeywordRulesConfig(true,
                new KeywordRule(List.of("costco"), "Groceries"));

        TokenMigrationService migrationService = createMigrationService(config);

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: the transaction was processed but not recategorized (already correct)
        assertEquals(1, report.getProcessedCount());
        assertEquals(0, report.getRecategorizedCount());
        assertFalse(report.hasRecategorizations());
    }

    @Test
    void migrateSkipsTransactionsWithNoMeaningfulTokens() throws SQLException {
        // Given: a transaction with a description that produces no meaningful tokens
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        insertTransactionWithoutTokens("***", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: the transaction was skipped (no meaningful tokens)
        assertEquals(0, report.getProcessedCount());
        assertEquals(0, report.getRecategorizedCount());
        assertEquals(1, report.getSkippedCount());
    }

    @Test
    void migrateBatchesLargeNumberOfTransactions() throws SQLException {
        // Given: more transactions than a single batch (BATCH_SIZE = 100)
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        for (int i = 0; i < 150; i++) {
            insertTransactionWithoutTokens("AMAZON ORDER " + i, category);
        }

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When: we run migration
        MigrationReport report = migrationService.migrateExistingTransactions();

        // Then: all transactions were processed
        assertEquals(150, report.getProcessedCount());
        assertEquals(0, report.getRecategorizedCount());
        assertEquals(0, report.getSkippedCount());
    }

    @Test
    void migrateIsIdempotent() throws SQLException {
        // Given: a transaction without tokens
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        insertTransactionWithoutTokens("UBER EATS", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When: we run migration twice
        MigrationReport report1 = migrationService.migrateExistingTransactions();
        MigrationReport report2 = migrationService.migrateExistingTransactions();

        // Then: first run processes the transaction
        assertEquals(1, report1.getProcessedCount());

        // And: second run skips it (already migrated)
        assertEquals(0, report2.getProcessedCount());
    }

    @Test
    void forceMigration_deletesExistingTokensAndReprocesses() throws SQLException {
        // Given: a transaction that already has tokens
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        CategorizedTransaction txn = insertTransactionWithTokens("STARBUCKS COFFEE", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // Verify migration is NOT needed (tokens already exist)
        assertFalse(migrationService.isMigrationNeeded());

        // When: we force migration
        MigrationReport report = migrationService.forceMigration();

        // Then: the transaction was reprocessed
        assertEquals(1, report.getProcessedCount());

        // And: tokens still exist
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            assertTrue(transactionTokenDao.hasTokens(t, txn.getId()));
        }
    }

    @Test
    void forceMigration_appliesNewKeywordRules() throws SQLException {
        // Given: a transaction already migrated with UNKNOWN category
        CategorizedTransaction txn = insertTransactionWithTokens("COSTCO WHOLESALE", Category.UNKNOWN);

        // Verify transaction is in UNKNOWN category
        CategorizedTransaction before = categorizedTransactionDao.select(txn.getId()).orElse(null);
        assertNotNull(before);
        assertEquals("UNKNOWN", before.getCategory().getName());

        // When: we force migration with keyword rules that match "costco" to "Groceries"
        KeywordRulesConfig config = createKeywordRulesConfig(true,
                new KeywordRule(List.of("costco"), "Groceries"));
        TokenMigrationService migrationService = createMigrationService(config);

        MigrationReport report = migrationService.forceMigration();

        // Then: the transaction was recategorized
        assertEquals(1, report.getProcessedCount());
        assertEquals(1, report.getRecategorizedCount());

        // And: the category was updated
        CategorizedTransaction after = categorizedTransactionDao.select(txn.getId()).orElse(null);
        assertNotNull(after);
        assertEquals("GROCERIES", after.getCategory().getName());
    }

    @Test
    void forceMigration_dryRunShowsChangesWithoutApplying() throws SQLException {
        // Given: a transaction already migrated with UNKNOWN category
        CategorizedTransaction txn = insertTransactionWithTokens("COSTCO WHOLESALE", Category.UNKNOWN);

        // When: we force migration with dry-run enabled
        KeywordRulesConfig config = createKeywordRulesConfig(true,
                new KeywordRule(List.of("costco"), "Groceries"));
        TokenMigrationService migrationService = createMigrationService(config);

        MigrationReport report = migrationService.forceMigration(true);

        // Then: the report shows what would be changed
        assertEquals(1, report.getProcessedCount());
        assertEquals(1, report.getRecategorizedCount());
        assertTrue(report.hasRecategorizations());

        // But: the transaction was NOT actually changed
        CategorizedTransaction unchanged = categorizedTransactionDao.select(txn.getId()).orElse(null);
        assertNotNull(unchanged);
        assertEquals("UNKNOWN", unchanged.getCategory().getName());

        // And: tokens were NOT deleted (still exist from original migration)
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            assertTrue(transactionTokenDao.hasTokens(t, txn.getId()));
        }
    }

    @Test
    void forceMigration_reprocessesAllTransactions() throws SQLException {
        // Given: multiple transactions, some already with tokens
        Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        insertTransactionWithTokens("AMAZON ORDER 1", category);
        insertTransactionWithTokens("WALMART GROCERY", category);
        insertTransactionWithoutTokens("NEW TRANSACTION", category);

        TokenMigrationService migrationService = createMigrationService(KeywordRulesConfig.empty());

        // When: we force migration
        MigrationReport report = migrationService.forceMigration();

        // Then: all transactions were processed
        assertEquals(3, report.getProcessedCount());
    }

    // Helper methods

    private TokenMigrationService createMigrationService(KeywordRulesConfig keywordRulesConfig) {
        return new TokenMigrationService(
                connection,
                categorizedTransactionDao,
                transactionTokenDao,
                descriptionCategoryDao,
                categoryDao,
                tokenNormalizer,
                keywordRulesConfig
        );
    }

    private CategorizedTransaction insertTransactionWithoutTokens(String description, Category category) {
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(testAccount)
                .setDescription(description)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .setAmount(-50.0f)
                .build();

        return categorizedTransactionDao.insert(new CategorizedTransaction(transaction, category)).orElse(null);
    }

    private CategorizedTransaction insertTransactionWithTokens(String description, Category category) throws SQLException {
        CategorizedTransaction txn = insertTransactionWithoutTokens(description, category);

        // Store tokens
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Set<String> tokens = tokenNormalizer.normalize(description);
            transactionTokenDao.insertTokens(t, txn.getId(), tokens);
        }

        return txn;
    }

    private KeywordRulesConfig createKeywordRulesConfig(boolean autoCategorize, KeywordRule... rules) {
        KeywordRulesConfig config = new KeywordRulesConfig(List.of(rules));
        KeywordRulesConfig.Settings settings = new KeywordRulesConfig.Settings();
        settings.setAutoCategorize(autoCategorize);
        config.setSettings(settings);
        return config;
    }
}
