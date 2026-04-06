package ca.jonathanfritz.ofxcat.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

class CLITest {

    // ==================== Test infrastructure ====================

    private record TestContext(CLI cli, ByteArrayOutputStream output) {
        String outputText() {
            // Strip ANSI/VT100 escape sequences so assertions work on plain text
            return output.toString(StandardCharsets.UTF_8).replaceAll("\u001B(?:\\[[0-9;?]*[a-zA-Z]|[=>])", "");
        }
    }

    private static TestContext setup(String... inputLines) throws IOException {
        String input = String.join("\n", inputLines) + "\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Terminal terminal =
                TerminalBuilder.builder().streams(in, out).dumb(true).build();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
        return new TestContext(new CLI(terminal, reader), out);
    }

    // ==================== Output tests ====================

    @Test
    void printlnWritesLine() throws IOException {
        TestContext ctx = setup();
        ctx.cli().println("hello world");
        assertThat(ctx.outputText(), containsString("hello world"));
    }

    @Test
    void printlnWritesAllLines() throws IOException {
        TestContext ctx = setup();
        ctx.cli().println(List.of("line one", "line two", "line three"));
        String output = ctx.outputText();
        assertThat(output, containsString("line one"));
        assertThat(output, containsString("line two"));
        assertThat(output, containsString("line three"));
    }

    @Test
    void printFoundNewTransactionIncludesKeyFields() throws IOException {
        Account account = TestUtils.createRandomAccount("Test Account");
        Transaction transaction = TestUtils.createRandomTransaction(
                account, "FIT-001", LocalDate.of(2024, 6, 15), -42.50f, Transaction.TransactionType.DEBIT);

        TestContext ctx = setup();
        ctx.cli().printFoundNewTransaction(transaction);

        String output = ctx.outputText();
        assertThat(output, containsString("FIT-001"));
        assertThat(output, containsString("2024-06-15"));
        assertThat(output, containsString("DEBIT"));
        assertThat(output, containsString("42.50"));
        assertThat(output, containsString(transaction.getDescription()));
        assertThat(output, containsString("Test Account"));
    }

    @Test
    void printTransactionCategorizedAsIncludesCategoryName() throws IOException {
        TestContext ctx = setup();
        ctx.cli().printTransactionCategorizedAs(new Category("Groceries"));
        assertThat(ctx.outputText(), containsString("GROCERIES"));
    }

    @Test
    void printFoundNewTransferIncludesKeyFields() throws IOException {
        Account sourceAccount = TestUtils.createRandomAccount("Chequing");
        Account sinkAccount = TestUtils.createRandomAccount("Savings");
        Category category = new Category("Transfer");

        Transaction source = TestUtils.createRandomTransaction(
                sourceAccount, "SRC-001", LocalDate.of(2024, 6, 15), -500f, Transaction.TransactionType.DEBIT);
        Transaction sink = TestUtils.createRandomTransaction(
                sinkAccount, "SNK-001", LocalDate.of(2024, 6, 15), 500f, Transaction.TransactionType.CREDIT);

        Transfer transfer =
                new Transfer(new CategorizedTransaction(source, category), new CategorizedTransaction(sink, category));

        TestContext ctx = setup();
        ctx.cli().printFoundNewTransfer(transfer);

        String output = ctx.outputText();
        assertThat(output, containsString("500.00"));
        assertThat(output, containsString("Chequing"));
        assertThat(output, containsString("Savings"));
        assertThat(output, containsString("2024-06-15"));
    }

    @Test
    void updateProgressBarIncludesLabelAndPercentage() throws IOException {
        TestContext ctx = setup();
        ctx.cli().updateProgressBar("Importing", 6, 10);
        assertThat(ctx.outputText(), containsString("Importing"));
        assertThat(ctx.outputText(), containsString("60%"));
        assertThat(ctx.outputText(), containsString("(6/10)"));
    }

    // ==================== promptYesNo ====================

    @Test
    void promptYesNoReturnsTrueForY() throws IOException {
        TestContext ctx = setup("y");
        assertTrue(ctx.cli().promptYesNo("Continue?"));
    }

