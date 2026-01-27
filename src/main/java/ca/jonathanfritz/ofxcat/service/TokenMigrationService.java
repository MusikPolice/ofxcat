package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.matching.KeywordRulesConfig;
import ca.jonathanfritz.ofxcat.matching.TokenNormalizer;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for migrating existing transactions to have tokens and optionally
 * recategorizing them based on keyword rules.
 */
public class TokenMigrationService {

    private static final Logger logger = LogManager.getLogger(TokenMigrationService.class);
    private static final int BATCH_SIZE = 100;

    private final Connection connection;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;
    private final CategoryDao categoryDao;
    private final TokenNormalizer tokenNormalizer;
    private final KeywordRulesConfig keywordRulesConfig;

    @Inject
    public TokenMigrationService(
            Connection connection,
            CategorizedTransactionDao categorizedTransactionDao,
            TransactionTokenDao transactionTokenDao,
            CategoryDao categoryDao,
            TokenNormalizer tokenNormalizer,
            KeywordRulesConfig keywordRulesConfig
    ) {
        this.connection = connection;
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.transactionTokenDao = transactionTokenDao;
        this.categoryDao = categoryDao;
        this.tokenNormalizer = tokenNormalizer;
        this.keywordRulesConfig = keywordRulesConfig;
    }

    /**
     * Migrates existing transactions by:
     * 1. Computing and storing tokens for transactions that don't have them
     * 2. Optionally recategorizing transactions based on keyword rules
     *
     * @return a report describing what was migrated and changed
     */
    public MigrationReport migrateExistingTransactions() {
        return migrateExistingTransactions(MigrationProgressCallback.NOOP);
    }

    /**
     * Migrates existing transactions by:
     * 1. Computing and storing tokens for transactions that don't have them
     * 2. Optionally recategorizing transactions based on keyword rules
     *
     * @param progressCallback callback to report progress during migration
     * @return a report describing what was migrated and changed
     */
    public MigrationReport migrateExistingTransactions(MigrationProgressCallback progressCallback) {
        MigrationReport report = new MigrationReport();

        // Only load transactions that actually need migration (no tokens yet)
        List<CategorizedTransaction> needsMigration = categorizedTransactionDao.selectWithoutTokens();

        if (needsMigration.isEmpty()) {
            logger.debug("Token migration: no transactions need migration");
            return report;
        }

        logger.info("Token migration: processing {} transactions", needsMigration.size());
        final int total = needsMigration.size();
        int processed = 0;

        // Process in batches for efficiency
        for (int i = 0; i < needsMigration.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, needsMigration.size());
            List<CategorizedTransaction> batch = needsMigration.subList(i, end);

            try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
                for (CategorizedTransaction txn : batch) {
                    migrateTransaction(t, txn, report);
                    processed++;
                    progressCallback.onProgress(processed, total);
                }
            } catch (SQLException ex) {
                logger.error("Token migration failed at batch starting index {}", i, ex);
                throw new RuntimeException("Token migration failed", ex);
            }

