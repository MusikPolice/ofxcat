package ca.jonathanfritz.ofxcat.cli;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.service.TransactionCategoryService;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.beryx.textio.InputReader;
import org.beryx.textio.TextIO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: test me?
public class CLI {

    private static final String CATEGORIZED_TRANSACTION_BOOKMARK = "categorized-transaction";
    private final TextIO textIO;
    private final TransactionCategoryService transactionCategoryService;

    private static final String NEW_CATEGORY_PROMPT = "New Category";
    private static final String CHOOSE_ANOTHER_CATEGORY_PROMPT = "Choose another Category";
    private static final String CATEGORIZE_NEW_TRANSACTION_BOOKMARK = "categorize-transaction";

    @Inject
    public CLI(TextIO textIO, TransactionCategoryService transactionCategoryService) {
        this.textIO = textIO;
        this.transactionCategoryService = transactionCategoryService;
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

    /**
     * Converts the provided {@link OfxAccount} into an instance of {@link Account}, prompting the user to assign a
     * human readable friendly name along the way
     */
    public Account assignAccountName(OfxAccount ofxAccount) {
        // prompt the user to enter a name for the account
        textIO.getTextTerminal().print("\nFound new account with account number ");
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
                .setAccountId(ofxAccount.getAccountId())
                .setBankId(ofxAccount.getBankId())
                .setAccountType(ofxAccount.getAccountType())
                .setName(accountName)
                .build();
    }

    public CategorizedTransaction categorizeTransaction(Transaction transaction) {
        textIO.getTextTerminal().setBookmark(CATEGORIZE_NEW_TRANSACTION_BOOKMARK);
        textIO.getTextTerminal().println("\nFound new transaction:");
        printTransaction(transaction);

        // try to automatically categorize the transaction
        // fall back to prompting the user for a category if an exact match cannot be found
        final CategorizedTransaction categorizedTransaction = transactionCategoryService.getCategoryExact(transaction)
                .orElse(categorizeTransactionFuzzy(transaction));

        // return the cursor to the bookmark that we set before starting the categorization process
        // this will be replaced by a println(...) if not supported in the current terminal
        textIO.getTextTerminal().resetToBookmark(CATEGORIZE_NEW_TRANSACTION_BOOKMARK);

        // return the cursor to the bookmark that we set before showing the category that the last transaction was sorted into
        // this will be replaced by a println(...) if not supported in the current terminal
        textIO.getTextTerminal().resetToBookmark(CATEGORIZED_TRANSACTION_BOOKMARK);
        textIO.getTextTerminal().setBookmark(CATEGORIZED_TRANSACTION_BOOKMARK);

        textIO.getTextTerminal().print("\nCategorized transaction as ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(categorizedTransaction.getCategory().getName()));

        return categorizedTransaction;
    }

    public void exit() {
        textIO.dispose();
    }

    private void printTransaction(Transaction transaction) {
        // date
        textIO.getTextTerminal().print("Date: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(transaction.getDate().toString()));

        // type
        textIO.getTextTerminal().print("Type: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t ->t.println(transaction.getType().name()));

        // amount
        textIO.getTextTerminal().print("Amount: ");
        if (transaction.getAmount() >= 0) {
            textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(String.format(java.util.Locale.US, "$%.2f", Math.abs(transaction.getAmount()))));
        } else {
            textIO.getTextTerminal().executeWithPropertiesPrefix("value", t -> t.println(String.format(java.util.Locale.US, "-$%.2f", Math.abs(transaction.getAmount()))));
        }

        // description
        textIO.getTextTerminal().print("Description: ");
        textIO.getTextTerminal().executeWithPropertiesPrefix("value", t ->t.println(transaction.getDescription()));
    }

    private CategorizedTransaction categorizeTransactionFuzzy(Transaction transaction) {
        final List<Category> fuzzyMatches = transactionCategoryService.getCategoryFuzzy(transaction, 5);
        if (fuzzyMatches.isEmpty()) {
            // no fuzzy match - add a new category
            return addNewCategory(transaction);
        } else if (fuzzyMatches.size() == 1) {
            // exactly one potential match - prompt user to confirm
            final boolean transactionBelongsToCategory = textIO.newBooleanInputReader()
                    .withDefaultValue(true)
                    .read(String.format("\nDoes the transaction belong to category %s?", fuzzyMatches.get(0).getName()));
            if (transactionBelongsToCategory) {
                return transactionCategoryService.put(transaction, fuzzyMatches.get(0));
            } else {
                // false positive - add a new category for the transaction
                return addNewCategory(transaction);
            }
        }

        // a bunch of potential matches, prompt user to select one
        final List<String> potentialCategories = Stream.concat(
                fuzzyMatches.stream().map(Category::getName),
                Arrays.stream(new String[]{CHOOSE_ANOTHER_CATEGORY_PROMPT})
            )
            .collect(Collectors.toList());
        final String input = textIO.newStringInputReader()
                .withNumberedPossibleValues(potentialCategories)
                .read("\nSelect an existing category for the transaction:");

        // associate the transaction with the selected category, or prompt the user to add a new category if none was selected
        return fuzzyMatches.stream()
                .filter(pc -> pc.getName().equalsIgnoreCase(input))
                .findFirst()
                .map(selectedCategory -> transactionCategoryService.put(transaction, selectedCategory))
                .orElse(addNewCategory(transaction));
    }

    private CategorizedTransaction addNewCategory(Transaction transaction) {
        // if there are no existing categories, prompt the user to enter one
        final List<String> existingCategoryNames = transactionCategoryService.getCategoryNames();
        if (existingCategoryNames.isEmpty()) {
            final String newCategoryName = promptForNewCategoryName();
            return transactionCategoryService.put(transaction, new Category(newCategoryName));
        }

        // prompt the user to choose from an existing category
        final List<String> potentialCategories = Stream.concat(
                existingCategoryNames.stream(),
                Arrays.stream(new String[] {NEW_CATEGORY_PROMPT})
            ).collect(Collectors.toList());

        final String input = textIO.newStringInputReader()
                .withNumberedPossibleValues(potentialCategories)
                .read("\nSelect an existing category for transaction:");

        // if their choice matches an existing category name, return that category
        final String categoryName = existingCategoryNames.stream()
                .filter(pc -> pc.equalsIgnoreCase(input))
                .findFirst()
                .orElseGet(this::promptForNewCategoryName);

        return transactionCategoryService.put(transaction, new Category(categoryName));
    }

    private String promptForNewCategoryName() {
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
                    }
                    return null;
                })
                .read("\nPlease enter a new category for transaction");
    }
}
