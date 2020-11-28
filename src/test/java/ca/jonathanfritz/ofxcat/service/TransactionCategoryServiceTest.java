package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.DescriptionCategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class TransactionCategoryServiceTest extends AbstractDatabaseTest {

    private static TransactionCategoryService transactionCategoryService;

    @BeforeAll
    static void setup() {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);
        transactionCategoryService = new TransactionCategoryService(categoryDao, descriptionCategoryDao);
    }

    @Test
    void getCategoryFuzzyTest1() {
        // put some "existing" categories into the store
        populateTestData();

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
        // put some "existing" categories into the store
        populateTestData();

        // get fuzzy matches for a new transaction
        Transaction newTransaction = Transaction.newBuilder()
                .setDescription("Streets 'R Us")
                .setAmount(7.59f)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .build();
        getCategoryFuzzyTest(newTransaction, 1, Collections.singletonList("VEHICLES"));
    }

    private void populateTestData() {
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Meats 'R Us").build(), new Category("Restaurants"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Beats 'R Us").build(), new Category("Music"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Fleets 'R Us").build(), new Category("Vehicles"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Toys 'R Us").build(), new Category("Shopping"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Boys 'R Us").build(), new Category("Dating"));
        transactionCategoryService.put(Transaction.newBuilder().setDescription("Kois 'R Us").build(), new Category("Pets"));
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