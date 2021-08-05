package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.DescriptionCategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDate;
import java.util.UUID;

class TransactionCategoryServiceTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionCategoryService transactionCategoryService;

    private Account testAccount;

    public TransactionCategoryServiceTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);
        accountDao = new AccountDao(connection);
        categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        transactionCategoryService = new TransactionCategoryService(categoryDao, descriptionCategoryDao, categorizedTransactionDao, connection, null);
    }

    @BeforeEach
    void populateTestData() {
        final Account account = Account.newBuilder()
                .setAccountNumber(UUID.randomUUID().toString())
                .setName(UUID.randomUUID().toString())
                .build();
        testAccount = accountDao.insert(account).get();

        insertTransaction(testAccount, "Beats 'R Us", "Music");
        insertTransaction(testAccount, "Fleets 'R Us", "Vehicles");
        insertTransaction(testAccount, "Toys 'R Us", "Shopping");
        insertTransaction(testAccount, "Boys 'R Us", "Dating");
        insertTransaction(testAccount, "Kois 'R Us", "Pets");

        // one of the descriptions is linked to two categories
        insertTransaction(testAccount, "Meats 'R Us", "Restaurants");
        insertTransaction(testAccount, "Meats 'R Us", "Groceries");
    }

    private void insertTransaction(Account account, String description, String categoryName) {
        final Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDescription(description)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        final Category category = new Category(categoryName);
        final CategorizedTransaction categorizedTransaction = transactionCategoryService.put(transaction, category);
        categorizedTransactionDao.insert(categorizedTransaction).get();
    }

    // TODO

}