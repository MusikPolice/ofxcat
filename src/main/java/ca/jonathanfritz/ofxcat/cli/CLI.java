package ca.jonathanfritz.ofxcat.cli;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;

// TODO: test me?
public class CLI {

    private final Terminal terminal;
    private final LineReader lineReader;

    private static final Logger logger = LogManager.getLogger(CLI.class);

    private static final String NEW_CATEGORY_PROMPT = "New Category";
    private static final String CHOOSE_ANOTHER_CATEGORY_PROMPT = "Choose another Category";

    @Inject
    public CLI(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    public void printWelcomeBanner() {
        println(Arrays.asList(
                "         __               _   ",
                "        / _|             | |  ",
                "   ___ | |___  _____ __ _| |_ ",
                "  / _ \\|  _\\ \\/ / __/ _` | __|",
                " | (_) | |  >  < (_| (_| | |_ ",
                "  \\___/|_| /_/\\_\\___\\__,_|\\__|",
                "                              "));
    }

    /**
     * Prints the specified line to the terminal, along with a trailing newline character
     */
    public void println(String line) {
        terminal.writer().println(line);
        terminal.writer().flush();
    }

    /**
     * Prints the specified lines to the terminal, advancing to the next line after each
     */
    public void println(List<String> lines) {
        lines.forEach(this::println);
    }

    /**
     * Prints the specified line to the terminal and blocks until the user presses the enter key
     */
    public void waitForInput(String line) {
        lineReader.readLine(line + " ");
    }

    public boolean promptYesNo(String prompt) {
        while (true) {
            String input = lineReader.readLine(prompt + " [Y/n]: ").trim();
            if (input.isEmpty() || input.equalsIgnoreCase("y")) {
                return true;
            } else if (input.equalsIgnoreCase("n")) {
                return false;
            }
            println("Please enter y or n.");
        }
    }

    /**
     * Converts the provided {@link OfxAccount} into an instance of {@link Account}, prompting the user to assign a
     * human readable friendly name along the way
     */
    public Account assignAccountName(OfxAccount ofxAccount) {
        println("\nFound new " + ofxAccount.getAccountType() + " account with account number "
                + ofxAccount.getAccountId());

        while (true) {
            String accountName =
                    lineReader.readLine("Please enter a name for the account: ").trim();
            if (StringUtils.isBlank(accountName)) {
                println("Account name must not be blank");
                continue;
            }
            return Account.newBuilder()
                    .setAccountNumber(ofxAccount.getAccountId())
                    .setBankId(ofxAccount.getBankId())
                    .setAccountType(ofxAccount.getAccountType())
                    .setName(accountName)
                    .build();
        }
    }

    public void printFoundNewTransaction(Transaction transaction) {
        println("\nFound new transaction:");
        printTransaction(transaction);
    }

    public void printTransactionCategorizedAs(final Category category) {
        println("\nCategorized transaction as " + category.getName());
    }

    public void exit() {
        try {
            terminal.close();
        } catch (java.io.IOException e) {
            logger.warn("Failed to close terminal", e);
        }
    }

    private void printTransaction(Transaction transaction) {
        println("Transaction Id: " + transaction.getFitId());
        println("Date: " + transaction.getDate().toString());
        println("Type: " + transaction.getType().name());
        println("Amount: " + formatCurrency(transaction.getAmount()));
        println("Description: " + transaction.getDescription());
        println("Account: " + transaction.getAccount().getName());
        println("Balance: " + formatCurrency(transaction.getBalance()));
    }

    private String formatCurrency(float value) {
        if (value >= 0) {
            return String.format(java.util.Locale.US, "$%.2f", value);
        } else {
            return String.format(java.util.Locale.US, "-$%.2f", Math.abs(value));
        }
    }

    public Optional<Category> chooseCategoryOrChooseAnother(List<Category> categories) {
        return chooseCategory(categories, CHOOSE_ANOTHER_CATEGORY_PROMPT);
    }

    public Optional<Category> chooseCategoryOrAddNew(List<Category> categories) {
        return chooseCategory(categories, NEW_CATEGORY_PROMPT);
    }

    /**
     * Prompts the user to choose from one of the supplied categories. Includes an additional escape choice.
     *
     * @param categories the list of categories to choose from
     * @return an {@link Optional} containing the selected category, or {@link Optional#empty()} if the escape
     *     choice is selected
     */
    private Optional<Category> chooseCategory(List<Category> categories, final String escapePrompt) {
        println("\nSelect an existing category for the transaction:");
        for (int i = 0; i < categories.size(); i++) {
            println(String.format("  %d  %s", i + 1, categories.get(i).getName()));
        }
        println(String.format("  %d  %s", categories.size() + 1, escapePrompt));

        int choice = readIntInRange(1, categories.size() + 1);
        if (choice == categories.size() + 1) {
            return Optional.empty();
        }
        return Optional.of(categories.get(choice - 1));
    }

    public String promptForNewCategoryName(List<Category> allCategories) {
        while (true) {
            String val = lineReader
                    .readLine("\nPlease enter a new category for transaction: ")
                    .trim();
            if (StringUtils.isBlank(val)) {
                println("Category names must not be blank");
            } else if (val.contains(",")) {
                println("Category names must not contain a comma");
            } else if (val.equalsIgnoreCase(NEW_CATEGORY_PROMPT)) {
                println(String.format("Category cannot be called \"%s\"", NEW_CATEGORY_PROMPT));
            } else if (val.equalsIgnoreCase(CHOOSE_ANOTHER_CATEGORY_PROMPT)) {
                println(String.format("Category cannot be called \"%s\"", CHOOSE_ANOTHER_CATEGORY_PROMPT));
            } else if (allCategories.stream().anyMatch(c -> c.getName().trim().equalsIgnoreCase(val))) {
                println("Category names must be unique");
            } else {
                return val;
            }
        }
    }

    /**
     * Updates a progress bar on the current line. Uses the carriage-return capability to overwrite
     * the previous progress, falling back to printing a new line if not supported.
     *
     * @param label the label to display before the progress bar
     * @param current the current progress value
     * @param total the total value (100%)
     */
    public void updateProgressBar(String label, int current, int total) {
        int percentage = total > 0 ? (current * 100) / total : 0;
        int barWidth = 30;
        int filled = (percentage * barWidth) / 100;

        StringBuilder bar = new StringBuilder();
        bar.append(label).append(" [");
        for (int i = 0; i < barWidth; i++) {
            if (i < filled) {
                bar.append("=");
            } else if (i == filled) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("] ").append(String.format("%3d%%", percentage));
        bar.append(" (").append(current).append("/").append(total).append(")");

        boolean canOverwrite = terminal.getStringCapability(InfoCmp.Capability.carriage_return) != null;
        if (canOverwrite) {
            terminal.writer().print("\r" + bar);
        } else {
            terminal.writer().println(bar.toString());
        }
        terminal.writer().flush();
    }

    /**
     * Completes a progress bar by printing a final newline.
     * Call this after the last updateProgressBar() to move to the next line.
     */
    public void finishProgressBar() {
        terminal.writer().println();
        terminal.writer().flush();
    }

    public void printFoundNewTransfer(Transfer transfer) {
        println("\nFound new transfer:");
        println("Amount: " + formatCurrency(transfer.getSink().getAmount()));
        println("From: " + transfer.getSource().getAccount().getName());
        println("To: " + transfer.getSink().getAccount().getName());
        println("On: " + transfer.getSource().getDate().toString());
    }

    private int readIntInRange(int min, int max) {
        while (true) {
            String input = lineReader.readLine("Choice: ").trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to error message
            }
            println(String.format("Please enter a number between %d and %d.", min, max));
        }
    }
}
