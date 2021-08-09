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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class TransactionCategoryService {

    private final CategoryDao categoryDao;
    private final DescriptionCategoryDao descriptionCategoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final Connection connection;
    private final CLI cli;

    private static final Logger logger = LoggerFactory.getLogger(TransactionCategoryService.class);

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

    public CategorizedTransaction categorizeTransaction(DatabaseTransaction t, Transaction transaction) throws SQLException {
        // try an exact match first
        Optional<CategorizedTransaction> categorizedTransaction = categorizeTransactionExactMatch(t, transaction);
        if (categorizedTransaction.isPresent()) {
            return categorizedTransaction.get();
        }

        // if that doesn't work, try a partial match
        categorizedTransaction = categorizeTransactionPartialMatch(t, transaction);
        if (categorizedTransaction.isPresent()) {
            return categorizedTransaction.get();
        }

        // there were no partial matches - just prompt for a new category name
        return promptForNewCategoryName(transaction);
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
            return chooseCategoryOrAddNewCategory(transaction, distinctCategories);
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

            return chooseCategoryOrAddNewCategory(transaction, choices);
        }
    }

    private CategorizedTransaction promptForNewCategoryName(Transaction transaction) {
        final String newCategoryName = cli.promptForNewCategoryName();
        return new CategorizedTransaction(transaction, new Category(newCategoryName));
    }

    private Optional<CategorizedTransaction> chooseCategoryOrAddNewCategory(Transaction transaction, List<Category> choices) {
        Optional<Category> chosenCategory = cli.chooseCategoryOrChooseAnother(choices);
        if (chosenCategory.isPresent()) {
            return Optional.of(new CategorizedTransaction(transaction, chosenCategory.get()));
        } else {
            // the user doesn't like any of our matches - allow them to pick from the full set of categories
            final List<Category> allCategories = categoryDao.select();
            chosenCategory = cli.chooseCategoryOrAddNew(allCategories);
            // the user doesn't like any of the existing categories - allow them to add a new one
            return chosenCategory.map(category -> new CategorizedTransaction(transaction, category))
                    .or(() -> Optional.of(promptForNewCategoryName(transaction)));
        }
    }
}
