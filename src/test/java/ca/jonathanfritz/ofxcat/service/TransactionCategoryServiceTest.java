package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.DescriptionCategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

class TransactionCategoryServiceTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final DescriptionCategoryDao descriptionCategoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;

    public static final String FRONTYS_MEAT_MARKET = "Fronty's Meat Market";

    private Account testAccount;

    public TransactionCategoryServiceTest() {
        categoryDao = injector.getInstance(CategoryDao.class);
        descriptionCategoryDao = injector.getInstance(DescriptionCategoryDao.class);
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
    }

    @BeforeEach
    void populateTestData() {
        final Account account = TestUtils.createRandomAccount();
        testAccount = accountDao.insert(account).get();

        // used to populate data here, but not a global variable because we want to use a custom version as a test fixture
        final TransactionCategoryService transactionCategoryService = new TransactionCategoryService(categoryDao, descriptionCategoryDao, categorizedTransactionDao, connection, null);

        // two similarly named transactions are linked to different categories
        insertTransaction(testAccount, "Beats 'R Us", "Music", transactionCategoryService);
        insertTransaction(testAccount, "Fleets 'R Us", "Vehicles", transactionCategoryService);

        // one of the transaction descriptions is linked to two categories
        insertTransaction(testAccount, "Sweets 'R Us", "Restaurants", transactionCategoryService);
        insertTransaction(testAccount, "Sweets 'R Us", "Groceries", transactionCategoryService);

        // there is a transaction in the unknown category
        insertTransaction(testAccount, FRONTYS_MEAT_MARKET, Category.UNKNOWN, transactionCategoryService);
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
                    .get();
            final CLI spyCli = new SpyCli(expectedCategory);

            final TransactionCategoryService testFixture = new TransactionCategoryService(categoryDao, descriptionCategoryDao, categorizedTransactionDao, connection, spyCli);

            // try to categorize a transaction with a description that exactly matches that of an existing transaction
            // that was previously categorized as UNKNOWN
            Transaction transaction = createRandomTransaction(testAccount, FRONTYS_MEAT_MARKET);
            CategorizedTransaction categorizedTransaction =
                    testFixture.categorizeTransaction(t, transaction);

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
        final Category existingCategory = categoryDao.insert(TestUtils.createRandomCategory()).get();
        final CategorizedTransaction expected =
                categorizedTransactionDao.insert(new CategorizedTransaction(existingTransaction, existingCategory)).get();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create another transaction with the same description
            final Transaction newTransaction = createRandomTransaction(testAccount, description);

            // attempt to automatically categorize it - we should get a direct match with the category created above
            final TransactionCategoryService testFixture = new TransactionCategoryService(categoryDao, descriptionCategoryDao, categorizedTransactionDao, connection, null);
            final CategorizedTransaction actual = testFixture.categorizeTransaction(t, newTransaction);

            // actual should have the same category as expected
            Assertions.assertEquals(expected.getCategory(), actual.getCategory());
            Assertions.assertEquals(existingCategory, actual.getCategory());
        }
    }

    @Test
    public void categorizeTransactionPartialMatchTest() throws SQLException {
        // create one previously categorized transaction
        final Transaction existingTransaction = createRandomTransaction(testAccount, "Hello World");
        final Category existingCategory = categoryDao.insert(TestUtils.createRandomCategory()).get();
        final CategorizedTransaction expected =
                categorizedTransactionDao.insert(new CategorizedTransaction(existingTransaction, existingCategory)).get();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create another transaction with a description that shares a word in common with that of an existing transaction
            final Transaction newTransaction = createRandomTransaction(testAccount, "Boy Meets World");

            // attempt to automatically categorize it - the CLI should be prompted to choose the category based on the partial match
            final SpyCli spyCli = new SpyCli(existingCategory);
            final TransactionCategoryService testFixture = new TransactionCategoryService(categoryDao, descriptionCategoryDao, categorizedTransactionDao, connection, spyCli);
            final CategorizedTransaction actual = testFixture.categorizeTransaction(t, newTransaction);

            // actual should have the same category as expected
            Assertions.assertEquals(expected.getCategory(), actual.getCategory());
            Assertions.assertEquals(existingCategory, actual.getCategory());

            // and the CLI should have been prompted to choose the existing category
            Assertions.assertEquals(List.of(existingCategory), spyCli.capturedCategories);
        }
    }

    // inserts a transaction, associating it with a new category
    private void insertTransaction(Account account, String description, String categoryName, TransactionCategoryService transactionCategoryService) {
        final Transaction transaction = createRandomTransaction(account, description);
        final Category category = new Category(categoryName);
        final CategorizedTransaction categorizedTransaction = transactionCategoryService.put(transaction, category);
        categorizedTransactionDao.insert(categorizedTransaction).get();
    }

    // inserts a transaction, associating it with an existing category
    private void insertTransaction(Account account, String description, Category category, TransactionCategoryService transactionCategoryService) {
        final Transaction transaction = createRandomTransaction(account, description);
        final CategorizedTransaction categorizedTransaction = transactionCategoryService.put(transaction, category);
        categorizedTransactionDao.insert(categorizedTransaction).get();
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

        public SpyCli(Category category) {
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