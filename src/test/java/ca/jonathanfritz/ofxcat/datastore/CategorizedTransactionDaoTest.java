package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static ca.jonathanfritz.ofxcat.datastore.dto.Category.UNKNOWN;

class CategorizedTransactionDaoTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final TransactionTokenDao transactionTokenDao;

    CategorizedTransactionDaoTest() {
        accountDao = injector.getInstance(AccountDao.class);
        categoryDao = injector.getInstance(CategoryDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        transactionTokenDao = new TransactionTokenDao();
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
        categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(account), UNKNOWN));

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
    public void selectByCategoryWithoutDateFilterTest() {
        // need an account and two categories
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Category groceries = categoryDao.insert(new Category("GROCERIES")).orElse(null);
        final Category restaurants = categoryDao.insert(new Category("RESTAURANTS")).orElse(null);

        // insert transactions across different dates into the target category
        final List<CategorizedTransaction> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expected.add(
                    categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(account), groceries)).orElse(null)
            );
        }

        // insert a transaction in a different category
        categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(account), restaurants));

        // selectByCategory without date filter should return only groceries transactions
        List<CategorizedTransaction> actual = categorizedTransactionDao.selectByCategory(groceries);
        Assertions.assertEquals(expected.size(), actual.size());
        for (CategorizedTransaction expectedTxn : expected) {
            Assertions.assertTrue(actual.stream().anyMatch(a -> a.getId().equals(expectedTxn.getId())));
        }
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

    @Test
    void hasTransactionsWithoutTokens_returnsFalseWhenNoTransactions() {
        // Empty database should return false
        Assertions.assertFalse(categorizedTransactionDao.hasTransactionsWithoutTokens());
    }

    @Test
    void hasTransactionsWithoutTokens_returnsTrueWhenTransactionHasNoTokens() throws SQLException {
        // Create a transaction without tokens
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        categorizedTransactionDao.insert(new CategorizedTransaction(TestUtils.createRandomTransaction(account), category));

        // Should return true - transaction exists without tokens
        Assertions.assertTrue(categorizedTransactionDao.hasTransactionsWithoutTokens());
    }

    @Test
    void hasTransactionsWithoutTokens_returnsFalseWhenAllTransactionsHaveTokens() throws SQLException {
        // Create a transaction
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final CategorizedTransaction txn = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElse(null);

        // Add tokens for it
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, txn.getId(), Set.of("test", "token"));
        }

        // Should return false - all transactions have tokens
        Assertions.assertFalse(categorizedTransactionDao.hasTransactionsWithoutTokens());
    }

    @Test
    void selectWithoutTokens_returnsEmptyWhenNoTransactions() {
        // Empty database should return empty list
        Assertions.assertTrue(categorizedTransactionDao.selectWithoutTokens().isEmpty());
    }

    @Test
    void selectWithoutTokens_returnsTransactionsWithoutTokens() throws SQLException {
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // Create two transactions
        final CategorizedTransaction withTokens = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElse(null);
        final CategorizedTransaction withoutTokens = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElse(null);

        // Add tokens only for the first one
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, withTokens.getId(), Set.of("test", "token"));
        }

        // Should return only the transaction without tokens
        List<CategorizedTransaction> result = categorizedTransactionDao.selectWithoutTokens();
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(withoutTokens.getId(), result.getFirst().getId());
    }

    @Test
    void selectWithoutTokens_returnsEmptyWhenAllHaveTokens() throws SQLException {
        final Category category = categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // Create a transaction
        final CategorizedTransaction txn = categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account), category)
        ).orElse(null);

        // Add tokens for it
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, txn.getId(), Set.of("test", "token"));
        }

        // Should return empty list - all transactions have tokens
        Assertions.assertTrue(categorizedTransactionDao.selectWithoutTokens().isEmpty());
    }
}
