package ca.jonathanfritz.ofxcat.cli;

import ca.jonathanfritz.ofxcat.data.TransactionCategoryStore;
import ca.jonathanfritz.ofxcat.io.OfxAccount;
import ca.jonathanfritz.ofxcat.transactions.*;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.beryx.textio.TextIO;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: test me?
public class CLI {

    private final TextIO textIO;
    private final TransactionCategoryStore transactionCategoryStore;

    private static final String NEW_CATEGORY_PROMPT = "New Category";
    private static final String CHOOSE_ANOTHER_CATEGORY_PROMPT = "Choose another Category";

    @Inject
    public CLI(TextIO textIO, TransactionCategoryStore transactionCategoryStore) {
        this.textIO = textIO;
        this.transactionCategoryStore = transactionCategoryStore;
    }

    public Account assignAccountName(OfxAccount ofxAccount) {
        final String accountName = textIO.newStringInputReader()
                .withValueChecker((val, itemName) -> {
                    if (StringUtils.isBlank(val)) {
                        return Collections.singletonList("Account name must not be blank");
                    }
                    return null;
                })
                .read(String.format("\nPlease enter a name for account number %s", ofxAccount.getAccountId()));

        return Account.newBuilder()
                .setAccountId(ofxAccount.getAccountId())
                .setBankId(ofxAccount.getBankId())
                .setAccountType(ofxAccount.getAccountType())
                .setName(accountName)
                .build();
    }

    public CategorizedTransaction categorizeTransaction(Transaction transaction) {
        // try to automatically categorize the transaction
        // fall back to prompting the user for a category if an exact match cannot be found
        final CategorizedTransaction categorizedTransaction = transactionCategoryStore.getCategoryExact(transaction)
                .orElse(categorizeTransactionFuzzy(transaction));

        textIO.getTextTerminal().println(String.format("Categorized transaction %s as %s", transaction, categorizedTransaction.getCategory().getName()));
        return categorizedTransaction;
    }

    private CategorizedTransaction categorizeTransactionFuzzy(Transaction transaction) {
        final List<Category> fuzzyMatches = transactionCategoryStore.getCategoryFuzzy(transaction, 5);
        if (fuzzyMatches.isEmpty()) {
            // no fuzzy match - add a new category
            return addNewCategory(transaction);
        } else if (fuzzyMatches.size() == 1) {
            // exactly one potential match - prompt user to confirm
            final boolean transactionBelongsToCategory = textIO.newBooleanInputReader()
                    .withDefaultValue(true)
                    .read(String.format("\nDoes %s belong to category %s?", transaction, fuzzyMatches.get(0).getName()));
            if (transactionBelongsToCategory) {
                return transactionCategoryStore.put(transaction, fuzzyMatches.get(0));
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
                .read(String.format("\nSelect a category for %s:", transaction));

        // associate the transaction with the selected category, or prompt the user to add a new category if none was selected
        return fuzzyMatches.parallelStream()
                .filter(pc -> pc.getName().equalsIgnoreCase(input))
                .findFirst()
                .map(selectedCategory -> transactionCategoryStore.put(transaction, selectedCategory))
                .orElse(addNewCategory(transaction));
    }

    private CategorizedTransaction addNewCategory(Transaction transaction) {
        // if there are no existing categories, prompt the user to enter the first one
        final List<String> existingCategoryNames = transactionCategoryStore.getCategoryNames();
        if (existingCategoryNames.isEmpty()) {
            final String newCategoryName = promptForNewCategoryName(transaction);
            return transactionCategoryStore.put(transaction, new Category(newCategoryName));
        }

        // prompt the user to choose from an existing category name or a new category name
        final List<String> potentialCategories = Stream.concat(
                existingCategoryNames.stream(),
                Arrays.stream(new String[] {NEW_CATEGORY_PROMPT})
            ).collect(Collectors.toList());

        final String input = textIO.newStringInputReader()
                .withNumberedPossibleValues(potentialCategories)
                .read(String.format("\nSelect a category for %s:", transaction));

        // if their choice matches an existing category name, return that category
        final String categoryName = existingCategoryNames.parallelStream()
                .filter(pc -> pc.equalsIgnoreCase(input))
                .findFirst()
                .orElseGet(() -> {
                    return promptForNewCategoryName(transaction);
                });

        return transactionCategoryStore.put(transaction, new Category(categoryName));
    }

    private String promptForNewCategoryName(Transaction transaction) {
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
                .read(String.format("\nPlease enter a new category for %s", transaction));
    }
}
