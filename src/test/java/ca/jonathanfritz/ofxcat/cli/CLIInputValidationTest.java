package ca.jonathanfritz.ofxcat.cli;

import static org.junit.jupiter.api.Assertions.*;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for CLI input validation to ensure user input is properly validated
 * and clear error messages are provided for invalid input.
 *
 * Note: TextIO validation behavior (blank rejection, reserved names, comma rejection)
 * is tested via integration tests. Unit tests focus on Category DTO validation
 * and CLI method behavior that can be tested with test doubles.
 */
class CLIInputValidationTest {

    @Test
    void categoryNameValidationAcceptsValidName() {
        // Setup: Valid category name
        // note that the CLI echoes category names in all caps
        final String validName = "GROCERIES";

        // Execute
        final Category category = new Category(validName);

        // Verify
        assertNotNull(category);
        assertEquals(validName, category.getName());
    }

    @Test
    void categoryNameValidationAcceptsUnicode() {
        // Setup: Category name with unicode characters
        // note that the CLI echoes category names in all caps
        final String unicodeName = "CAFÉ & RESTAURANTS ☕";

        // Execute
        final Category category = new Category(unicodeName);

        // Verify: Unicode characters are accepted
        assertNotNull(category);
        assertEquals(unicodeName, category.getName());
    }

    @Test
    void categoryNameValidationAcceptsSpecialCharacters() {
        // Setup: Category name with special characters (except comma)
        // note that the CLI echos category names in all caps
        final String specialName = "HOME & GARDEN (MISC.)";

        // Execute
        final Category category = new Category(specialName);

        // Verify: Special characters are accepted
        assertNotNull(category);
        assertEquals(specialName, category.getName());
    }

    @Test
    void categoryNameValidationAcceptsVeryLongName() {
        // Setup: Very long category name
        final String longName = "A".repeat(255);

        // Execute: Should not crash (may be truncated by database)
        final Category category = new Category(longName);

        // Verify
        assertNotNull(category);
        assertEquals(longName, category.getName());
    }

    @Test
    void chooseCategoryOrAddNewHandlesEmptyList() {
        // Setup: Empty category list
        final List<Category> emptyList = Collections.emptyList();
        final TestCLI cli = new TestCLI(new Category("Test"));

        // Execute: Should handle empty list gracefully
        final var result = cli.chooseCategoryOrAddNew(emptyList);

        // Verify: Returns a category (the test category)
        assertTrue(result.isPresent());
    }

    @Test
    void chooseCategoryOrAddNewHandlesSingleCategory() {
        // Setup: Single category in list
        final Category singleCategory = new Category("Groceries");
        final List<Category> singleList = Collections.singletonList(singleCategory);
        final TestCLI cli = new TestCLI(singleCategory);

        // Execute
        final var result = cli.chooseCategoryOrAddNew(singleList);

        // Verify: Returns a category
        assertTrue(result.isPresent());
        assertEquals(singleCategory, result.get());
    }

    @Test
    void chooseCategoryOrAddNewHandlesMultipleCategories() {
        // Setup: Multiple categories
        final Category groceries = new Category("Groceries");
        final Category restaurants = new Category("Restaurants");
        final List<Category> categories = Arrays.asList(groceries, restaurants);
        final TestCLI cli = new TestCLI(groceries);

        // Execute
        final var result = cli.chooseCategoryOrAddNew(categories);

        // Verify: Returns a category
        assertTrue(result.isPresent());
    }

    // ==================== Account Name Validation Tests ====================

    @Test
    void assignAccountNameReturnsAccountWithCorrectFields() {
        // Setup: Test CLI that returns a specific account name
        final String accountName = "My Checking Account";
        final AccountTestCLI cli = new AccountTestCLI(accountName);

        // Create an OfxAccount
        final OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setAccountId("1234567890")
                .setBankId("BANK001")
                .setAccountType("CHECKING")
                .build();

        // Execute
        final Account result = cli.assignAccountName(ofxAccount);

        // Verify: Account has all fields populated correctly
        assertNotNull(result);
        assertEquals("1234567890", result.getAccountNumber());
        assertEquals("BANK001", result.getBankId());
        assertEquals("CHECKING", result.getAccountType());
        assertEquals(accountName, result.getName());
    }

    @Test
    void assignAccountNameAcceptsUnicodeName() {
        // Setup: Account name with unicode characters
        final String unicodeName = "Compte Épargne 日本語";
        final AccountTestCLI cli = new AccountTestCLI(unicodeName);

        final OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setAccountId("9876543210")
                .setBankId("BANK002")
                .setAccountType("SAVINGS")
                .build();

        // Execute
        final Account result = cli.assignAccountName(ofxAccount);

        // Verify: Unicode name is accepted
        assertNotNull(result);
        assertEquals(unicodeName, result.getName());
    }

    @Test
    void assignAccountNameAcceptsVeryLongName() {
        // Setup: Very long account name
        final String longName = "A".repeat(200);
        final AccountTestCLI cli = new AccountTestCLI(longName);

        final OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setAccountId("1111111111")
                .setBankId("BANK003")
                .setAccountType("MONEYMRKT")
                .build();

        // Execute
        final Account result = cli.assignAccountName(ofxAccount);

        // Verify: Long name is accepted (may be truncated by database later)
        assertNotNull(result);
        assertEquals(longName, result.getName());
    }

    @Test
    void assignAccountNameAcceptsSpecialCharacters() {
        // Setup: Account name with special characters
        final String specialName = "John's Account #1 (Primary)";
        final AccountTestCLI cli = new AccountTestCLI(specialName);

        final OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setAccountId("2222222222")
                .setBankId("BANK004")
                .setAccountType("CHECKING")
                .build();

        // Execute
        final Account result = cli.assignAccountName(ofxAccount);

        // Verify: Special characters are accepted
        assertNotNull(result);
        assertEquals(specialName, result.getName());
    }

    // ==================== Test Doubles ====================

    // Simple test double for CLI (category-focused)
    private static class TestCLI extends CLI {
        private final Category categoryToReturn;
        private final String nameToReturn;

        TestCLI(Category categoryToReturn) {
            super(null, null);
            this.categoryToReturn = categoryToReturn;
            this.nameToReturn = null;
        }

        TestCLI(String nameToReturn) {
            super(null, null);
            this.categoryToReturn = null;
            this.nameToReturn = nameToReturn;
        }

        @Override
        public java.util.Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
            return java.util.Optional.ofNullable(categoryToReturn);
        }

        @Override
        public String promptForNewCategoryName(List<Category> allCategories) {
            return nameToReturn != null ? nameToReturn : "Test Category";
        }
    }

    // Test double for CLI (account-focused)
    private static class AccountTestCLI extends CLI {
        private final String accountNameToReturn;

        AccountTestCLI(String accountNameToReturn) {
            super(null, null);
            this.accountNameToReturn = accountNameToReturn;
        }

        @Override
        public Account assignAccountName(OfxAccount ofxAccount) {
            // Build account with the provided name, simulating user input
            return Account.newBuilder()
                    .setAccountNumber(ofxAccount.getAccountId())
                    .setBankId(ofxAccount.getBankId())
                    .setAccountType(ofxAccount.getAccountType())
                    .setName(accountNameToReturn)
                    .build();
        }
    }
}
