package ca.jonathanfritz.ofxcat.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TransactionCategoryStoreTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static TransactionCategoryStore testFixture;

    @BeforeAll
    public static void setup() {
        final Map<String, Category> descriptionCategories = new HashMap<>();
        descriptionCategories.put("Quick Stop", new Category("GROCERIES"));
        descriptionCategories.put("Fronty's Meat Market", new Category("GROCERIES"));
        descriptionCategories.put("Sneed's Feed and Seed", new Category("FARM SUPPLIES"));
        testFixture = new TransactionCategoryStore(descriptionCategories);
    }

    @Test
    public void serializationTest() throws JsonProcessingException {
        final String serialized = objectMapper.writeValueAsString(testFixture);
        final TransactionCategoryStore deserialized = objectMapper.readValue(serialized, TransactionCategoryStore.class);
        assertThat(deserialized.getCategoryNames(), IsEqual.equalTo(Arrays.asList("FARM SUPPLIES", "GROCERIES")));
        assertThat(deserialized, IsEqual.equalTo(testFixture));
    }

    @Test
    public void deserializationTest() throws JsonProcessingException {
        final String serialized = "{\"categoryStore\":{\"Fronty's Meat Market\":{\"name\":\"GROCERIES\"},\"Quick Stop\":{\"name\":\"GROCERIES\"},\"Sneed's Feed and Seed\":{\"name\":\"FARM SUPPLIES\"}}}";
        final TransactionCategoryStore deserialized = objectMapper.readValue(serialized, TransactionCategoryStore.class);
        assertThat(deserialized.getCategoryNames(), IsEqual.equalTo(Arrays.asList("FARM SUPPLIES", "GROCERIES")));
        assertThat(deserialized, IsEqual.equalTo(testFixture));
    }
}