    @Test
    void promptYesNoReturnsTrueForUpperCaseY() throws IOException {
        TestContext ctx = setup("Y");
        assertTrue(ctx.cli().promptYesNo("Continue?"));
    }

    @Test
    void promptYesNoReturnsTrueForBlankInput() throws IOException {
        TestContext ctx = setup("");
        assertTrue(ctx.cli().promptYesNo("Continue?"));
    }

    @Test
    void promptYesNoReturnsFalseForN() throws IOException {
        TestContext ctx = setup("n");
        assertFalse(ctx.cli().promptYesNo("Continue?"));
    }

    @Test
    void promptYesNoReturnsFalseForUpperCaseN() throws IOException {
        TestContext ctx = setup("N");
        assertFalse(ctx.cli().promptYesNo("Continue?"));
    }

    @Test
    void promptYesNoRetriesOnInvalidInputThenAcceptsN() throws IOException {
        TestContext ctx = setup("maybe", "n");
        assertFalse(ctx.cli().promptYesNo("Continue?"));
        assertThat(ctx.outputText(), containsString("Please enter y or n."));
    }

    // ==================== assignAccountName ====================

    @Test
    void assignAccountNameReturnsAccountWithEnteredName() throws IOException {
        OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setAccountId("ACC-123")
                .setBankId("BANK-456")
                .setAccountType("CHECKING")
                .build();

        TestContext ctx = setup("My Chequing Account");
        Account result = ctx.cli().assignAccountName(ofxAccount);

        assertEquals("My Chequing Account", result.getName());
        assertEquals("ACC-123", result.getAccountNumber());
        assertEquals("BANK-456", result.getBankId());
    }

    @Test
    void assignAccountNameRejectsBlankInputAndRetries() throws IOException {
        OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setAccountId("ACC-123")
                .setBankId("BANK-456")
                .setAccountType("CHECKING")
                .build();

        TestContext ctx = setup("   ", "My Chequing Account");
        Account result = ctx.cli().assignAccountName(ofxAccount);

        assertEquals("My Chequing Account", result.getName());
        assertThat(ctx.outputText(), containsString("Account name must not be blank"));
    }

    @Test
    void assignAccountNameIncludesAccountDetailsInPrompt() throws IOException {
        OfxAccount ofxAccount = OfxAccount.newBuilder()
                .setAccountId("ACC-999")
                .setBankId("BANK-456")
                .setAccountType("SAVINGS")
                .build();

        TestContext ctx = setup("Savings Account");
        ctx.cli().assignAccountName(ofxAccount);

        String output = ctx.outputText();
        assertThat(output, containsString("ACC-999"));
        assertThat(output, containsString("SAVINGS"));
    }

    // ==================== chooseCategoryOrAddNew ====================

    @Test
    void chooseCategoryOrAddNewReturnsSelectedCategory() throws IOException {
        List<Category> categories = List.of(new Category("Groceries"), new Category("Restaurants"));

        TestContext ctx = setup("2");
        Optional<Category> result = ctx.cli().chooseCategoryOrAddNew(categories);

        assertTrue(result.isPresent());
        assertThat(result.get().getName(), is("RESTAURANTS"));
    }

    @Test
    void chooseCategoryOrAddNewReturnsEmptyWhenNewCategorySelected() throws IOException {
        List<Category> categories = List.of(new Category("Groceries"), new Category("Restaurants"));

        TestContext ctx = setup("3"); // "New Category" is always the last option
        Optional<Category> result = ctx.cli().chooseCategoryOrAddNew(categories);

        assertFalse(result.isPresent());
    }

    @Test
    void chooseCategoryOrAddNewRejectsOutOfRangeAndRetries() throws IOException {
        List<Category> categories = List.of(new Category("Groceries"));

        TestContext ctx = setup("5", "1");
        Optional<Category> result = ctx.cli().chooseCategoryOrAddNew(categories);

        assertTrue(result.isPresent());
        assertThat(ctx.outputText(), containsString("Please enter a number between 1 and 2"));
    }

