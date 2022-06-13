package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
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
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // now we can create a CategorizedTransaction
        final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        final CategorizedTransaction expected = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElse(null);

        // it should have been given an id
        Assertions.assertNotNull(expected.getId());

        // use the id to find it
        final CategorizedTransaction actual = categorizedTransactionDao.select(expected.getId()).orElse(null);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void selectGroupByCategoryTest() {
        // need two categories
        final CategoryDao categoryDao = new CategoryDao(connection);
        Category groceries = new Category("GROCERIES");
        groceries = categoryDao.insert(groceries).orElse(null);
        Category restaurants = new Category("RESTAURANTS");
        restaurants = categoryDao.insert(restaurants).orElse(null);

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // now we can create some CategorizedTransactions
        final CategorizedTransactionDao transactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        final LocalDate now = LocalDate.now();
        final CategorizedTransaction threeDaysAgo = transactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, now.minusDays(3)), groceries)
        ).orElse(null);
        final CategorizedTransaction twoDaysAgo = transactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, now.minusDays(2)), groceries)
        ).orElse(null);
        final CategorizedTransaction oneDayAgo = transactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, now.minusDays(1)), restaurants)
        ).orElse(null);

        // now we can get all transactions grouped by category
        Map<Category, List<CategorizedTransaction>> transactions = transactionDao.selectGroupByCategory(now.minusDays(4), now);
        Assertions.assertEquals(Set.of(restaurants, groceries), transactions.keySet());
        Assertions.assertEquals(Arrays.asList(threeDaysAgo, twoDaysAgo), transactions.get(groceries));
        Assertions.assertEquals(Collections.singletonList(oneDayAgo), transactions.get(restaurants));

        // adjusting the date range excludes some transactions
        transactions = transactionDao.selectGroupByCategory(now.minusDays(2), now);
        Assertions.assertEquals(Set.of(restaurants, groceries), transactions.keySet());
        Assertions.assertEquals(Collections.singletonList(twoDaysAgo), transactions.get(groceries));
        Assertions.assertEquals(Collections.singletonList(oneDayAgo), transactions.get(restaurants));
    }

    @Test
    void isDuplicateSuccessTest() throws SQLException {
        // need a category
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // we can use them to create a transaction
        final Transaction transaction = TestUtils.createRandomTransaction(account);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // the transaction should not a duplicate because it doesn't exist yet
            final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
            Assertions.assertFalse(categorizedTransactionDao.isDuplicate(t, transaction));

            // now we can insert the transaction, and assert that it is a duplicate since it exists
            final CategorizedTransaction categorizedTransaction = new CategorizedTransaction(transaction, category);
            categorizedTransactionDao.insert(t, categorizedTransaction);
            Assertions.assertTrue(categorizedTransactionDao.isDuplicate(t, transaction));
        }
    }

    @Test
    public void findByDescriptionExactTest() throws SQLException {
        // need a category
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // create a transaction
        final Transaction expected = TestUtils.createRandomTransaction(account);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create the transaction
            final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
            categorizedTransactionDao.insert(new CategorizedTransaction(expected, category));

            // search for the transaction using its exact description string
            final List<CategorizedTransaction> actual = categorizedTransactionDao.findByDescription(t, expected.getDescription());

            //  expect 1 match because the two transactions share a description
            Assertions.assertEquals(1, actual.size());
            Assertions.assertEquals(expected, actual.get(0).getTransaction());
        }
    }

    @Test
    public void findByDescriptionTokensTest() throws SQLException {
        // need a category
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // create a transaction with description "Fronty's Meat Market"
        final Transaction transaction = Transaction.newBuilder(TestUtils.createRandomTransaction(account))
                .setDescription("Fronty's Meat Market")
                .build();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create the transaction
            final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
            categorizedTransactionDao.insert(new CategorizedTransaction(transaction, category));

            // search for transactions with tokens ["Fronty's", "Beat", "Market"]
            final List<String> searchTerms = Arrays.asList("Fronty's", "Beat", "Market");
            final List<CategorizedTransaction> results = categorizedTransactionDao.findByDescription(t, searchTerms);

            //  expect 1 match because the two transactions share "Fronty's" and "Market"
            Assertions.assertEquals(1, results.size());
            Assertions.assertEquals(transaction, results.get(0).getTransaction());
        }
    }

    @Test
    public void selectByFitIdTest() {
        // need a category
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final AccountDao accountDao = new AccountDao(connection);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // now we can create a CategorizedTransaction
        final CategorizedTransactionDao categorizedTransactionDao = new CategorizedTransactionDao(connection, accountDao, categoryDao);
        final String fitId = UUID.randomUUID().toString();
        final CategorizedTransaction expected = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, fitId), category)
        ).orElse(null);

        // and select it by fitId
        final CategorizedTransaction actual = categorizedTransactionDao.selectByFitId(fitId).orElse(null);
        Assertions.assertEquals(expected, actual);
    }
}