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
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
        transactionCategoryService = new TransactionCategoryService(categoryDao, descriptionCategoryDao, categorizedTransactionDao, connection);
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

    @Test
    void getCategoryExactOneMatchTest() {
        // get exact matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(testAccount)
                .setDescription("Beats 'R Us")
                .setAmount(8.14f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        final Optional<CategorizedTransaction> categorized;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            categorized = transactionCategoryService.getCategoryExact(t, newTransaction);
        }

        Assertions.assertNotNull(categorized.get().getCategory().getId());
        Assertions.assertEquals("MUSIC", categorized.get().getCategory().getName());
        Assertions.assertEquals(newTransaction.getDescription(), categorized.get().getDescription());
        Assertions.assertEquals(newTransaction.getAmount(), categorized.get().getAmount());
        Assertions.assertEquals(newTransaction.getDate(), categorized.get().getDate());
        Assertions.assertEquals(newTransaction.getType(), categorized.get().getType());
    }

    @Test
    void getCategoryExactNoMatchTest() {
        // get exact matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(testAccount)
                .setDescription("Beets 'R Us")
                .setAmount(8.14f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        final Optional<CategorizedTransaction> categorized;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            categorized = transactionCategoryService.getCategoryExact(t, newTransaction);
        }
        Assertions.assertTrue(categorized.isEmpty());
    }

    @Test
    void getCategoryExactMultipleMatchTest() {
        // get exact matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(testAccount)
                .setDescription("Meats 'R Us")
                .setAmount(8.14f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        // an empty optional will be returned because the search string matches multiple categories so an exact
        // match cannot be found
        final Optional<CategorizedTransaction> categorized;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            categorized = transactionCategoryService.getCategoryExact(t, newTransaction);
        }
        Assertions.assertTrue(categorized.isEmpty());
    }

    @Test
    void getCategoryFuzzyTest1() {
        // get fuzzy matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setDescription("Soys 'R Us")
                .setAmount(7.59f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        getCategoryFuzzyTest(newTransaction, 3, Arrays.asList("DATING", "SHOPPING", "PETS"));
    }

    @Test
    void getCategoryFuzzyTest2() {
        // get fuzzy matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setDescription("Streets 'R Us")
                .setAmount(7.59f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        getCategoryFuzzyTest(newTransaction, 1, Collections.singletonList("VEHICLES"));
    }

    private void getCategoryFuzzyTest(Transaction newTransaction, int numExpectedResults, List<String> expectedResults) {
        final List<Category> categories;
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            categories = transactionCategoryService.getCategoryFuzzy(t, newTransaction, 5);
        }

        // we asked for 5 results, but not all of the existing categories have a match threshold > 80%
        Assertions.assertEquals(numExpectedResults, categories.size());

        // all results belong to the set of existing categories
        Assertions.assertTrue(transactionCategoryService.getCategoryNames()
                .containsAll(categories.stream()
                        .map(Category::getName)
                        .collect(Collectors.toList())
                )
        );

        // results are ordered as expected
        Assertions.assertEquals(expectedResults, categories.stream()
                .map(Category::getName)
                .collect(Collectors.toList())
        );
    }

    // TODO: more tests for other methods!
}