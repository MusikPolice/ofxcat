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

class TransactionCategoryStoreTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final PathUtils pathUtils = new TestPathUtils();

    private static TransactionCategoryStore testFixture;

    @BeforeEach
    public void setup() throws IOException {
        Files.deleteIfExists(pathUtils.getTransactionCategoryStorePath());
        testFixture = new TransactionCategoryStore(objectMapper, pathUtils);
        testFixture.clear();
    }

    @Test
    public void serializationTest() throws IOException {
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

    // TODO: more tests for other methods!

    private static class TestPathUtils extends PathUtils {
        @Override
        public Path getTransactionCategoryStorePath() {
            return Paths.get(System.getProperty("user.dir"),"target", "transaction-category-store.json");
        }
    }
}