package ca.jonathanfritz.ofxcat.cli;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.beryx.textio.InputReader;
import org.beryx.textio.TextIO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: test me?
public class CLI {

    private static final String CATEGORIZED_TRANSACTION_BOOKMARK = "categorized-transaction";
    private final TextIO textIO;
    private final TextIOWrapper textIOWrapper;

    private static final String NEW_CATEGORY_PROMPT = "New Category";
    private static final String CHOOSE_ANOTHER_CATEGORY_PROMPT = "Choose another Category";
    private static final String CATEGORIZE_NEW_TRANSACTION_BOOKMARK = "categorize-transaction";
    private static final String NEW_TRANSFER_BOOKMARK = "new-transfer";

    @Inject
    public CLI(TextIO textIO, TextIOWrapper textIOWrapper) {
        this.textIO = textIO;
        this.textIOWrapper = textIOWrapper;
    }

    public void printWelcomeBanner() {
        textIO.getTextTerminal().println(Arrays.asList(
                "         __               _   ",
                "        / _|             | |  ",
                "   ___ | |___  _____ __ _| |_ ",
                "  / _ \\|  _\\ \\/ / __/ _` | __|",
                " | (_) | |  >  < (_| (_| | |_ ",
                "  \\___/|_| /_/\\_\\___\\__,_|\\__|",
                "                              "
        ));
    }

    /**
     * Prints the specified line to the terminal, along with a trailing newline character
     */
    public void println(String line) {
        textIO.getTextTerminal().println(line);
    }

    /**
     * Prints the specified lines to the terminal, advancing to the next line after each
     */
    public void println(List<String> lines) {
        textIO.getTextTerminal().println(lines);
    }

    /**
     * Prints the specified line to the terminal using the specified propertiesPrefix as defined in the
     * src/main/resources/textio.properties file. Adds a trailing newline character.
     */
    public void println(String propertiesPrefix, String line) {
        textIO.getTextTerminal().executeWithPropertiesPrefix(propertiesPrefix, t -> t.println(line));
    }

    /**
     * Prints the specified line to the terminal and blocks until the user presses the enter key
     */
    public void waitForInput(String line) {
        textIO.newGenericInputReader(s -> new InputReader.ParseResult<>("")).read(line);
    }

    public boolean promptYesNo(String prompt) {
        return textIOWrapper.promptYesNo(prompt);
    }

