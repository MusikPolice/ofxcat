package ca.jonathanfritz.ofxcat.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Category testFixture = new Category("categoryName");

    @Test
    public void serializationTest() throws JsonProcessingException {
        final String serialized = objectMapper.writeValueAsString(testFixture);
        final Category deserialized = objectMapper.readValue(serialized, Category.class);
        assertThat(deserialized, IsEqual.equalTo(testFixture));
    }

    @Test
    public void deserializationTest() throws JsonProcessingException {
        final String serialized = "{\"name\":\"CATEGORYNAME\"}";
        final Category deserialized = objectMapper.readValue(serialized, Category.class);
        assertThat(deserialized, IsEqual.equalTo(testFixture));
    }
}