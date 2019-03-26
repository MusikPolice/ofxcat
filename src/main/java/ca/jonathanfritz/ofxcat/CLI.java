package ca.jonathanfritz.ofxcat;

import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import ca.jonathanfritz.ofxcat.transactions.TransactionCategoryStore;
import org.apache.commons.lang3.StringUtils;
import org.beryx.textio.TextIO;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CLI {

    private final TextIO textIO;
    private final TransactionCategoryStore transactionCategoryStore;

    public CLI(TextIO textIO, TransactionCategoryStore transactionCategoryStore) {
        this.textIO = textIO;
        this.transactionCategoryStore = transactionCategoryStore;
    }

    public Set<CategorizedTransaction> categorizeTransactions(Set<Transaction> transactions) {
        final Set<CategorizedTransaction> categorizedTransactions = new HashSet<>();

        // TODO: when adding a description to a category, let user control the part of the description to match on so that IDP PURCHASE can be stripped out

        for (Transaction transaction : transactions) {
            // try to automatically categorize the transaction
            CategorizedTransaction categorizedTransaction = transactionCategoryStore.getCategoryExact(transaction);
            if (categorizedTransaction == null) {
                // didn't work, fall back to prompting the user for a category
                categorizedTransaction = categorizeTransaction(transaction);
            }

            if (categorizedTransaction != null) {
                categorizedTransactions.add(categorizedTransaction);
                textIO.getTextTerminal().println(String.format("Categorized transaction %s as %s", transaction, categorizedTransaction.getCategory().getName()));
            } else {
                textIO.getTextTerminal().println("Failed to categorize transaction " + transaction.toString());
            }
        }

        return categorizedTransactions;
    }

    private CategorizedTransaction categorizeTransaction(Transaction transaction) {
        final List<Category> potentialCategories = transactionCategoryStore.getCategoryFuzzy(transaction, 5);
        if (potentialCategories.isEmpty()) {
            final CategorizedTransaction categorizedTransaction = addNewCategory(transaction);
            if (categorizedTransaction != null) {
                return categorizedTransaction;
            }
        } else if (potentialCategories.size() == 1) {
            // special case for exactly one potential match
            final boolean input = textIO.newBooleanInputReader()
                    .withDefaultValue(true)
                    .read(String.format("\nDoes %s belong to category %s?", transaction, potentialCategories.get(0).getName()));
            if (input) {
                return transactionCategoryStore.put(transaction, potentialCategories.get(0));
            } else {
                final CategorizedTransaction categorizedTransaction = addNewCategory(transaction);
                if (categorizedTransaction != null) {
                    return categorizedTransaction;
                }
            }
        }

        // a bunch of potential matches, select one
        final String input = textIO.newStringInputReader()
                .withIgnoreCase()
                .withNumberedPossibleValues(
                        Stream.concat(potentialCategories.stream().map(Category::getName), Arrays.stream(new String[] {"New Category"}))
                        .collect(Collectors.toList())
                ).read(String.format("\nSelect a category for %s:", transaction));

        final Category existingCategory = potentialCategories.stream()
                .filter(pc -> pc.getName().equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);

        if (existingCategory != null) {
            return transactionCategoryStore.put(transaction, existingCategory);
        } else {
            final CategorizedTransaction categorizedTransaction = addNewCategory(transaction);
            if (categorizedTransaction != null) {
                return categorizedTransaction;
            }
        }

        return null;
    }

    private CategorizedTransaction addNewCategory(Transaction transaction) {
        // TODO: fuzzy match on category names

        // no existing categories that pass the threshold test - prompt for a new one
        final String categoryName = textIO.newStringInputReader()
                .withValueChecker((val, itemName) -> {
                    if (StringUtils.isBlank(val)) {
                        return Collections.singletonList("Category names must not be blank");
                    } else if (val.contains(",")) {
                       return Collections.singletonList("Category names must not contain a comma");
                    }
                    return null;
                })
                .read(String.format("\nPlease enter a new category for %s", transaction));

        return transactionCategoryStore.put(transaction, new Category(categoryName));
    }

}