            // Log progress for large migrations
            if (needsMigration.size() > BATCH_SIZE && (i + BATCH_SIZE) % (BATCH_SIZE * 10) == 0) {
                logger.info("Token migration progress: {} / {} transactions processed",
                        Math.min(i + BATCH_SIZE, needsMigration.size()), needsMigration.size());
            }
        }

        logger.info("Token migration completed: {} processed, {} recategorized, {} skipped",
                report.getProcessedCount(), report.getRecategorizedCount(), report.getSkippedCount());

        return report;
    }

    /**
     * Migrates a single transaction by storing its tokens and optionally recategorizing.
     */
    private void migrateTransaction(DatabaseTransaction t, CategorizedTransaction txn, MigrationReport report) throws SQLException {
        String description = txn.getDescription();
        Set<String> tokens = tokenNormalizer.normalize(description);

        // Skip if no meaningful tokens (transaction will rely on exact match)
        if (tokens.isEmpty()) {
            report.incrementSkipped();
            logger.debug("Skipped migration for transaction with no tokens: {}", description);
            return;
        }

        // Store tokens
        transactionTokenDao.insertTokens(t, txn.getId(), tokens);
        report.incrementProcessed();

        // Check keyword rules for potential recategorization
        if (keywordRulesConfig.isAutoCategorizeEnabled()) {
            Optional<String> keywordCategoryName = keywordRulesConfig.findMatchingCategory(tokens);

            if (keywordCategoryName.isPresent()) {
                String newCategoryName = keywordCategoryName.get();
                String currentCategoryName = txn.getCategory().getName();

                // Only recategorize if the category would change
                if (!newCategoryName.equalsIgnoreCase(currentCategoryName)) {
                    recategorizeTransaction(t, txn, newCategoryName, report);
                }
            }
        }
    }

    /**
     * Recategorizes a transaction to a new category.
     */
    private void recategorizeTransaction(DatabaseTransaction t, CategorizedTransaction txn, String newCategoryName, MigrationReport report) throws SQLException {
        String oldCategoryName = txn.getCategory().getName();

        // Get or create the new category
        Optional<Category> newCategory = categoryDao.getOrCreate(t, newCategoryName);
        if (newCategory.isEmpty()) {
            logger.error("Failed to get or create category: {}", newCategoryName);
            return;
        }

        // Update the transaction's category
        boolean updated = categorizedTransactionDao.updateCategory(t, txn.getId(), newCategory.get());
        if (!updated) {
            logger.error("Failed to update category for transaction: {}", txn.getId());
            return;
        }

        // Record the recategorization
        report.addRecategorization(txn.getDescription(), oldCategoryName, newCategoryName);
        logger.debug("Recategorized '{}': {} -> {}", txn.getDescription(), oldCategoryName, newCategoryName);
    }

    /**
     * Checks if migration is needed (any transactions without tokens).
     * This is an efficient check that doesn't load transaction data into memory.
     *
     * @return true if there are transactions that need migration
     */
    public boolean isMigrationNeeded() {
        return categorizedTransactionDao.hasTransactionsWithoutTokens();
    }

    /**
     * Forces re-migration of all transactions, deleting existing tokens first.
     * Use this after updating keyword rules to apply them to all transactions.
     *
     * @return a report describing what was migrated and changed
     */
    public MigrationReport forceMigration() {
        return forceMigration(false);
    }

    /**
     * Forces re-migration of all transactions.
     *
     * @param dryRun if true, shows what would change without making actual changes
     * @return a report describing what would be (or was) migrated and changed
     */
    public MigrationReport forceMigration(boolean dryRun) {
        return forceMigration(dryRun, MigrationProgressCallback.NOOP);
    }

    /**
     * Forces re-migration of all transactions.
     *
     * @param dryRun if true, shows what would change without making actual changes
     * @param progressCallback callback to report progress during migration
     * @return a report describing what would be (or was) migrated and changed
     */
    public MigrationReport forceMigration(boolean dryRun, MigrationProgressCallback progressCallback) {
        if (dryRun) {
            return simulateMigration(progressCallback);
        }

        // Delete all existing tokens
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.deleteAllTokens(t);
            logger.info("Force migration: deleted all existing tokens");
        } catch (SQLException ex) {
            logger.error("Force migration: failed to delete existing tokens", ex);
            throw new RuntimeException("Force migration failed", ex);
        }

        // Run normal migration (will now process all transactions since none have tokens)
        return migrateExistingTransactions(progressCallback);
    }

    /**
     * Simulates migration to show what would change without making actual changes.
     */
    private MigrationReport simulateMigration(MigrationProgressCallback progressCallback) {
        MigrationReport report = new MigrationReport();

        // Load all transactions (regardless of token status)
        List<CategorizedTransaction> allTransactions = categorizedTransactionDao.selectAll();

        if (allTransactions.isEmpty()) {
            logger.debug("Dry run: no transactions to simulate");
            return report;
        }

        logger.info("Dry run: simulating migration of {} transactions", allTransactions.size());
        final int total = allTransactions.size();
        int processed = 0;

        for (CategorizedTransaction txn : allTransactions) {
            simulateTransactionMigration(txn, report);
            processed++;
            progressCallback.onProgress(processed, total);
        }

        logger.info("Dry run completed: {} would be processed, {} would be recategorized, {} would be skipped",
                report.getProcessedCount(), report.getRecategorizedCount(), report.getSkippedCount());

        return report;
    }

    /**
     * Simulates migration of a single transaction without making changes.
     */
    private void simulateTransactionMigration(CategorizedTransaction txn, MigrationReport report) {
        String description = txn.getDescription();
        Set<String> tokens = tokenNormalizer.normalize(description);

        // Skip if no meaningful tokens
        if (tokens.isEmpty()) {
            report.incrementSkipped();
            return;
        }

        report.incrementProcessed();

        // Check keyword rules for potential recategorization
        if (keywordRulesConfig.isAutoCategorizeEnabled()) {
            Optional<String> keywordCategoryName = keywordRulesConfig.findMatchingCategory(tokens);

            if (keywordCategoryName.isPresent()) {
                String newCategoryName = keywordCategoryName.get();
                String currentCategoryName = txn.getCategory().getName();

                // Would recategorize if category would change
                if (!newCategoryName.equalsIgnoreCase(currentCategoryName)) {
                    report.addRecategorization(description, currentCategoryName, newCategoryName);
                }
            }
        }
    }
}