    /**
     * Converts the provided {@link OfxAccount} into an instance of {@link Account}, prompting the user to assign a
     * human readable friendly name along the way
     */
    public Account assignAccountName(OfxAccount ofxAccount) {
        // prompt the user to enter a name for the account
        textIO.getTextTerminal().print("\nFound new ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.print(ofxAccount.getAccountType()));
        textIO.getTextTerminal().print(" account with account number ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(ofxAccount.getAccountId()));

        final String accountName = textIO.newStringInputReader()
                .withValueChecker((val, itemName) -> {
                    if (StringUtils.isBlank(val)) {
                        return Collections.singletonList("Account name must not be blank");
                    }
                    return null;
                })
                .read("Please enter a name for the account:");

        // create the account object
        return Account.newBuilder()
                .setAccountNumber(ofxAccount.getAccountId())
                .setBankId(ofxAccount.getBankId())
                .setAccountType(ofxAccount.getAccountType())
                .setName(accountName)
                .build();
    }

    public void printFoundNewTransaction(Transaction transaction) {
        textIO.getTextTerminal().setBookmark(CATEGORIZE_NEW_TRANSACTION_BOOKMARK);
        textIO.getTextTerminal().println("\nFound new transaction:");
        printTransaction(transaction);
    }

    public void printTransactionCategorizedAs(final Category category) {
        // return the cursor to the bookmark that we set before starting the categorization process
        // this will be replaced by a println(...) if not supported in the current terminal
        textIO.getTextTerminal().resetToBookmark(CATEGORIZE_NEW_TRANSACTION_BOOKMARK);

        // return the cursor to the bookmark that we set before showing the category that the last transaction was sorted into
        // this will be replaced by a println(...) if not supported in the current terminal
        textIO.getTextTerminal().resetToBookmark(CATEGORIZED_TRANSACTION_BOOKMARK);
        textIO.getTextTerminal().setBookmark(CATEGORIZED_TRANSACTION_BOOKMARK);

        textIO.getTextTerminal().print("\nCategorized transaction as ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(category.getName()));
    }

    public void exit() {
        textIO.dispose();
    }

    private void printTransaction(Transaction transaction) {
        // fit id
        textIO.getTextTerminal().print("Transaction Id: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transaction.getFitId()));

        // date
        textIO.getTextTerminal().print("Date: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transaction.getDate().toString()));

        // type
        textIO.getTextTerminal().print("Type: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transaction.getType().name()));

        // amount
        textIO.getTextTerminal().print("Amount: ");
        printCurrencyValue(transaction.getAmount());

        // description
        textIO.getTextTerminal().print("Description: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transaction.getDescription()));

        // account name
        textIO.getTextTerminal().print("Account: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transaction.getAccount().getName()));

        // remaining balance
        textIO.getTextTerminal().print("Balance: ");
        printCurrencyValue(transaction.getBalance());
    }

    private void printCurrencyValue(float value) {
        if (value >= 0) {
            printCurrencyValue("$%.2f", value);
        } else {
            printCurrencyValue("-$%.2f", value);
        }
    }

    private void printCurrencyValue(String formatString, float value) {
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t ->
                t.println(String.format(java.util.Locale.US, formatString, Math.abs(value))));
    }

    public Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
        return chooseCategory(categories, CHOOSE_ANOTHER_CATEGORY_PROMPT);
    }

    public Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
        return chooseCategory(categories, NEW_CATEGORY_PROMPT);
    }

    /**
     * Prompts the user to choose from one of the supplied distinctCategories. Includes an additional choice to "Choose
     * another category"
     * @param categories the list of categories to choose from
     * @return an {@link Optional<Category>} containing the selected category, or {@link Optional#empty()} if "Choose
     * another category" is selected
     */
    private Optional<Category> chooseCategory(List<Category> categories, final String prompt) {
        final List<String> potentialCategories = Stream.concat(
                categories.stream().map(Category::getName),
                Arrays.stream(new String[]{prompt})
        )
        .collect(Collectors.toList());
        final String choice = textIOWrapper.promptChooseString("\nSelect an existing category for the transaction:", potentialCategories);
        return categories.stream()
                .filter(pc -> pc.getName().equalsIgnoreCase(choice))
                .findFirst();
    }

    public String promptForNewCategoryName(List<Category> allCategories) {
        // otherwise, prompt them to enter a new category name
        return textIO.newStringInputReader()
                .withValueChecker((val, itemName) -> {
                    if (StringUtils.isBlank(val)) {
                        return Collections.singletonList("Category names must not be blank");
                    } else if (val.contains(",")) {
                        // TODO: better csv escaping?
                        return Collections.singletonList("Category names must not contain a comma");
                    } else if (val.equalsIgnoreCase(NEW_CATEGORY_PROMPT)) {
                        return Collections.singletonList(String.format("Category cannot be called \"%s\"", NEW_CATEGORY_PROMPT));
                    } else if (val.equalsIgnoreCase(CHOOSE_ANOTHER_CATEGORY_PROMPT)) {
                        return Collections.singletonList(String.format("Category cannot be called \"%s\"", CHOOSE_ANOTHER_CATEGORY_PROMPT));
                    } else if (allCategories.stream().anyMatch(c -> c.getName().trim().equalsIgnoreCase(val.trim()))) {
                        return Collections.singletonList("Category names must be unique");
                    }
                    return null;
                })
                .read("\nPlease enter a new category for transaction");
    }

    public void printFoundNewTransfer(Transfer transfer) {
        textIO.getTextTerminal().println("\nFound new transfer:");

        textIO.getTextTerminal().print("Amount: ");
        printCurrencyValue(transfer.getSink().getAmount());

        textIO.getTextTerminal().print("From: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transfer.getSource().getAccount().getName()));

        textIO.getTextTerminal().print("To: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transfer.getSink().getAccount().getName()));

        textIO.getTextTerminal().print("On: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transfer.getSource().getDate().toString()));
    }
}
