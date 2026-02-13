package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import ca.jonathanfritz.ofxcat.matching.KeywordRulesConfig;
import ca.jonathanfritz.ofxcat.matching.TokenMatchingService;
import ca.jonathanfritz.ofxcat.matching.TokenNormalizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TransactionCategoryService {

    private final CategoryDao categoryDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TokenNormalizer tokenNormalizer;
    private final TokenMatchingService tokenMatchingService;
    private final KeywordRulesConfig keywordRulesConfig;
    private final CLI cli;

    private static final Logger logger = LogManager.getLogger(TransactionCategoryService.class);

    @Inject
    public TransactionCategoryService(
            CategoryDao categoryDao,
            CategorizedTransactionDao categorizedTransactionDao,
            TokenNormalizer tokenNormalizer,
            TokenMatchingService tokenMatchingService,
            KeywordRulesConfig keywordRulesConfig,
            CLI cli
    ) {
        this.categoryDao = categoryDao;
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.tokenNormalizer = tokenNormalizer;
        this.tokenMatchingService = tokenMatchingService;
        this.keywordRulesConfig = keywordRulesConfig;
        this.cli = cli;
    }

    /**
     * Categorizes a transaction using a multi-step matching strategy:
     * 1. Keyword rules: Check if normalized tokens match any configured keyword rules
     * 2. Exact match: Find transactions with identical description
     * 3. Token match: Find similar transactions using token-based matching
     * 4. Manual: Prompt user to choose or create a category
     */
    public CategorizedTransaction categorizeTransaction(DatabaseTransaction t, Transaction transaction) throws SQLException {
        // Step 1: Try keyword rules matching first (auto-categorization based on rules)
        if (keywordRulesConfig.isAutoCategorizeEnabled()) {
            Optional<CategorizedTransaction> categorizedTransaction = categorizeTransactionByKeywordRules(transaction);
            if (categorizedTransaction.isPresent()) {
                return categorizedTransaction.get();
            }
        }

        // Step 2: Try exact match next (respecting existing choices that user made in the past)
        Optional<CategorizedTransaction> categorizedTransaction = categorizeTransactionExactMatch(t, transaction);
        if (categorizedTransaction.isPresent()) {
            return categorizedTransaction.get();
        }

        // Step 3: Try token-based matching (finding similar transactions)
        categorizedTransaction = categorizeTransactionByTokenMatch(transaction);
        if (categorizedTransaction.isPresent()) {
            return categorizedTransaction.get();
        }

        // Step 4: No matches - prompt user to choose or create a category
        return chooseExistingCategoryOrAddNew(transaction);
    }

    /**
     * Attempts to categorize a transaction using keyword rules.
     * Normalizes the transaction description and checks against configured rules.
     * TODO: what if multiple rules match? right now we take the first, but could prompt user to choose
     */
    private Optional<CategorizedTransaction> categorizeTransactionByKeywordRules(Transaction transaction) {
        Set<String> tokens = tokenNormalizer.normalize(transaction.getDescription());
        Optional<String> matchedCategoryName = keywordRulesConfig.findMatchingCategory(tokens);

        if (matchedCategoryName.isEmpty()) {
            logger.debug("No keyword rule matches for tokens: {}", tokens);
            return Optional.empty();
        }

        String categoryName = matchedCategoryName.get();
        logger.info("Keyword rule matched: {} -> {}", tokens, categoryName);

        // Get or create the category in the database
        Optional<Category> category = categoryDao.getOrCreate(categoryName);
        if (category.isEmpty()) {
            logger.error("Failed to get or create category: {}", categoryName);
            return Optional.empty();
        }

        return Optional.of(new CategorizedTransaction(transaction, category.get()));
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

    /**
     * Attempts to categorize a transaction using token-based matching.
     * Finds similar transactions by comparing normalized tokens and their overlap ratio.
     */
    private Optional<CategorizedTransaction> categorizeTransactionByTokenMatch(Transaction transaction) {
        // Use TokenMatchingService to find matching categories
        List<TokenMatchingService.CategoryMatch> matches = tokenMatchingService.findMatchingCategoriesForDescription(
                transaction.getDescription()
        );

        if (matches.isEmpty()) {
            logger.info("No token-based matches found for transaction description");
            return Optional.empty();
        }

        logger.info("Found {} token-based category matches for transaction", matches.size());

        // Extract the top categories (up to 5) ranked by overlap ratio
        List<Category> choices = matches.stream()
                .map(TokenMatchingService.CategoryMatch::category)
                .limit(5)
                .collect(Collectors.toList());

        // If only one category matched, auto-categorize with it
        if (choices.size() == 1) {
            Category category = choices.get(0);
            logger.info("Single token match found, auto-categorizing as: {}", category.getName());
            return Optional.of(new CategorizedTransaction(transaction, category));
        }

        // Multiple matches - prompt user to choose
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
