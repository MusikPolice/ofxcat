package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.exception.CliException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OfxCat CLI parameter parsing and validation.
 * These tests verify that command-line arguments are properly validated and parsed,
 * ensuring clear error messages for user mistakes.
 */
class OfxCatParameterParsingTest {

    @Test
    void getModeWithNoArguments() {
        // Setup: Empty arguments array
        String[] args = {};

        // Execute & Verify: Should throw CliException for too few arguments
        CliException exception = assertThrows(CliException.class, () -> {
            OfxCat.getMode(args);
        });

        assertTrue(exception.getMessage().contains("Too few arguments"),
                "Error message should indicate too few arguments");
    }

    @Test
    void getModeWithUnknownMode() {
        // Setup: Invalid mode
        String[] args = {"unknown-command"};

        // Execute & Verify: Should throw CliException for invalid mode
        CliException exception = assertThrows(CliException.class, () -> {
            OfxCat.getMode(args);
        });

        assertTrue(exception.getMessage().contains("Invalid mode"),
                "Error message should indicate invalid mode");
    }

    @Test
    void getModeWithValidImportMode() throws CliException {
        // Setup: Valid IMPORT mode
        String[] args = {"import", "file.ofx"};

        // Execute
        OfxCat.Mode mode = OfxCat.getMode(args);

        // Verify: Should return IMPORT mode
        assertEquals(OfxCat.Mode.IMPORT, mode);
    }

    @Test
    void getModeWithValidGetMode() throws CliException {
        // Setup: Valid GET mode
        String[] args = {"get", "transactions"};

        // Execute
        OfxCat.Mode mode = OfxCat.getMode(args);

        // Verify: Should return GET mode
        assertEquals(OfxCat.Mode.GET, mode);
    }

    @Test
    void getModeWithValidHelpMode() throws CliException {
        // Setup: Valid HELP mode
        String[] args = {"help"};

        // Execute
        OfxCat.Mode mode = OfxCat.getMode(args);

        // Verify: Should return HELP mode
        assertEquals(OfxCat.Mode.HELP, mode);
    }

    @Test
    void getModeCaseInsensitive() throws CliException {
        // Setup: IMPORT in uppercase
        String[] args = {"IMPORT", "file.ofx"};

        // Execute
        OfxCat.Mode mode = OfxCat.getMode(args);

        // Verify: Should work (case insensitive)
        assertEquals(OfxCat.Mode.IMPORT, mode);
    }

