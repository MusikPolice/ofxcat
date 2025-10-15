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
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
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

    // TODO: why does LCBO/RAO not auto-categorize?
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
        return chooseExistingCategoryOrAddNew(transaction);
    }

    private Optional<CategorizedTransaction> categorizeTransactionExactMatch(DatabaseTransaction t, Transaction transaction) throws SQLException {
        // first search is on the entire description of the incoming transaction
        final List<CategorizedTransaction> categorizedTransactions = categorizedTransactionDao.findByDescription(t, transaction.getDescription());
        final List<Category> distinctCategories = categorizedTransactions.stream()
                .map(CategorizedTransaction::getCategory)
                .filter(c -> !c.equals(Category.UNKNOWN)) // do not automatically categorize transactions as UNKNOWN
                .distinct()
                .collect(Collectors.toList());

        if (distinctCategories.isEmpty()) {
            // there were no exact matches for this transaction description
            logger.info("There are no existing transactions that exactly match the description of the specified Transaction");
            return Optional.empty();
        } else if (distinctCategories.size() == 1) {
            // all matching transactions share the same category - use it
            logger.info("New transaction description exactly matches that of {} existing transactions " +
                    "with category {}", categorizedTransactions.size(), distinctCategories.get(0));
            return Optional.of(new CategorizedTransaction(transaction, distinctCategories.get(0)));
        } else {
            // there is more than one potential category - prompt the user to choose
            logger.info("New transaction description exactly matches that of {} existing transactions " +
                    "with {} distinct categories", categorizedTransactions.size(), distinctCategories.size());
            return chooseCategoryFromList(transaction, distinctCategories);
        }
    }

    private Optional<CategorizedTransaction> categorizeTransactionPartialMatch(DatabaseTransaction t, Transaction transaction) throws SQLException {
        // try splitting the description up into tokens and partially matching on each
        final List<String> tokens = Arrays.stream(transaction.getDescription().split(" "))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .filter(s -> !s.matches("^#?\\d*$")) // drop tokens that are entirely numeric or a # sign followed by a number - usually franchise store numbers
                .filter(s -> !s.matches("^[0-9\\-]*$")) // drop tokens that look like phone numbers
                .distinct()
                .collect(Collectors.toList());
        final List<CategorizedTransaction> categorizedTransactions = categorizedTransactionDao.findByDescription(t, tokens);

        // count the number of categories that we found
        final List<Category> distinctCategories = categorizedTransactions.stream()
                .map(CategorizedTransaction::getCategory)
                .filter(c -> !c.equals(Category.UNKNOWN)) // do not automatically categorize transactions as UNKNOWN
                .distinct()
                .collect(Collectors.toList());

        // short circuit if there were no partial matches
        if (distinctCategories.isEmpty()) {
            logger.info("There are no existing transactions that partially match the description of the specified Transaction");
            return Optional.empty();
        }
        logger.info("New transaction description partially matches that of {} existing transactions " +
                "with categories {}", categorizedTransactions.size(), distinctCategories);

        // rank the choices by fuzzy string match
        final List<BoundExtractedResult<CategorizedTransaction>> fuzzyMatches = FuzzySearch.extractAll(
                transaction.getDescription(),
                categorizedTransactions,
                Transaction::getDescription
        );

        // score each category based on the fuzzy match score of all associated transactions
        final Map<Category, Float> categoryScores = new HashMap<>();
        for (Category category : distinctCategories) {
            // find all matched transactions with this category
            final List<BoundExtractedResult<CategorizedTransaction>> categoryFuzzyMatches = fuzzyMatches.stream()
                    .filter(fm -> fm.getReferent().getCategory() == category)
                    .toList();

            // compute the average score for the category
            final Integer sum = categoryFuzzyMatches.stream()
                    .map(BoundExtractedResult::getScore)
                    .reduce(0, Integer::sum);
            final float average = sum / (float) categoryFuzzyMatches.size();

            // only keep categories with a score above 60%
            // TODO: should this threshold be configurable?
            if (average >= 60) {
                categoryScores.put(category, average);
            }
        }

        // return the top five choices ranked by score descending for the user to choose from
        final List<Category> choices = categoryScores.entrySet().stream()
                .sorted((entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()) * -1)
                .map(Map.Entry::getKey)
                .limit(5)
                .collect(Collectors.toList());

        // edge case - if there are no choices exceeding the fuzzy score threshold, return all found categories
        if (choices.isEmpty()) {
            return chooseCategoryFromList(transaction, distinctCategories);
        }
        return chooseCategoryFromList(transaction, choices);
    }

    /**
     * Prompts the user to pick a category from the specified list. If the user chooses a category, it will be
     * associated with the specified transaction. Otherwise, returns Optional.empty()
     */
    private Optional<CategorizedTransaction> chooseCategoryFromList(Transaction transaction, List<Category> choices) {
        logger.info("Prompting user to categorize new transaction as one of {}", choices);
        final Optional<Category> chosenCategory = cli.chooseCategoryOrChooseAnother(choices);
        return Optional.of(chosenCategory.map(category -> {
                    logger.info("User chose category {}", category);
                    return new CategorizedTransaction(transaction, category);
                })).orElseGet(() -> {
                    logger.info("User declined to choose one of the presented categories");
                    return Optional.empty();
                });
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
        return promptForNewCategoryName(transaction, allCategories);
    }

    /**
     * Prompts the user to choose a new category name, associates it with the specified transaction
     */
    private CategorizedTransaction promptForNewCategoryName(Transaction transaction, List<Category> allCategories) {
        final String newCategoryName = cli.promptForNewCategoryName(allCategories);
        return new CategorizedTransaction(transaction, new Category(newCategoryName));
    }
}
