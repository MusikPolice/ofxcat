package ca.jonathanfritz.ofxcat.cli;

import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Transfer;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public class CLI {

    private final Terminal terminal;
    private final LineReader lineReader;

    private static final Logger logger = LogManager.getLogger(CLI.class);

    private static final String NEW_CATEGORY_PROMPT = "New Category";
    private static final String CHOOSE_ANOTHER_CATEGORY_PROMPT = "Choose another Category";

    // Reusable styles
    private static final AttributedStyle STYLE_LABEL =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).faint();
    private static final AttributedStyle STYLE_VALUE =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold();
    private static final AttributedStyle STYLE_HEADER =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
    private static final AttributedStyle STYLE_POSITIVE =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).bold();
    private static final AttributedStyle STYLE_NEGATIVE =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();
    private static final AttributedStyle STYLE_PROMPT = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle STYLE_ERROR =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();
    private static final AttributedStyle STYLE_MUTED =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).faint();

    @Inject
    public CLI(Terminal terminal, LineReader lineReader) {
        this.terminal = terminal;
        this.lineReader = lineReader;
    }

    public void printWelcomeBanner() {
        List<String> bannerLines = Arrays.asList(
                "         __               _   ",
                "        / _|             | |  ",
                "   ___ | |___  _____ __ _| |_ ",
                "  / _ \\|  _\\ \\/ / __/ _` | __|",
                " | (_) | |  >  < (_| (_| | |_ ",
                "  \\___/|_| /_/\\_\\___\\__,_|\\__|",
                "                              ");
        bannerLines.forEach(line -> printAnsi(new AttributedString(line, STYLE_HEADER)));
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
        lineReader.readLine(styledPrompt(line + " "));
    }

    public boolean promptYesNo(String prompt) {
        while (true) {
            String input =
                    lineReader.readLine(styledPrompt(prompt + " [Y/n]: ")).trim();
            if (input.isEmpty() || input.equalsIgnoreCase("y")) {
                return true;
            } else if (input.equalsIgnoreCase("n")) {
                return false;
            }
            printError("Please enter y or n.");
        }
    }

    /**
     * Converts the provided {@link OfxAccount} into an instance of {@link Account}, prompting the user to assign a
     * human readable friendly name along the way
     */
    public Account assignAccountName(OfxAccount ofxAccount) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("\nFound new ");
        sb.style(STYLE_HEADER);
        sb.append(ofxAccount.getAccountType());
        sb.style(AttributedStyle.DEFAULT);
        sb.append(" account with account number ");
        sb.style(STYLE_HEADER);
        sb.append(ofxAccount.getAccountId());
        printAnsi(sb.toAttributedString());

        while (true) {
            String accountName = lineReader
                    .readLine(styledPrompt("Please enter a name for the account: "))
                    .trim();
            if (StringUtils.isBlank(accountName)) {
                printError("Account name must not be blank");
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
        printAnsi(new AttributedString("\nFound new transaction:", STYLE_HEADER));
        printTransaction(transaction);
    }

    public void printTransactionCategorizedAs(final Category category) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("\nCategorized transaction as ");
        sb.style(STYLE_HEADER);
        sb.append(category.getName());
        printAnsi(sb.toAttributedString());
    }

    public void exit() {
        try {
            terminal.close();
        } catch (IOException e) {
            logger.warn("Failed to close terminal", e);
        }
    }

    private void printTransaction(Transaction transaction) {
        printRow("Trans. Id", new AttributedString(transaction.getFitId(), STYLE_MUTED));
        printRow("Date", new AttributedString(transaction.getDate().toString(), STYLE_VALUE));
        printRow("Type", new AttributedString(transaction.getType().name(), STYLE_VALUE));
        printRow("Amount", currencyString(transaction.getAmount()));
        printRow("Description", new AttributedString(transaction.getDescription(), STYLE_VALUE));
        printRow("Account", new AttributedString(transaction.getAccount().getName(), STYLE_VALUE));
        printRow("Balance", currencyString(transaction.getBalance()));
    }

    private void printRow(String label, AttributedString value) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(STYLE_LABEL);
        sb.append(String.format("%12s  ", label));
        sb.style(AttributedStyle.DEFAULT);
        sb.append(value);
        printAnsi(sb.toAttributedString());
    }

    private AttributedString currencyString(float value) {
        AttributedStyle style = value >= 0 ? STYLE_POSITIVE : STYLE_NEGATIVE;
        return new AttributedString(formatCurrency(value), style);
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
        printAnsi(new AttributedString("\nSelect a category:", STYLE_HEADER));
        for (int i = 0; i < categories.size(); i++) {
            AttributedStringBuilder sb = new AttributedStringBuilder();
            sb.style(STYLE_PROMPT);
            sb.append(String.format("  %d  ", i + 1));
            sb.style(STYLE_VALUE);
            sb.append(categories.get(i).getName());
            printAnsi(sb.toAttributedString());
        }
        AttributedStringBuilder escapeSb = new AttributedStringBuilder();
        escapeSb.style(STYLE_PROMPT);
        escapeSb.append(String.format("  %d  ", categories.size() + 1));
        escapeSb.style(STYLE_MUTED);
        escapeSb.append(escapePrompt);
        printAnsi(escapeSb.toAttributedString());

        int choice = readIntInRange(1, categories.size() + 1);
        if (choice == categories.size() + 1) {
            return Optional.empty();
        }
        return Optional.of(categories.get(choice - 1));
    }

    public String promptForNewCategoryName(List<Category> allCategories) {
        while (true) {
            String val = lineReader
                    .readLine(styledPrompt("\nPlease enter a new category for transaction: "))
                    .trim();
            if (StringUtils.isBlank(val)) {
                printError("Category names must not be blank");
            } else if (val.contains(",")) {
                printError("Category names must not contain a comma");
            } else if (val.equalsIgnoreCase(NEW_CATEGORY_PROMPT)) {
                printError(String.format("Category cannot be called \"%s\"", NEW_CATEGORY_PROMPT));
            } else if (val.equalsIgnoreCase(CHOOSE_ANOTHER_CATEGORY_PROMPT)) {
                printError(String.format("Category cannot be called \"%s\"", CHOOSE_ANOTHER_CATEGORY_PROMPT));
            } else if (allCategories.stream().anyMatch(c -> c.getName().trim().equalsIgnoreCase(val))) {
                printError("Category names must be unique");
            } else {
                return val;
            }
        }
    }

    /**
     * Updates a progress bar on the current line, overwriting the previous output. Falls back to
     * printing a new line on dumb terminals.
     *
     * @param label the label to display before the progress bar
     * @param current the current progress value
     * @param total the total value (100%)
     */
    public void updateProgressBar(String label, int current, int total) {
        int percentage = total > 0 ? (current * 100) / total : 0;
        int barWidth = 30;
        int filled = (percentage * barWidth) / 100;

        // System.console() is non-null when stdout is connected to a real terminal (not piped).
        // Use it to decide whether in-place updates (carriage return) are appropriate.
        // ASCII chars are used unconditionally: Unicode block chars (█ ░) are multi-byte UTF-8
        // sequences that get garbled on Windows consoles running with a non-UTF-8 code page (e.g.
        // CP437), which is the default when JLine's JNI library fails to load.
        boolean isInteractive = System.console() != null;
        char filledChar = '=';
        char emptyChar = ' ';
        char headChar = '>';

        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(STYLE_VALUE);
        sb.append(label).append(" ");
        sb.style(STYLE_PROMPT);
        sb.append("[");
        sb.style(STYLE_POSITIVE);
        for (int i = 0; i < barWidth; i++) {
            if (i < filled) {
                sb.append(filledChar);
            } else if (i == filled) {
                sb.append(headChar);
            } else {
                sb.style(STYLE_MUTED);
                sb.append(emptyChar);
            }
        }
        sb.style(STYLE_PROMPT);
        sb.append("] ");
        sb.style(STYLE_VALUE);
        sb.append(String.format("%3d%%", percentage));
        sb.style(STYLE_MUTED);
        sb.append(String.format(" (%d/%d)", current, total));

        String rendered = sb.toAttributedString().toAnsi(terminal);
        if (isInteractive) {
            terminal.writer().print("\r" + rendered);
        } else {
            terminal.writer().println(rendered);
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
        printAnsi(new AttributedString("\nFound new transfer:", STYLE_HEADER));
        printRow("Amount", currencyString(transfer.getSink().getAmount()));
        printRow("From", new AttributedString(transfer.getSource().getAccount().getName(), STYLE_VALUE));
        printRow("To", new AttributedString(transfer.getSink().getAccount().getName(), STYLE_VALUE));
        printRow("On", new AttributedString(transfer.getSource().getDate().toString(), STYLE_VALUE));
    }

    private void printAnsi(AttributedString s) {
        terminal.writer().println(s.toAnsi(terminal));
        terminal.writer().flush();
    }

    private void printError(String message) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(STYLE_ERROR);
        sb.append("✗  ");
        sb.style(AttributedStyle.DEFAULT);
        sb.append(message);
        printAnsi(sb.toAttributedString());
    }

    private String styledPrompt(String text) {
        return new AttributedString(text, STYLE_PROMPT).toAnsi(terminal);
    }

    private int readIntInRange(int min, int max) {
        while (true) {
            String input = lineReader.readLine(styledPrompt("Choice: ")).trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through to error message
            }
            printError(String.format("Please enter a number between %d and %d.", min, max));
        }
    }
}
