package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.DescriptionCategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class TransactionCategoryServiceTest extends AbstractDatabaseTest {

    private final TransactionCategoryService transactionCategoryService;

    public TransactionCategoryServiceTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);
        transactionCategoryService = new TransactionCategoryService(categoryDao, descriptionCategoryDao);
    }

    @BeforeEach
    void populateTestData() {
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Beats 'R Us").build(), new Category("Music"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Fleets 'R Us").build(), new Category("Vehicles"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Toys 'R Us").build(), new Category("Shopping"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Boys 'R Us").build(), new Category("Dating"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Kois 'R Us").build(), new Category("Pets"));

        // one of the descriptions is linked to two categories
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Meats 'R Us").build(), new Category("Restaurants"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Meats 'R Us").build(), new Category("Groceries"));
    }

    @Test
    void getCategoryExactOneMatchTest() {
        // get exact matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder()
                .setDescription("Beats 'R Us")
                .setAmount(8.14f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        final Optional<CategorizedTransaction> categorized = transactionCategoryService.getCategoryExact(newTransaction);
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
        Transaction newTransaction = Transaction.newBuilder()
                .setDescription("Beets 'R Us")
                .setAmount(8.14f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        final Optional<CategorizedTransaction> categorized = transactionCategoryService.getCategoryExact(newTransaction);
        Assertions.assertTrue(categorized.isEmpty());
    }

    @Test
    void getCategoryExactMultipleMatchTest() {
        // get exact matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder()
                .setDescription("Meats 'R Us")
                .setAmount(8.14f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();

        // an empty optional will be returned because the search string matches multiple categories so an exact
        // match cannot be found
        final Optional<CategorizedTransaction> categorized = transactionCategoryService.getCategoryExact(newTransaction);
        Assertions.assertTrue(categorized.isEmpty());
    }

    @Test
    void getCategoryFuzzyTest1() {
        // get fuzzy matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder()
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
        Transaction newTransaction = Transaction.newBuilder()
                .setDescription("Streets 'R Us")
                .setAmount(7.59f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        getCategoryFuzzyTest(newTransaction, 1, Collections.singletonList("VEHICLES"));
    }

    private void getCategoryFuzzyTest(Transaction newTransaction, int numExpectedResults, List<String> expectedResults) {
        List<Category> categories = transactionCategoryService.getCategoryFuzzy(newTransaction, 5);

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