    @Test
    void chooseCategoryOrAddNewRejectsNonNumericAndRetries() throws IOException {
        List<Category> categories = List.of(new Category("Groceries"));

        TestContext ctx = setup("abc", "1");
        Optional<Category> result = ctx.cli().chooseCategoryOrAddNew(categories);

        assertTrue(result.isPresent());
        assertThat(ctx.outputText(), containsString("Please enter a number between 1 and 2"));
    }

    // ==================== chooseCategoryOrChooseAnother ====================

    @Test
    void chooseCategoryOrChooseAnotherReturnsEmptyWhenEscapeSelected() throws IOException {
        List<Category> categories = List.of(new Category("Groceries"));

        TestContext ctx = setup("2"); // "Choose another Category" is always the last option
        Optional<Category> result = ctx.cli().chooseCategoryOrChooseAnother(categories);

        assertFalse(result.isPresent());
    }

    @Test
    void chooseCategoryOrChooseAnotherDisplaysAllCategories() throws IOException {
        List<Category> categories =
                List.of(new Category("Groceries"), new Category("Restaurants"), new Category("Utilities"));

        TestContext ctx = setup("1");
        ctx.cli().chooseCategoryOrChooseAnother(categories);

        String output = ctx.outputText();
        assertThat(output, containsString("GROCERIES"));
        assertThat(output, containsString("RESTAURANTS"));
        assertThat(output, containsString("UTILITIES"));
    }

    // ==================== promptForNewCategoryName ====================

    @Test
    void promptForNewCategoryNameReturnsValidName() throws IOException {
        TestContext ctx = setup("Coffee");
        String result = ctx.cli().promptForNewCategoryName(Collections.emptyList());
        assertThat(result, is("Coffee"));
    }

    @Test
    void promptForNewCategoryNameRejectsBlankAndRetries() throws IOException {
        TestContext ctx = setup("  ", "Coffee");
        String result = ctx.cli().promptForNewCategoryName(Collections.emptyList());
        assertThat(result, is("Coffee"));
        assertThat(ctx.outputText(), containsString("Category names must not be blank"));
    }

    @Test
    void promptForNewCategoryNameRejectsCommaAndRetries() throws IOException {
        TestContext ctx = setup("Coffee,Tea", "Coffee");
        String result = ctx.cli().promptForNewCategoryName(Collections.emptyList());
        assertThat(result, is("Coffee"));
        assertThat(ctx.outputText(), containsString("Category names must not contain a comma"));
    }

    @Test
    void promptForNewCategoryNameRejectsDuplicateAndRetries() throws IOException {
        List<Category> existing = List.of(new Category("Groceries"));
        TestContext ctx = setup("Groceries", "Coffee");
        String result = ctx.cli().promptForNewCategoryName(existing);
        assertThat(result, is("Coffee"));
        assertThat(ctx.outputText(), containsString("Category names must be unique"));
    }

    @Test
    void promptForNewCategoryNameIsCaseInsensitiveForDuplicates() throws IOException {
        List<Category> existing = List.of(new Category("Groceries"));
        TestContext ctx = setup("GROCERIES", "Coffee");
        String result = ctx.cli().promptForNewCategoryName(existing);
        assertThat(result, is("Coffee"));
        assertThat(ctx.outputText(), containsString("Category names must be unique"));
    }

    @Test
    void promptForNewCategoryNameRejectsReservedNameNewCategory() throws IOException {
        TestContext ctx = setup("New Category", "Coffee");
        String result = ctx.cli().promptForNewCategoryName(Collections.emptyList());
        assertThat(result, is("Coffee"));
        assertNotNull(ctx.outputText());
    }

    @Test
    void promptForNewCategoryNameRejectsReservedNameChooseAnother() throws IOException {
        TestContext ctx = setup("Choose another Category", "Coffee");
        String result = ctx.cli().promptForNewCategoryName(Collections.emptyList());
        assertThat(result, is("Coffee"));
        assertNotNull(ctx.outputText());
    }
}
