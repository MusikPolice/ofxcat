package ca.jonathanfritz.ofxcat.data;

import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class TransactionCategoryStoreTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final PathUtils pathUtils = new TestPathUtils();

    private static TransactionCategoryStore testFixture;

    @BeforeEach
    void setup() throws IOException {
        Files.deleteIfExists(pathUtils.getTransactionCategoryStorePath());
        testFixture = new TransactionCategoryStore(objectMapper, pathUtils);
        testFixture.clear();
    }

    @Test
    void serializationTest() throws IOException {
        // put a transaction in the store and save it to disk
        testFixture.put(Transaction.newBuilder()
                .setDescription("Fronty's Meat Market")
                .build(),
            new Category("GROCERIES"));
        Assertions.assertEquals(1, testFixture.getCategoryNames().size());
        testFixture.save();

        // clear the store
        testFixture.clear();
        Assertions.assertEquals(0, testFixture.getCategoryNames().size());

        // assert that file was written to disk
        Assertions.assertTrue(Files.exists(pathUtils.getTransactionCategoryStorePath()));
        Assertions.assertNotEquals(Files.size(pathUtils.getTransactionCategoryStorePath()), 0L);

        // load the store and check that the data is still there
        testFixture.load();
        Assertions.assertEquals(1, testFixture.getCategoryNames().size());
        Assertions.assertArrayEquals(new String[]{"GROCERIES"}, testFixture.getCategoryNames().toArray(new String[]{}));
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
        testFixture.put(Transaction.newBuilder().setDescription("Meats 'R Us").build(), new Category("Restaurants"));
        testFixture.put(Transaction.newBuilder().setDescription("Beats 'R Us").build(), new Category("Music"));
        testFixture.put(Transaction.newBuilder().setDescription("Fleets 'R Us").build(), new Category("Vehicles"));
        testFixture.put(Transaction.newBuilder().setDescription("Toys 'R Us").build(), new Category("Shopping"));
        testFixture.put(Transaction.newBuilder().setDescription("Boys 'R Us").build(), new Category("Dating"));
        testFixture.put(Transaction.newBuilder().setDescription("Kois 'R Us").build(), new Category("Pets"));
    }

    private void getCategoryFuzzyTest(Transaction newTransaction, int numExpectedResults, List<String> expectedResults) {
        List<Category> categories = testFixture.getCategoryFuzzy(newTransaction, 5);

        // we asked for 5 results, but not all of the existing categories have a match threshold > 80%
        Assertions.assertEquals(numExpectedResults, categories.size());

        // all results belong to the set of existing categories
        Assertions.assertTrue(testFixture.getCategoryNames()
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

    private static class TestPathUtils extends PathUtils {
        @Override
        public Path getTransactionCategoryStorePath() {
            return Paths.get(System.getProperty("user.dir"),"target", "transaction-category-store.json");
        }
    }
}