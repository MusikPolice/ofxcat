package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class TransactionCategoryServiceTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionCategoryService transactionCategoryService;

    public static final String FRONTYS_MEAT_MARKET = "Fronty's Meat Market";

    private Account testAccount;
    private final CLI mockCli = Mockito.mock(CLI.class);

    public TransactionCategoryServiceTest() {
        categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);
        accountDao = new AccountDao(connection);
        categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        transactionCategoryService = new TransactionCategoryService(categoryDao, descriptionCategoryDao, categorizedTransactionDao, connection, mockCli);
    }

    @BeforeEach
    void populateTestData() {
        final Account account = Account.newBuilder()
                .setAccountNumber(UUID.randomUUID().toString())
                .setName(UUID.randomUUID().toString())
                .build();
        testAccount = accountDao.insert(account).get();

        // two similarly named transactions are linked to different categories
        insertTransaction(testAccount, "Beats 'R Us", "Music");
        insertTransaction(testAccount, "Fleets 'R Us", "Vehicles");

        // one of the transaction descriptions is linked to two categories
        insertTransaction(testAccount, "Sweets 'R Us", "Restaurants");
        insertTransaction(testAccount, "Sweets 'R Us", "Groceries");

        // there is a transaction in the unknown category
        insertTransaction(testAccount, FRONTYS_MEAT_MARKET, Category.UNKNOWN);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(mockCli);
    }

    /**
     * Ensures that our automatic transaction categorizer will not assign the default UNKNOWN category to any new
     * transaction, even if its description matches an existing transaction in that category
     */
    @Test
    public void categorizeTransactionDoesNotMatchUnknown() throws SQLException {
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // when the cli is prompted to choose a category, it will return the first category that isn't UNKNOWN
            final List<Category> categories = categoryDao.select();
            final Optional<Category> categoryOptional = categories.stream()
                    .filter(c -> c != Category.UNKNOWN)
                    .findFirst();
            Mockito.when(mockCli.chooseCategoryOrAddNew(categories))
                    .thenReturn(categoryOptional);

            // try to categorize a transaction with a description that exactly matches that of an existing transaction
            // that was previously categorized as UNKNOWN
            Transaction transaction = createRandomTransaction(testAccount, FRONTYS_MEAT_MARKET);
            CategorizedTransaction categorizedTransaction =
                    transactionCategoryService.categorizeTransaction(t, transaction);

            Assertions.assertNotEquals(categorizedTransaction.getCategory(), Category.UNKNOWN);
            Assertions.assertEquals(categorizedTransaction.getCategory(), categoryOptional.get());
            Assertions.assertEquals(categorizedTransaction.getTransaction(), transaction);

            // try to categorize a transaction with a description that partially matches that of an existing transaction
            // that was previously categorized as UNKNOWN
            transaction = createRandomTransaction(testAccount, "Fronty's Flea Market");
            categorizedTransaction = transactionCategoryService.categorizeTransaction(t, transaction);

            Assertions.assertNotEquals(categorizedTransaction.getCategory(), Category.UNKNOWN);
            Assertions.assertEquals(categorizedTransaction.getCategory(), categoryOptional.get());
            Assertions.assertEquals(categorizedTransaction.getTransaction(), transaction);
        }
    }

    // inserts a transaction, associating it with a new category
    private void insertTransaction(Account account, String description, String categoryName) {
        final Transaction transaction = createRandomTransaction(account, description);
        final Category category = new Category(categoryName);
        final CategorizedTransaction categorizedTransaction = transactionCategoryService.put(transaction, category);
        categorizedTransactionDao.insert(categorizedTransaction).get();
    }

    // inserts a transaction, associating it with an existing category
    private void insertTransaction(Account account, String description, Category category) {
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
}