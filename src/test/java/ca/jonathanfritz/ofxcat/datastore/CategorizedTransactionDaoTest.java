package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.*;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ca.jonathanfritz.ofxcat.datastore.dto.Category.TRANSFER;
import static ca.jonathanfritz.ofxcat.datastore.dto.Category.UNKNOWN;

class CategorizedTransactionDaoTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;

    CategorizedTransactionDaoTest() {
        accountDao = injector.getInstance(AccountDao.class);
        categoryDao = injector.getInstance(CategoryDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
    }

    @Test
    void insertSelectSuccessTest() {
        // need a category
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // now we can create a CategorizedTransaction
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
        Category groceries = new Category("GROCERIES");
        groceries = categoryDao.insert(groceries).orElse(null);
        Category restaurants = new Category("RESTAURANTS");
        restaurants = categoryDao.insert(restaurants).orElse(null);

        // need an account
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // now we can create some CategorizedTransactions
        final LocalDate now = LocalDate.now();
        final CategorizedTransaction threeDaysAgo = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, now.minusDays(3)), groceries)
        ).orElse(null);
        final CategorizedTransaction twoDaysAgo = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, now.minusDays(2)), groceries)
        ).orElse(null);
        final CategorizedTransaction oneDayAgo = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, now.minusDays(1)), restaurants)
        ).orElse(null);

        // now we can get all transactions grouped by category
        Map<Category, List<CategorizedTransaction>> transactions = categorizedTransactionDao.selectGroupByCategory(now.minusDays(4), now);
        Assertions.assertEquals(Set.of(restaurants, groceries), transactions.keySet());
        Assertions.assertEquals(Arrays.asList(threeDaysAgo, twoDaysAgo), transactions.get(groceries));
        Assertions.assertEquals(Collections.singletonList(oneDayAgo), transactions.get(restaurants));

        // adjusting the date range excludes some transactions
        transactions = categorizedTransactionDao.selectGroupByCategory(now.minusDays(2), now);
        Assertions.assertEquals(Set.of(restaurants, groceries), transactions.keySet());
        Assertions.assertEquals(Collections.singletonList(twoDaysAgo), transactions.get(groceries));
        Assertions.assertEquals(Collections.singletonList(oneDayAgo), transactions.get(restaurants));
    }

    @Test
    public void selectByCategoryTest() {
        // need an account
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // and a handful of transactions in the target category
        final List<CategorizedTransaction> expected = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            expected.add(
                    categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)).orElse(null)
            );
        }

        // as well as one transaction in some other category
        categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(account), UNKNOWN)).orElse(null);

        // we can select all the transactions
        final LocalDate minDate = expected.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElse(null);
        final LocalDate maxDate = expected.stream().map(Transaction::getDate).max(LocalDate::compareTo).orElse(null);
        List<CategorizedTransaction> actual = categorizedTransactionDao.selectByCategory(category, minDate, maxDate);
        Assertions.assertEquals(
                expected.stream()
                        .sorted(Comparator.comparing(Transaction::getDate))
                        .collect(Collectors.toList()),
                actual
        );
    }

    @Test
    void isDuplicateSuccessTest() throws SQLException {
        // need a category
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // we can use them to create a transaction
        final Transaction transaction = TestUtils.createRandomTransaction(account);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // the transaction should not a duplicate because it doesn't exist yet
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
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // create a transaction
        final Transaction expected = TestUtils.createRandomTransaction(account);

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create the transaction
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
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // create a transaction with description "Fronty's Meat Market"
        final Transaction transaction = Transaction.newBuilder(TestUtils.createRandomTransaction(account))
                .setDescription("Fronty's Meat Market")
                .build();

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            // create the transaction
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
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // need an account
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // now we can create a CategorizedTransaction
        final String fitId = UUID.randomUUID().toString();
        final CategorizedTransaction expected = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, fitId), category)
        ).orElse(null);

        // and select it by fitId
        final CategorizedTransaction actual = categorizedTransactionDao.selectByFitId(fitId).orElse(null);
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void findUnlinkedTransfersTest() {
        // we need the TRANSFER category
        final Category category = categoryDao.select(1).orElse(null);

        // as well as two accounts
        final Account sourceAccount = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Account sinkAccount = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // and two transactions that represent that transfer of money from one account to the other
        final CategorizedTransaction source = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(sourceAccount), category)
        ).orElse(null);
        final CategorizedTransaction sink = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(sinkAccount), category)
        ).orElse(null);

        // both transactions should be returned if we find unlinked transactions
        final Map<Account, List<Transaction>> unlinkedTransfers = categorizedTransactionDao.findUnlinkedTransfers();
        Assertions.assertEquals(2, unlinkedTransfers.size());
        Assertions.assertEquals(Set.of(sourceAccount, sinkAccount), unlinkedTransfers.keySet());
        Assertions.assertEquals(1, unlinkedTransfers.get(sourceAccount).size());
        Assertions.assertEquals(1, unlinkedTransfers.get(sinkAccount).size());
        Assertions.assertEquals(source.getTransaction(), unlinkedTransfers.get(sourceAccount).get(0));
        Assertions.assertEquals(sink.getTransaction(), unlinkedTransfers.get(sinkAccount).get(0));

        // but if we recognize those transactions as a part of a transfer...
        final TransferDao transferDao = new TransferDao(connection, categorizedTransactionDao);
        transferDao.insert(new Transfer(source, sink));

        // then they are no longer returned by the unlinked transactions method
        Assertions.assertEquals(0, categorizedTransactionDao.findUnlinkedTransfers().size());
    }
}