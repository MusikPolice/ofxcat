package ca.jonathanfritz.ofxcat.transactions;
import me.xdrop.fuzzywuzzy.FuzzySearch;

import java.util.*;
import java.util.stream.Collectors;

public class TransactionCategoryStore {

    // TODO: persist this to some sort of data store between executions so that knowledge is gained over time
    private final Map<String, Category> descriptionCategories = new HashMap<>();
    private final Set<Category> categories = new HashSet<>();

    /**
     * Maps the specified transaction's description to the specified category
     */
    public CategorizedTransaction put(Transaction newTransaction, Category newCategory) {
        // to ensure that we don't have duplicate categories in the system, we'll check if there is an existing category
        // that has the same name as the supplied category
        Category singletonCategory = categories.stream()
                .filter(c -> c.equals(newCategory))
                .findFirst()
                .orElse(newCategory);
        categories.add(singletonCategory);

        // record the association between the transaction's description and the category
        descriptionCategories.put(newTransaction.getDescription(), singletonCategory);
        return new CategorizedTransaction(newTransaction, newCategory);
    }

    /**
     * Returns the category that exactly matches the specified transaction's description, or else null
     */
    public CategorizedTransaction getCategoryExact(Transaction transaction) {
        final Category category = descriptionCategories.entrySet()
                .parallelStream()
                .filter(es -> es.getKey().equalsIgnoreCase(transaction.getDescription()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (category != null) {
            return new CategorizedTransaction(transaction, category);
        }
        return null;
    }

    /**
     * Finds the top limit categories that most closely match the description of the specified transaction.
     * Once a category is selected from the returned set, it must be associated to the specified transaction by way
     * of the {@link #put(Transaction, Category)} method.
     */
    public List<Category> getCategoryFuzzy(Transaction transaction, int limit) {
        // TODO: is 80% match good enough?
        return FuzzySearch.extractSorted(transaction.getDescription(), descriptionCategories.keySet(), 80)
                .parallelStream()
                .map(er -> descriptionCategories.get(er.getString()))
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Set<Category> getCategories() {
        return new HashSet<>(categories);
    }

    public List<String> getCategoryNames() {
        return categories
                .stream()
                .map(Category::getName)
                .sorted()
                .collect(Collectors.toList());
    }
}
