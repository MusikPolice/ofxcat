package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionCategoryServiceTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;

    public static final String FRONTYS_MEAT_MARKET = "Fronty's Meat Market";

    private Account testAccount;

    TransactionCategoryServiceTest() {
        categoryDao = injector.getInstance(CategoryDao.class);
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        transactionTokenDao = new TransactionTokenDao();
    }

    @BeforeEach
    void populateTestData() throws SQLException {
        testAccount = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // two similarly named transactions are linked to different categories
            insertTransaction(t, testAccount, "Beats 'R Us", "Music");
            insertTransaction(t, testAccount, "Fleets 'R Us", "Vehicles");

            // one of the transaction descriptions is linked to two categories
            insertTransaction(t, testAccount, "Sweets 'R Us", "Restaurants");
            insertTransaction(t, testAccount, "Sweets 'R Us", "Groceries");

            // there is a transaction in the unknown category
            insertTransaction(t, testAccount, FRONTYS_MEAT_MARKET, Category.UNKNOWN);
        }
    }

    /**
     * Ensures that our automatic transaction categorizer will not assign the default UNKNOWN category to any new
     * transaction, even if its description matches an existing transaction in that category
     */
    @Test
    public void categorizeTransactionDoesNotMatchUnknownTest() throws SQLException {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // when the cli is prompted to choose a category, it will return the first category that isn't UNKNOWN
            final List<Category> categories = categoryDao.select();
            final Category expectedCategory = categories.stream()
                    .filter(c -> c != Category.UNKNOWN)
                    .findFirst()
                    .orElse(null);
            final CLI spyCli = new SpyCli(expectedCategory);

            final TransactionCategoryService testFixture =
                    createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);

            // try to categorize a transaction with a description that exactly matches that of an existing transaction
            // that was previously categorized as UNKNOWN
            Transaction transaction = createRandomTransaction(testAccount, FRONTYS_MEAT_MARKET);
            CategorizedTransaction categorizedTransaction = testFixture.categorizeTransaction(t, transaction);

            Assertions.assertNotEquals(categorizedTransaction.getCategory(), Category.UNKNOWN);
            Assertions.assertEquals(categorizedTransaction.getCategory(), expectedCategory);
            Assertions.assertEquals(categorizedTransaction.getTransaction(), transaction);

            // try to categorize a transaction with a description that partially matches that of an existing transaction
            // that was previously categorized as UNKNOWN
            transaction = createRandomTransaction(testAccount, "Fronty's Flea Market");
            categorizedTransaction = testFixture.categorizeTransaction(t, transaction);

            Assertions.assertNotEquals(categorizedTransaction.getCategory(), Category.UNKNOWN);
            Assertions.assertEquals(categorizedTransaction.getCategory(), expectedCategory);
            Assertions.assertEquals(categorizedTransaction.getTransaction(), transaction);
        }
    }

    @Test
    public void categorizeTransactionExactMatchTest() throws SQLException {
        // create one previously categorized transaction
        final String description = "Hello World";
        final Transaction existingTransaction = createRandomTransaction(testAccount, description);
        final Category existingCategory =
                categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final CategorizedTransaction expected = categorizedTransactionDao
                .insert(new CategorizedTransaction(existingTransaction, existingCategory))
                .orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create another transaction with the same description
            final Transaction newTransaction = createRandomTransaction(testAccount, description);

            // attempt to automatically categorize it - we should get a direct match with the category created above
            final TransactionCategoryService testFixture =
                    createTransactionCategoryService(categoryDao, categorizedTransactionDao, null);
            final CategorizedTransaction actual = testFixture.categorizeTransaction(t, newTransaction);

            // actual should have the same category as expected
            Assertions.assertEquals(expected.getCategory(), actual.getCategory());
            Assertions.assertEquals(existingCategory, actual.getCategory());
        }
    }

    @Test
    public void categorizeTransactionExactMatchDifferentAccountsTest() throws SQLException {
        // create one previously categorized transaction
        final String description = "Hello World";
        final Account otherAccount =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Transaction existingTransaction = createRandomTransaction(otherAccount, description);
        final Category existingCategory =
                categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final CategorizedTransaction expected = categorizedTransactionDao
                .insert(new CategorizedTransaction(existingTransaction, existingCategory))
                .orElse(null);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create another transaction that has the same description, but in a different account from the first
            final Transaction newTransaction = createRandomTransaction(testAccount, description);

            // attempt to automatically categorize it - we should get a direct match with the category created above
            final TransactionCategoryService testFixture =
                    createTransactionCategoryService(categoryDao, categorizedTransactionDao, null);
            final CategorizedTransaction actual = testFixture.categorizeTransaction(t, newTransaction);

            // actual should have the same category as expected
            Assertions.assertEquals(expected.getCategory(), actual.getCategory());
            Assertions.assertEquals(existingCategory, actual.getCategory());
        }
    }

    @Test
    public void categorizeTransactionPartialMatchTest() throws SQLException {
        // create one previously categorized transaction with tokens stored
        final Category existingCategory =
                categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final Transaction existingTransaction = createRandomTransaction(testAccount, "Hello World");
        final CategorizedTransaction expected;

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            expected = categorizedTransactionDao
                    .insert(t, new CategorizedTransaction(existingTransaction, existingCategory))
                    .orElseThrow(() -> new RuntimeException("Failed to insert transaction"));
            // Store tokens for token-based matching
            Set<String> tokens = tokenNormalizer.normalize(existingTransaction.getDescription());
            transactionTokenDao.insertTokens(t, expected.getId(), tokens);
        }

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create another transaction with a description that shares a word in common with that of an existing
            // transaction
            final Transaction newTransaction = createRandomTransaction(testAccount, "Boy Meets World");

            // attempt to automatically categorize it - the CLI should be prompted to choose the category based on the
            // partial match
            final SpyCli spyCli = new SpyCli(existingCategory);
            final TransactionCategoryService testFixture =
                    createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
            final CategorizedTransaction actual = testFixture.categorizeTransaction(t, newTransaction);

            // actual should have the same category as expected
            Assertions.assertEquals(expected.getCategory(), actual.getCategory());
            Assertions.assertEquals(existingCategory, actual.getCategory());

            // With token-based matching, a single strong match auto-categorizes without prompting
            // (different from old fuzzy matching which always prompted)
        }
    }

    @Test
    public void categorizeTransactionPartialMatchDifferentAccountsTest() throws SQLException {
        // create one previously categorized transaction with tokens stored
        final Account otherAccount =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Category existingCategory =
                categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final Transaction existingTransaction = createRandomTransaction(otherAccount, "Hello World");
        final CategorizedTransaction expected;

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            expected = categorizedTransactionDao
                    .insert(t, new CategorizedTransaction(existingTransaction, existingCategory))
                    .orElseThrow(() -> new RuntimeException("Failed to insert transaction"));
            // Store tokens for token-based matching
            Set<String> tokens = tokenNormalizer.normalize(existingTransaction.getDescription());
            transactionTokenDao.insertTokens(t, expected.getId(), tokens);
        }

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create another transaction with a description that shares a word in common with that of an existing
            // transaction, but is in a different account
            final Transaction newTransaction = createRandomTransaction(testAccount, "Boy Meets World");

            // attempt to automatically categorize it - the CLI should be prompted to choose the category based on the
            // partial match
            final SpyCli spyCli = new SpyCli(existingCategory);
            final TransactionCategoryService testFixture =
                    createTransactionCategoryService(categoryDao, categorizedTransactionDao, spyCli);
            final CategorizedTransaction actual = testFixture.categorizeTransaction(t, newTransaction);

            // actual should have the same category as expected
            Assertions.assertEquals(expected.getCategory(), actual.getCategory());
            Assertions.assertEquals(existingCategory, actual.getCategory());

            // With token-based matching, a single strong match auto-categorizes without prompting
            // (different from old fuzzy matching which always prompted)
        }
    }

    // inserts a transaction, associating it with a new category
    private void insertTransaction(DatabaseTransaction t, Account account, String description, String categoryName)
            throws SQLException {
        final Transaction transaction = createRandomTransaction(account, description);
        Category category = categoryDao.getOrCreate(t, categoryName).orElse(null);
        CategorizedTransaction ct = new CategorizedTransaction(transaction, category);
        CategorizedTransaction inserted =
                categorizedTransactionDao.insert(t, ct).orElse(null);
        // Store tokens for token-based matching
        if (inserted != null && !Category.UNKNOWN.equals(category)) {
            Set<String> tokens = tokenNormalizer.normalize(description);
            if (!tokens.isEmpty()) {
                transactionTokenDao.insertTokens(t, inserted.getId(), tokens);
            }
        }
    }

    // inserts a transaction, associating it with an existing category
    private void insertTransaction(DatabaseTransaction t, Account account, String description, Category category)
            throws SQLException {
        final Transaction transaction = createRandomTransaction(account, description);
        CategorizedTransaction ct = new CategorizedTransaction(transaction, category);
        CategorizedTransaction inserted =
                categorizedTransactionDao.insert(t, ct).orElse(null);
        // Don't store tokens for UNKNOWN category
        if (inserted != null && !Category.UNKNOWN.equals(category)) {
            Set<String> tokens = tokenNormalizer.normalize(description);
            if (!tokens.isEmpty()) {
                transactionTokenDao.insertTokens(t, inserted.getId(), tokens);
            }
        }
    }

    private Transaction createRandomTransaction(Account account, String description) {
        return Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDescription(description)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
    }

    private static class SpyCli extends CLI {

        private final Category category;
        private final List<Category> capturedCategories = new ArrayList<>();

        SpyCli(Category category) {
            super(null, null);
            this.category = category;
        }

        @Override
        public Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            return Optional.of(category);
        }

        @Override
        public Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
            capturedCategories.addAll(categories);
            return Optional.of(category);
        }

        public List<Category> getCapturedCategories() {
            return Collections.unmodifiableList(capturedCategories);
        }
    }
}
