package ca.jonathanfritz.ofxcat.cli;

import static org.junit.jupiter.api.Assertions.*;

import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import org.junit.jupiter.api.Test;

class CLIInputValidationTest {

    @Test
    void categoryNameValidationAcceptsValidName() {
        final Category category = new Category("GROCERIES");
        assertNotNull(category);
        assertEquals("GROCERIES", category.getName());
    }

    @Test
    void categoryNameValidationAcceptsUnicode() {
        final Category category = new Category("CAFÉ & RESTAURANTS ☕");
        assertNotNull(category);
        assertEquals("CAFÉ & RESTAURANTS ☕", category.getName());
    }

    @Test
    void categoryNameValidationAcceptsSpecialCharacters() {
        final Category category = new Category("HOME & GARDEN (MISC.)");
        assertNotNull(category);
        assertEquals("HOME & GARDEN (MISC.)", category.getName());
    }

    @Test
    void categoryNameValidationAcceptsVeryLongName() {
        final String longName = "A".repeat(255);
        final Category category = new Category(longName);
        assertNotNull(category);
        assertEquals(longName, category.getName());
    }
}
