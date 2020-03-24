package ca.jonathanfritz.ofxcat.transactions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.xdrop.fuzzywuzzy.FuzzySearch;

import java.util.*;
import java.util.stream.Collectors;
public class TransactionCategoryStore {

    private final Map<String, Category> descriptionCategories;
    private final Set<Category> categories;

    @JsonCreator
    public TransactionCategoryStore(@JsonProperty("categoryStore") Map<String, Category> descriptionCategories) {
        this.descriptionCategories = descriptionCategories;
        this.categories = descriptionCategories != null ? new HashSet<>(descriptionCategories.values()) : new HashSet<>();
    }

    /**
     * Maps the specified transaction's description to the specified category
     */
    public CategorizedTransaction put(Transaction newTransaction, Category newCategory) {
        // to ensure that we don't have duplicate categories in the system, we'll check if there is an existing category
        // that has the same name as the supplied category
        final Category singletonCategory = categories.stream()
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
    public Optional<CategorizedTransaction> getCategoryExact(Transaction transaction) {
        return descriptionCategories.entrySet()
                .parallelStream()
                .filter(es -> es.getKey().equalsIgnoreCase(transaction.getDescription()))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(category -> new CategorizedTransaction(transaction, category));
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

    /**
     * Returns an alphabetically sorted list of all known category names
     */
    @JsonIgnore
    public List<String> getCategoryNames() {
        return categories
                .parallelStream()
                .map(Category::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns a copy of the internal map of transaction name to category
     * For serialization purposes only
     */
    @JsonProperty("categoryStore")
    public Map<String, Category> getDescriptionCategories() {
        // return a copy so nobody can mess with our internal representation
        return new HashMap<>(descriptionCategories);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionCategoryStore that = (TransactionCategoryStore) o;
        return Objects.equals(descriptionCategories, that.descriptionCategories) &&
                Objects.equals(categories, that.categories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptionCategories, categories);
    }
}
