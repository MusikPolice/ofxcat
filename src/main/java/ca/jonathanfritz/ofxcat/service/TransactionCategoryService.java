package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cli.CLI;
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
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

// TODO: Test me!
public class TransactionCategoryService {

    private final CategoryDao categoryDao;
    private final DescriptionCategoryDao descriptionCategoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final Connection connection;
    private final CLI cli;

    private static final Logger logger = LogManager.getLogger(TransactionCategoryService.class);

    @Inject
    public TransactionCategoryService(CategoryDao categoryDao, DescriptionCategoryDao descriptionCategoryDao, CategorizedTransactionDao categorizedTransactionDao, Connection connection, CLI cli) {
        this.categoryDao = categoryDao;
        this.descriptionCategoryDao = descriptionCategoryDao;
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.connection = connection;
        this.cli = cli;
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

    // TODO: never auto-categorize as UNKNOWN! Always prompt user
    // TODO: why does LCBO/RAO not auto-categorize?
    public CategorizedTransaction categorizeTransaction(DatabaseTransaction t, Transaction transaction) throws SQLException {
        // try an exact match first
        // TODO: Includes account number in search - consider dropping req to re-use categorizations between checking/visa?
        Optional<CategorizedTransaction> categorizedTransaction = categorizeTransactionExactMatch(t, transaction);
        if (categorizedTransaction.isPresent()) {
            return categorizedTransaction.get();
        }

        // if that doesn't work, try a partial match
        // TODO: Includes account number in search - consider dropping req to re-use categorizations between checking/visa?
        categorizedTransaction = categorizeTransactionPartialMatch(t, transaction);
        if (categorizedTransaction.isPresent()) {
            return categorizedTransaction.get();
        }

        // there were no partial matches - just prompt for a new category name
        return chooseExistingCategoryOrAddNew(transaction);
    }

    private Optional<CategorizedTransaction> categorizeTransactionExactMatch(DatabaseTransaction t, Transaction transaction) throws SQLException {
        // first search is on the account number and entire description of the incoming transaction
        final List<CategorizedTransaction> categorizedTransactions = categorizedTransactionDao.findByDescriptionAndAccountNumber(t, transaction.getDescription(), transaction.getAccount().getAccountNumber());
        final List<Category> distinctCategories = categorizedTransactions.stream()
                .map(CategorizedTransaction::getCategory)
                .distinct()
                .collect(Collectors.toList());
        if (distinctCategories.isEmpty()) {
            // there were no exact matches for this transaction description and account number
            return Optional.empty();
        } else if (distinctCategories.size() == 1) {
            // all matching transactions share the same category - use it
            logger.info("New transaction description and account number exactly match that of {} existing transactions " +
                    "with category {}", categorizedTransactions.size(), distinctCategories.get(0));
            return Optional.of(new CategorizedTransaction(transaction, distinctCategories.get(0)));
        } else {
            // there is more than one potential category - prompt the user to choose
            logger.info("New transaction description and account number exactly match that of {} existing transactions " +
                    "with {} distinct categories", categorizedTransactions.size(), distinctCategories.size());
            return Optional.of(chooseCategoryFromListOrAddNew(transaction, distinctCategories));
        }
    }

    private Optional<CategorizedTransaction> categorizeTransactionPartialMatch(DatabaseTransaction t, Transaction transaction) throws SQLException {
        // try splitting the description up into tokens and partially matching on each
        final List<String> tokens = Arrays.stream(transaction.getDescription().split(" "))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        final List<CategorizedTransaction> categorizedTransactions = categorizedTransactionDao.findByDescriptionAndAccountNumber(t, tokens, transaction.getAccount().getAccountNumber());

        final List<Category> distinctCategories = categorizedTransactions.stream()
                .map(CategorizedTransaction::getCategory)
                .distinct()
                .collect(Collectors.toList());
        if (distinctCategories.isEmpty()) {
            // there were no partial matches
            return Optional.empty();
        } else if (distinctCategories.size() == 1) {
            // all matching transactions share the same category - use it
            logger.info("New transaction exactly matches description and account number of {} existing transactions " +
                    "with category {}", categorizedTransactions.size(), distinctCategories.get(0));
            return Optional.of(new CategorizedTransaction(transaction, distinctCategories.get(0)));
        } else {
            // there is more than one potential category - rank the choices by fuzzy string match and prompt the user to choose
            final List<BoundExtractedResult<CategorizedTransaction>> fuzzyMatches = FuzzySearch.extractAll(transaction.getDescription(), categorizedTransactions, Transaction::getDescription);

            // score each category based on the fuzzy match score of all associated transactions
            final Map<Category, Float> categoryScores = new HashMap<>();
            for (Category category : distinctCategories) {
                // find all matched transactions with this category
                final List<BoundExtractedResult<CategorizedTransaction>> categoryFuzzyMatches = fuzzyMatches.stream()
                        .filter(fm -> fm.getReferent().getCategory() == category)
                        .collect(Collectors.toList());
                final float score = categoryFuzzyMatches.stream().map(BoundExtractedResult::getScore).reduce(0, Integer::sum) / (float) categoryFuzzyMatches.size();
                categoryScores.put(category, score);
            }

            // get list of choices ranked by score descending for the user to choose from
            final List<Category> choices = categoryScores.entrySet().stream()
                .sorted((entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()) * -1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            return Optional.of(chooseCategoryFromListOrAddNew(transaction, choices));
        }
    }

    /**
     * Prompts the user to pick a category from the specified list, with the option to pick from the wider list of
     * all categories, or to add a new category. The chosen category will be associated with the specified transaction
     */
    private CategorizedTransaction chooseCategoryFromListOrAddNew(Transaction transaction, List<Category> choices) {
        Optional<Category> chosenCategory = cli.chooseCategoryOrChooseAnother(choices);
        return chosenCategory.map(category -> new CategorizedTransaction(transaction, category))
                .orElseGet(() -> chooseExistingCategoryOrAddNew(transaction));
    }

    /**
     * Prompts the user to choose a category from the list of all known categories, with the option to add a new category
     * if none suffice. The chosen category will be associated with the specified transaction
     */
    private CategorizedTransaction chooseExistingCategoryOrAddNew(Transaction transaction) {
        final List<Category> allCategories = categoryDao.select();
        if (allCategories.size() > 0) {
            // choose one from the list of all known categories
            final Optional<Category> chosenCategory = cli.chooseCategoryOrAddNew(allCategories);
            if (chosenCategory.isPresent()) {
                return new CategorizedTransaction(transaction, chosenCategory.get());
            }
        }

        // there are no known categories or the user chose to add a new category
        return promptForNewCategoryName(transaction);
    }

    /**
     * Prompts the user to choose a new category name, associates it with the specified transaction
     */
    private CategorizedTransaction promptForNewCategoryName(Transaction transaction) {
        final String newCategoryName = cli.promptForNewCategoryName();
        return new CategorizedTransaction(transaction, new Category(newCategoryName));
    }
}
