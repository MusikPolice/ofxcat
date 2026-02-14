package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Combines two categories by moving all transactions from the source category
 * to the target category, then deleting the source category.
 */
public class CategoryCombineService {

    private static final Logger logger = LogManager.getLogger(CategoryCombineService.class);
    private static final int BATCH_SIZE = 100;

    private final Connection connection;
    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;

    @Inject
    public CategoryCombineService(
            Connection connection, CategoryDao categoryDao, CategorizedTransactionDao categorizedTransactionDao) {
        this.connection = connection;
        this.categoryDao = categoryDao;
        this.categorizedTransactionDao = categorizedTransactionDao;
    }

    /**
     * Moves all transactions from the source category to the target category,
     * then deletes the source category.
     *
     * @param sourceName the name of the category to move transactions from
     * @param targetName the name of the category to move transactions to (created if it doesn't exist)
     * @param progressCallback callback to report progress
     * @return a result describing what was done
     * @throws IllegalArgumentException if the source category doesn't exist, or source and target are the same
     */
    public CombineResult combine(String sourceName, String targetName, MigrationProgressCallback progressCallback) {
        final Category source = categoryDao
                .select(sourceName)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Source category \"%s\" does not exist", sourceName)));

        // look up the target, creating it if it doesn't exist
        final Optional<Category> existingTarget = categoryDao.select(targetName);
        final boolean targetCreated = existingTarget.isEmpty();
        final Category target = existingTarget.orElseGet(() -> categoryDao
                .insert(new Category(targetName))
                .orElseThrow(() ->
                        new RuntimeException(String.format("Failed to create target category \"%s\"", targetName))));

        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Source and target categories are the same");
        }

        if (targetCreated) {
            logger.info("Created target category \"{}\"", target.getName());
        }

        final List<CategorizedTransaction> transactions = categorizedTransactionDao.selectByCategory(source);
        final int total = transactions.size();
        int processed = 0;

        logger.info(
                "Combining category \"{}\" into \"{}\": {} transactions to move",
                source.getName(),
                target.getName(),
                total);

        // move transactions in batches
        for (int i = 0; i < transactions.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, transactions.size());
            List<CategorizedTransaction> batch = transactions.subList(i, end);

            try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
                for (CategorizedTransaction txn : batch) {
                    categorizedTransactionDao.updateCategory(t, txn.getId(), target);
                    processed++;
                    progressCallback.onProgress(processed, total);
                }
            } catch (SQLException ex) {
                logger.error("Failed to move transactions at batch starting index {}", i, ex);
                throw new RuntimeException("Failed to combine categories", ex);
            }
        }

        // delete the now-empty source category
        categoryDao.delete(source.getId());
        logger.info("Deleted source category \"{}\"", source.getName());

        return new CombineResult(source.getName(), target.getName(), processed, targetCreated);
    }

    /**
     * The result of combining two categories.
     */
    public record CombineResult(String sourceName, String targetName, int transactionsMoved, boolean targetCreated) {}
}
