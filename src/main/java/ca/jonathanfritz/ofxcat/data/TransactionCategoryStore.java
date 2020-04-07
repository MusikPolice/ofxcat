package ca.jonathanfritz.ofxcat.data;

import ca.jonathanfritz.ofxcat.transactions.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.Transaction;
import ca.jonathanfritz.ofxcat.utils.PathUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
public class TransactionCategoryStore {

    private final ObjectMapper objectMapper;
    private final PathUtils pathUtils;

    private final Map<String, Category> descriptionCategories = new HashMap<>();
    private final Set<Category> categories = new HashSet<>();

    private static final Logger logger = LoggerFactory.getLogger(TransactionCategoryStore.class);

    @Inject
    public TransactionCategoryStore(ObjectMapper objectMapper, PathUtils pathUtils) throws IOException {
        this.objectMapper = objectMapper;
        this.pathUtils = pathUtils;
        load();
    }

    /**
     * Allows tests to load from disk
     */
    void load() throws IOException {
        final Path transactionCategoryStorePath = pathUtils.getTransactionCategoryStorePath();
        if (!Files.exists(transactionCategoryStorePath)) {
            logger.info("No existing transaction categories in {}", transactionCategoryStorePath);
            return;
        }
        logger.info("Attempting to load existing transaction categories from {}", transactionCategoryStorePath);

        // make sure that we can open the file
        if (!Files.isReadable(transactionCategoryStorePath)) {
            throw new IOException(String.format("Transaction category store file %s is not readable", transactionCategoryStorePath.toString()));
        } else if (!Files.isWritable(transactionCategoryStorePath)) {
            throw new IOException(String.format("Transaction category store file %s is not writable", transactionCategoryStorePath.toString()));
        }

        final TransactionCategory deserialized = objectMapper.readValue(transactionCategoryStorePath.toFile(), TransactionCategory.class);
        this.descriptionCategories.putAll(deserialized.descriptionCategories);
        this.categories.addAll(descriptionCategories.values());
        logger.info("Successfully loaded existing categories {}", getCategoryNames());
    }

    /**
     * Allows tests to clear the store
     */
    void clear() {
        logger.debug("TransactionCategoryStore.clear() called");
        descriptionCategories.clear();
        categories.clear();
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
    public List<String> getCategoryNames() {
        return categories
                .parallelStream()
                .map(Category::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Saves the contents of the store to disk
     * @throws IOException thrown if something goes wrong
     */
    public void save() throws IOException {
        final TransactionCategory toSerialize = new TransactionCategory(this.descriptionCategories);
        Files.write(pathUtils.getTransactionCategoryStorePath(), objectMapper.writeValueAsBytes(toSerialize));
        logger.info("Successfully saved transaction category store to {}", pathUtils.getTransactionCategoryStorePath());
    }

    /**
     * When {@link TransactionCategoryStore} is serialized to disk, it is represented as this JSON object
     */
    private static class TransactionCategory {

        private final Map<String, Category> descriptionCategories;

        @JsonCreator
        TransactionCategory(@JsonProperty("descriptionCategories") Map<String, Category> descriptionCategories) {
            this.descriptionCategories = descriptionCategories;
        }

        @JsonProperty
        Map<String, Category> getDescriptionCategories() {
            return descriptionCategories;
        }
    }
}
