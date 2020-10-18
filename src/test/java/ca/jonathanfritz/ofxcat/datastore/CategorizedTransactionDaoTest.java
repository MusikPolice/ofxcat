package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.transactions.Account;
import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

class CategorizedTransactionDaoTest extends AbstractDatabaseTest {

    @AfterEach
    void cleanup() {
        // drop all tables and re-init the schema after each test to avoid conflicts
        cleanDatabase();
    }

    @Test
    void insertSelectSuccessTest() {
        // need a category
        final CategoryDao categoryDao = new CategoryDao(connection);
        Category category = new Category("HORSERADISH");
        category = categoryDao.insert(category).get();

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        Account account = Account.newBuilder()
                .setName(UUID.randomUUID().toString())
                .setAccountType("VISA")
                .setAccountId(UUID.randomUUID().toString())
                .setBankId(UUID.randomUUID().toString())
                .build();
        account = accountDao.insert(account).get();

        // now we can create a CategorizedTransaction
        CategorizedTransaction categorizedTransaction = new CategorizedTransaction(Transaction.newBuilder()
                .setAccount(account)
                .setAmount(5.14F)
                .setDate(LocalDate.now())
                .setDescription("SAUCE BOSS")
                .setType(Transaction.TransactionType.DEBIT)
                .build(), category);
        final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        categorizedTransaction = categorizedTransactionDao.insert(categorizedTransaction).get();

        // it should have been given an id
        Assertions.assertNotNull(categorizedTransaction.getId());

        // use the id to find it
        final CategorizedTransaction foundCategorizedTransaction = categorizedTransactionDao.select(categorizedTransaction.getId()).get();
        Assertions.assertEquals(categorizedTransaction, foundCategorizedTransaction);
    }
}