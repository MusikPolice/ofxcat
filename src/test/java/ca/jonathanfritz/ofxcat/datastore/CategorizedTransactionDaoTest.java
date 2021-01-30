package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

class CategorizedTransactionDaoTest extends AbstractDatabaseTest {

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
                .setAccountNumber(UUID.randomUUID().toString())
                .setBankId(UUID.randomUUID().toString())
                .build();
        account = accountDao.insert(account).get();

        // now we can create a CategorizedTransaction
        CategorizedTransaction categorizedTransaction = new CategorizedTransaction(Transaction.newBuilder()
                .setAccount(account)
                .setAmount(5.14f)
                .setDate(LocalDate.now())
                .setDescription("SAUCE BOSS")
                .setType(Transaction.TransactionType.DEBIT)
                .setBalance(21.58f)
                .build(), category);
        final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        categorizedTransaction = categorizedTransactionDao.insert(categorizedTransaction).get();

        // it should have been given an id
        Assertions.assertNotNull(categorizedTransaction.getId());

        // use the id to find it
        final CategorizedTransaction foundCategorizedTransaction = categorizedTransactionDao.select(categorizedTransaction.getId()).get();
        Assertions.assertEquals(categorizedTransaction, foundCategorizedTransaction);
    }

    @Test
    void selectGroupByCategoryTest() throws SQLException {
        // need two categories
        final CategoryDao categoryDao = new CategoryDao(connection);
        Category groceries = new Category("GROCERIES");
        groceries = categoryDao.insert(groceries).get();
        Category restaurants = new Category("RESTAURANTS");
        restaurants = categoryDao.insert(restaurants).get();

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        Account account = Account.newBuilder()
                .setName(UUID.randomUUID().toString())
                .setAccountType("CHECKING")
                .setAccountNumber(UUID.randomUUID().toString())
                .setBankId(UUID.randomUUID().toString())
                .build();
        account = accountDao.insert(account).get();

        // now we can create some CategorizedTransactions
        final CategorizedTransactionDao transactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        final LocalDate now = LocalDate.now();
        CategorizedTransaction quickieMart = new CategorizedTransaction(Transaction.newBuilder()
                .setAccount(account)
                .setAmount(10.72f)
                .setDate(now.minusDays(3))
                .setDescription("QUICKIE MART")
                .setType(Transaction.TransactionType.DEBIT)
                .setBalance(21.58f)
                .build(), groceries);
        quickieMart = transactionDao.insert(quickieMart).get();
        CategorizedTransaction tastyMart = new CategorizedTransaction(Transaction.newBuilder()
                .setAccount(account)
                .setAmount(15.31f)
                .setDate(now.minusDays(2))
                .setDescription("TASTY MART")
                .setType(Transaction.TransactionType.DEBIT)
                .setBalance(6.27f)
                .build(), groceries);
        tastyMart = transactionDao.insert(tastyMart).get();
        CategorizedTransaction monksCafe = new CategorizedTransaction(Transaction.newBuilder()
                .setAccount(account)
                .setAmount(2.50f)
                .setDate(now.minusDays(1))
                .setDescription("MONKS CAFE")
                .setType(Transaction.TransactionType.DEBIT)
                .setBalance(3.77f)
                .build(), restaurants);
        monksCafe = transactionDao.insert(monksCafe).get();

        // now we can get all transactions grouped by category
        Map<Category, List<CategorizedTransaction>> transactions = transactionDao.selectGroupByCategory(now.minusDays(4), now);
        Assertions.assertEquals(transactions.keySet(), Set.of(restaurants, groceries));
        Assertions.assertEquals(transactions.get(groceries), Arrays.asList(quickieMart, tastyMart));
        Assertions.assertEquals(transactions.get(restaurants), Collections.singletonList(monksCafe));

        // adjusting the date range excludes some transactions
        transactions = transactionDao.selectGroupByCategory(now.minusDays(2), now);
        Assertions.assertEquals(transactions.keySet(), Set.of(restaurants, groceries));
        Assertions.assertEquals(transactions.get(groceries), Collections.singletonList(tastyMart));
        Assertions.assertEquals(transactions.get(restaurants), Collections.singletonList(monksCafe));
    }

    @Test
    void isDuplicateSuccessTest() throws SQLException {
        // need a category
        final CategoryDao categoryDao = new CategoryDao(connection);
        Category category = new Category("EXCLAMATIONS OF DISBELIEF");
        category = categoryDao.insert(category).get();

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        Account account = Account.newBuilder()
                .setName(UUID.randomUUID().toString())
                .setAccountType("VISA")
                .setAccountNumber(UUID.randomUUID().toString())
                .setBankId(UUID.randomUUID().toString())
                .build();
        account = accountDao.insert(account).get();

        // we can use them to create a transaction
        final Transaction transaction = Transaction.newBuilder()
                .setAccount(account)
                .setAmount(6.17F)
                .setDate(LocalDate.now())
                .setDescription("HORSE FEATHERS")
                .setType(Transaction.TransactionType.DEBIT)
                .setBalance(34.21f)
                .build();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // the transaction should not a duplicate because it doesn't exist yet
            final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
            Assertions.assertFalse(categorizedTransactionDao.isDuplicate(t, transaction));

            // now we can insert the transaction, and assert that it is a duplicate since it exists
            final CategorizedTransaction categorizedTransaction = new CategorizedTransaction(transaction, category);
            categorizedTransactionDao.insert(t, categorizedTransaction).get();
            Assertions.assertTrue(categorizedTransactionDao.isDuplicate(t, transaction));
        }
    }

    @Test
    void findByDescriptionAndAccountIdSuccessTest() throws SQLException {
        // need a category
        final CategoryDao categoryDao = new CategoryDao(connection);
        Category category = new Category("EXCLAMATIONS OF DISBELIEF");
        category = categoryDao.insert(category).get();

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        Account account = Account.newBuilder()
                .setName(UUID.randomUUID().toString())
                .setAccountType("VISA")
                .setAccountNumber(UUID.randomUUID().toString())
                .setBankId(UUID.randomUUID().toString())
                .build();
        account = accountDao.insert(account).get();

        // we can use them to create a transaction
        final Transaction transaction = Transaction.newBuilder()
                .setAccount(account)
                .setAmount(6.17F)
                .setDate(LocalDate.now())
                .setDescription("CRIPES")
                .setType(Transaction.TransactionType.DEBIT)
                .setBalance(34.21f)
                .build();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            final Transaction target = Transaction.newBuilder()
                    .setAccount(account)
                    .setDescription(transaction.getDescription())
                    .build();

            // a similar transaction will not be found because we haven't inserted the target yet
            final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
            Assertions.assertTrue(categorizedTransactionDao.findByDescriptionAndAccountNumber(t, target).isEmpty());

            // now we can insert the transaction, and then get it back when querying with a similar transaction
            CategorizedTransaction categorizedTransaction = new CategorizedTransaction(transaction, category);
            categorizedTransaction = categorizedTransactionDao.insert(categorizedTransaction).get();
            final List<CategorizedTransaction> found = categorizedTransactionDao.findByDescriptionAndAccountNumber(t, target);
            Assertions.assertEquals(1, found.size());
            Assertions.assertEquals(categorizedTransaction, found.get(0));
        }
    }
}