    @Test
    void getConcernWithNoArguments() {
        // Setup: Only one argument (missing concern)
        String[] args = {"get"};

        // Execute & Verify: Should throw RuntimeException for too few arguments
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            OfxCat.getConcern(args);
        });

        assertTrue(exception.getMessage().contains("Too few arguments"),
                "Error message should indicate too few arguments");
    }

    @Test
    void getConcernWithUnknownConcern() {
        // Setup: Invalid concern
        String[] args = {"get", "unknown-concern"};

        // Execute & Verify: Should throw RuntimeException for invalid concern
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            OfxCat.getConcern(args);
        });

        assertTrue(exception.getMessage().contains("Invalid concern"),
                "Error message should indicate invalid concern");
    }

    @Test
    void getConcernWithValidTransactions() {
        // Setup: Valid TRANSACTIONS concern
        String[] args = {"get", "transactions"};

        // Execute
        OfxCat.Concern concern = OfxCat.getConcern(args);

        // Verify
        assertEquals(OfxCat.Concern.TRANSACTIONS, concern);
    }

    @Test
    void getConcernWithValidAccounts() {
        // Setup: Valid ACCOUNTS concern
        String[] args = {"get", "accounts"};

        // Execute
        OfxCat.Concern concern = OfxCat.getConcern(args);

        // Verify
        assertEquals(OfxCat.Concern.ACCOUNTS, concern);
    }

    @Test
    void getConcernWithValidCategories() {
        // Setup: Valid CATEGORIES concern
        String[] args = {"get", "categories"};

        // Execute
        OfxCat.Concern concern = OfxCat.getConcern(args);

        // Verify
        assertEquals(OfxCat.Concern.CATEGORIES, concern);
    }

    @Test
    void getOptionsWithValidDateRange() throws CliException {
        // Setup: Valid start and end dates
        String[] args = {"get", "transactions", "--start-date=2023-01-01", "--end-date=2023-12-31"};

        // Execute
        OfxCat.OfxCatOptions options = OfxCat.getOptions(args);

        // Verify
        assertNotNull(options);
        assertEquals("2023-01-01", options.startDate().toString());
        assertEquals("2023-12-31", options.endDate().toString());
        assertNull(options.categoryId());
    }

    @Test
    void getOptionsWithStartDateOnly() throws CliException {
        // Setup: Only start date (end date should default to today)
        String[] args = {"get", "transactions", "--start-date=2023-01-01"};

        // Execute
        OfxCat.OfxCatOptions options = OfxCat.getOptions(args);

        // Verify: End date should be today
        assertNotNull(options);
        assertEquals("2023-01-01", options.startDate().toString());
        assertEquals(java.time.LocalDate.now(), options.endDate(),
                "End date should default to today when not specified");
    }

    @Test
    void getOptionsWithMissingStartDate() {
        // Setup: Missing required start date
        String[] args = {"get", "transactions", "--end-date=2023-12-31"};

        // Execute & Verify: Should throw CliException
        assertThrows(CliException.class, () -> {
            OfxCat.getOptions(args);
        });
    }

    @Test
    void getOptionsWithInvalidDateFormat() {
        // Setup: Invalid date format (MM/DD/YYYY instead of YYYY-MM-DD)
        String[] args = {"get", "transactions", "--start-date=01/01/2023"};

        // Execute & Verify: Should throw IllegalArgumentException due to date parsing failure
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            OfxCat.getOptions(args);
        });

        // The exception should indicate date parsing failure
        assertTrue(exception.getMessage().contains("Failed to parse"),
                "Exception should indicate date parsing failure");
    }

    @Test
    void getOptionsWithCategoryFilter() throws CliException {
        // Setup: With category ID filter
        String[] args = {"get", "transactions", "--start-date=2023-01-01", "--category-id=5"};

        // Execute
        OfxCat.OfxCatOptions options = OfxCat.getOptions(args);

        // Verify
        assertNotNull(options);
        assertEquals(5L, options.categoryId());
    }

    @Test
    void getOptionsWithInvalidCategoryId() {
        // Setup: Non-numeric category ID
        String[] args = {"get", "transactions", "--start-date=2023-01-01", "--category-id=abc"};

        // Execute & Verify: Should throw NumberFormatException for non-numeric input
        NumberFormatException exception = assertThrows(NumberFormatException.class, () -> {
            OfxCat.getOptions(args);
        });

        // Verify it's trying to parse "abc"
        assertTrue(exception.getMessage().contains("abc"),
                "Exception should indicate the invalid input");
    }

    @Test
    void getOptionsWithNegativeCategoryId() throws CliException {
        // Setup: Negative category ID
        String[] args = {"get", "transactions", "--start-date=2023-01-01", "--category-id=-1"};

        // Execute: Currently allowed, document behavior
        OfxCat.OfxCatOptions options = OfxCat.getOptions(args);

        // Verify: Negative category ID is parsed (may not exist in DB, but accepted here)
        assertEquals(-1L, options.categoryId());
    }

    @Test
    void toLocalDateWithValidDate() {
        // Setup: Valid ISO date string
        String dateString = "2023-06-15";

        // Execute
        java.time.LocalDate date = OfxCat.toLocalDate(dateString);

        // Verify
        assertNotNull(date);
        assertEquals("2023-06-15", date.toString());
    }

    @Test
    void toLocalDateWithBlankString() {
        // Setup: Blank string
        String dateString = "";

        // Execute & Verify: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            OfxCat.toLocalDate(dateString);
        });

        assertTrue(exception.getMessage().contains("null or blank"),
                "Error message should indicate blank date");
    }

    @Test
    void toLocalDateWithNullString() {
        // Setup: Null string
        String dateString = null;

        // Execute & Verify: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            OfxCat.toLocalDate(dateString);
        });

        assertTrue(exception.getMessage().contains("null or blank"),
                "Error message should indicate null date");
    }

    @Test
    void toLocalDateWithInvalidFormat() {
        // Setup: Invalid date format
        String dateString = "2023/06/15";

        // Execute & Verify: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            OfxCat.toLocalDate(dateString);
        });

        assertTrue(exception.getMessage().contains("Failed to parse"),
                "Error message should indicate parsing failure");
    }

    @Test
    void toLocalDateWithInvalidDate() {
        // Setup: Invalid date (Feb 30)
        String dateString = "2023-02-30";

        // Execute & Verify: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            OfxCat.toLocalDate(dateString);
        });
    }

    @Test
    void toLocalDateWithLeapDay() {
        // Setup: Valid leap day
        String dateString = "2024-02-29";

        // Execute
        java.time.LocalDate date = OfxCat.toLocalDate(dateString);

        // Verify: Leap day accepted
        assertNotNull(date);
        assertEquals("2024-02-29", date.toString());
    }

    @Test
    void toLocalDateWithVeryOldDate() {
        // Setup: Very old date
        String dateString = "1900-01-01";

        // Execute
        java.time.LocalDate date = OfxCat.toLocalDate(dateString);

        // Verify: Old date accepted
        assertNotNull(date);
        assertEquals("1900-01-01", date.toString());
    }

    @Test
    void toLocalDateWithVeryFutureDate() {
        // Setup: Very future date
        String dateString = "2100-12-31";

        // Execute
        java.time.LocalDate date = OfxCat.toLocalDate(dateString);

        // Verify: Future date accepted
        assertNotNull(date);
        assertEquals("2100-12-31", date.toString());
    }

    @Test
    void getModeWithValidCombineMode() throws CliException {
        // Setup: Valid COMBINE mode
        String[] args = {"combine", "categories", "--source=BANK_FEES", "--target=BANK FEES"};

        // Execute
        OfxCat.Mode mode = OfxCat.getMode(args);

        // Verify
        assertEquals(OfxCat.Mode.COMBINE, mode);
    }

    @Test
    void getCombineOptionsWithValidSourceAndTarget() throws CliException {
        // Setup: Valid source and target
        String[] args = {"combine", "categories", "--source=BANK_FEES", "--target=BANK FEES"};

        // Execute
        OfxCat.CombineOptions options = OfxCat.getCombineOptions(args);

        // Verify
        assertEquals("BANK_FEES", options.source());
        assertEquals("BANK FEES", options.target());
    }

    @Test
    void getCombineOptionsWithMissingSource() {
        // Setup: Missing required --source
        String[] args = {"combine", "categories", "--target=BANK FEES"};

        // Execute & Verify
        assertThrows(CliException.class, () -> OfxCat.getCombineOptions(args));
    }

    @Test
    void getCombineOptionsWithMissingTarget() {
        // Setup: Missing required --target
        String[] args = {"combine", "categories", "--source=BANK_FEES"};

        // Execute & Verify
        assertThrows(CliException.class, () -> OfxCat.getCombineOptions(args));
    }

    @Test
    void getCombineOptionsRequiresCategoriesConcern() {
        // Setup: Missing "categories" keyword
        String[] args = {"combine", "--source=BANK_FEES", "--target=BANK FEES"};

        // Execute & Verify
        assertThrows(CliException.class, () -> OfxCat.getCombineOptions(args));
    }

    @Test
    void getModeWithValidRenameMode() throws CliException {
        // Setup: Valid RENAME mode
        String[] args = {"rename", "category", "--source=DAYCARE", "--target=CHILD CARE"};

        // Execute
        OfxCat.Mode mode = OfxCat.getMode(args);

        // Verify
        assertEquals(OfxCat.Mode.RENAME, mode);
    }

    @Test
    void getRenameOptionsWithValidSourceAndTarget() throws CliException {
        // Setup: Valid source and target
        String[] args = {"rename", "category", "--source=DAYCARE", "--target=CHILD CARE"};

        // Execute
        OfxCat.CombineOptions options = OfxCat.getRenameOptions(args);

        // Verify
        assertEquals("DAYCARE", options.source());
        assertEquals("CHILD CARE", options.target());
    }

    @Test
    void getRenameOptionsRequiresCategoryConcern() {
        // Setup: Missing "category" keyword
        String[] args = {"rename", "--source=DAYCARE", "--target=CHILD CARE"};

        // Execute & Verify
        assertThrows(CliException.class, () -> OfxCat.getRenameOptions(args));
    }

    @Test
    void getRenameOptionsRejectsPluralCategories() {
        // Setup: "categories" (plural) is not valid for rename
        String[] args = {"rename", "categories", "--source=DAYCARE", "--target=CHILD CARE"};

        // Execute & Verify: rename expects singular "category"
        assertThrows(CliException.class, () -> OfxCat.getRenameOptions(args));
    }
}

