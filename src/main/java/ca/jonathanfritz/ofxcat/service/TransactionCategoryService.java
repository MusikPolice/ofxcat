package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.DescriptionCategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.DescriptionCategory;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import com.google.inject.Inject;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TransactionCategoryService {

    private final CategoryDao categoryDao;
    private final DescriptionCategoryDao descriptionCategoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final Connection connection;

    private static final Logger logger = LoggerFactory.getLogger(TransactionCategoryService.class);

    @Inject
    public TransactionCategoryService(CategoryDao categoryDao, DescriptionCategoryDao descriptionCategoryDao, CategorizedTransactionDao categorizedTransactionDao, Connection connection) {
        this.categoryDao = categoryDao;
        this.descriptionCategoryDao = descriptionCategoryDao;
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.connection = connection;
    }

    public CategorizedTransaction put(Transaction newTransaction, Category newCategory) {
        try (final DatabaseTransaction t = new DatabaseTransaction(connection)) {
            return put(t, newTransaction, newCategory);
        } catch (SQLException ex) {
            logger.error("Failed to insert categorized transaction",ex);
            return null;
        }
    }

    /**
     * Maps the specified transaction's description to the specified category
     */
    public CategorizedTransaction put(DatabaseTransaction t, Transaction newTransaction, Category newCategory) throws SQLException {
        // if a DescriptionCategory with the specified description and category exists, use it. Otherwise, insert one
        final DescriptionCategory descriptionCategory = descriptionCategoryDao.selectByDescriptionAndCategory(t, newTransaction.getDescription(), newCategory)
                .or(() -> {
                    logger.debug("Implicitly creating DescriptionCategory with description {} and Category {}", newTransaction.getDescription(), newCategory);
                    try {
                        return descriptionCategoryDao.insert(t, new DescriptionCategory(newTransaction.getDescription(), newCategory));
                    } catch (SQLException ex) {
                        logger.error("Failed to insert into descriptionCategoryDao", ex);
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new SQLException("Failed to put categorized transaction"));

        return new CategorizedTransaction(newTransaction, descriptionCategory.getCategory());
    }

    /**
     * Converts the specified {@link Transaction} into a {@link CategorizedTransaction} by searching for previously
     * categorized transactions that share an account number and transaction description. If one or more are found that
     * are all associated with a single category, that category is assigned to the specified transaction. This is useful
     * for categorizing recurring purchases from the same vendor, like a monthly mortgage payment or weekly grocery
     * purchase.
     * @param transaction the Transaction to categorize
     * @return an {@link Optional<CategorizedTransaction>} if a previously categorized transaction exists that matches
     * the specified transaction, else {@link Optional#empty()}
     */
    public Optional<CategorizedTransaction> getCategoryExact(DatabaseTransaction t, Transaction transaction) {
        if (StringUtils.isBlank(transaction.getDescription())) {
            logger.warn("Specified transaction {} does not have a description. Cannot search for similar transactions", transaction);
            return Optional.empty();
        }
        if (transaction.getAccount() == null || StringUtils.isBlank(transaction.getAccount().getAccountNumber())) {
            logger.warn("Specified transaction {} does not have an account number. Cannot search for similar transactions", transaction);
            return Optional.empty();
        }

        final String withAccountIdAndDescription = String.format("with accountNumber %s and description %s",
                transaction.getAccount().getAccountNumber(), transaction.getDescription());

        // find previously categorized transactions with the same accountId and description
        try {
            logger.info("Attempting to find previously categorized transactions {}", withAccountIdAndDescription);

            // TODO: search for exact string, but also for each token in the string, all on an executor
            //       count number of matches for each category that is associated with the string, arrange into Map<Transaction, Integer>
            //       Fuzzy match each map key's description w/ incoming transaction description, multiply result by category count, arrange into Map<Transaction, Float>
            //       finally sum category "scores" by adding together map values that share a category, rank categories by score, prompt user to pick one
            //       also add an index on categorized transaction description column b/c we're hitting it a lot
            //       this incorporates exact matching & fuzzy matching based on existing categorized transactions, lets us delete the entire matchFuzzy(...) code path

            final List<CategorizedTransaction> existingTransactions = categorizedTransactionDao.findByDescriptionAndAccountNumber(t, transaction);
            if (existingTransactions.isEmpty()) {
                // no similar transactions found
                logger.debug("No previously categorized transactions {} were found", withAccountIdAndDescription);
                return Optional.empty();
            }

            // if all of the matches share a category, we can use it
            Optional<Category> potentialCategory = getSharedCategoryFromTransactions(existingTransactions);
            if (potentialCategory.isPresent()) {
                logger.debug("All previously categorized transactions {} were categorized as {}", withAccountIdAndDescription, potentialCategory.get());
                return Optional.of(new CategorizedTransaction(transaction, potentialCategory.get()));
            }

            // matches belong to disparate categories
            // let's see if some subset of them also match on amount (ex. mortgage payments)
            final List<CategorizedTransaction> amountMatches = existingTransactions.stream()
                    .filter(ct -> Math.floor(ct.getAmount()) == Math.floor(transaction.getAmount()))
                    .collect(Collectors.toList());
            if (amountMatches.isEmpty()) {
                logger.debug("The previously categorized transactions {} were all for different amounts", withAccountIdAndDescription);
                return Optional.empty();
            }

            // as above, if all of the matches share a category, we can use it
            potentialCategory = getSharedCategoryFromTransactions(amountMatches);
            if (potentialCategory.isPresent()) {
                logger.debug("All previously categorized transactions {} were for ${}, and were categorized as {}",
                        withAccountIdAndDescription, amountMatches.get(0).getAmount(), potentialCategory.get());
                return Optional.of(new CategorizedTransaction(transaction, potentialCategory.get()));
            }

            // TODO: just prompt the user to pick a category, goddamnit!

            logger.info("Failed to match incoming transaction to previously categorized transactions");
            return Optional.empty();

        } catch (SQLException ex) {
            logger.error("Failed to find exact match for transaction", ex);
            return Optional.empty();
        }
    }

    private Optional<Category> getSharedCategoryFromTransactions(List<CategorizedTransaction> existing) {
        final Category potentialCategory = existing.get(0).getCategory();
        boolean allSameCategory = existing.stream()
                .allMatch(ct -> ct.getCategory() == potentialCategory);
        if (allSameCategory) {
            return Optional.of(potentialCategory);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns a list of categories whose names most closely match the description of the specified transaction.
     * Once a category is selected from the returned set, it must be associated to the specified transaction by way
     * of the {@link #put(Transaction, Category)} method.
     * @param transaction the transaction to find potential category matches for.
     * @param limit the max number of potential categories to return
     * @return a list of potential {@link Category} matches, sorted by relevance descending
     */
    public List<Category> getCategoryFuzzy(DatabaseTransaction t, Transaction transaction, int limit) {
        // TODO: is 80% match good enough? - make this configurable
        logger.debug("Fuzzy category matches for transaction description \"{}\":", transaction.getDescription());

        // get all description categories and transform to a map keyed on description string
        try {
            final Map<String, List<Category>> descriptionCategories = descriptionCategoryDao.selectAll(t).stream()
                    .collect(Collectors.toMap(DescriptionCategory::getDescription,
                            dc -> Collections.singletonList(dc.getCategory()),
                            (l1, l2) -> Stream.concat(l1.stream(), l2.stream()).collect(Collectors.toList()))
                    );

            return FuzzySearch.extractSorted(transaction.getDescription(), descriptionCategories.keySet(), 80)
                    .stream()
                    .sorted((o1, o2) -> o2.getScore() - o1.getScore()) // sort descending
                    .map(er -> {
                        logger.debug("{}: {}", er.getString(), er.getScore());
                        return descriptionCategories.get(er.getString());
                    })
                    .flatMap((Function<List<Category>, Stream<Category>>) Collection::stream)
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            logger.error("Failed to get all description categories", e);
            return new ArrayList<>();
        }
    }

    /**
     * Returns an alphabetically sorted list of all known category names
     */
    public List<String> getCategoryNames() {
        return categoryDao.select().stream()
                .map(Category::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
