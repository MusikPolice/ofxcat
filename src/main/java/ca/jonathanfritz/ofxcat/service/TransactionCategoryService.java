package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.DescriptionCategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.DescriptionCategory;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import com.google.inject.Inject;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class TransactionCategoryService {

    private final CategoryDao categoryDao;
    private final DescriptionCategoryDao descriptionCategoryDao;

    private static final Logger logger = LoggerFactory.getLogger(TransactionCategoryService.class);

    @Inject
    public TransactionCategoryService(CategoryDao categoryDao, DescriptionCategoryDao descriptionCategoryDao) {
        this.categoryDao = categoryDao;
        this.descriptionCategoryDao = descriptionCategoryDao;
    }

    /**
     * Maps the specified transaction's description to the specified category
     */
    public CategorizedTransaction put(Transaction newTransaction, Category newCategory) {
        // if a DescriptionCategory with the specified description and category exists, use it. Otherwise, insert one
        final DescriptionCategory descriptionCategory = descriptionCategoryDao.selectByDescriptionAndCategory(newTransaction.getDescription(), newCategory)
                .or(() -> {
                    logger.debug("Implicitly creating DescriptionCategory with description {} and Category {}", newTransaction.getDescription(), newCategory);
                    return descriptionCategoryDao.insert(new DescriptionCategory(newTransaction.getDescription(), newCategory));
                }).get();

        return new CategorizedTransaction(newTransaction, descriptionCategory.getCategory());
    }

    /**
     * Converts the specified {@link Transaction} into an instance of {@link CategorizedTransaction}, assigning a
     * {@link Category} if one exists whose name exactly matches the specified transaction's description.
     */
    public Optional<CategorizedTransaction> getCategoryExact(Transaction transaction) {
        final Map<String, Category> descriptionCategories = descriptionCategoryDao.selectAll().stream()
                .collect(Collectors.toMap(DescriptionCategory::getDescription, DescriptionCategory::getCategory));

        return descriptionCategories.entrySet()
                .parallelStream()
                .filter(es -> es.getKey().equalsIgnoreCase(transaction.getDescription()))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(category -> new CategorizedTransaction(transaction, category));
    }

    /**
     * Returns a list of categories whose names most closely match the description of the specified transaction.
     * Once a category is selected from the returned set, it must be associated to the specified transaction by way
     * of the {@link #put(Transaction, Category)} method.
     * @param transaction the transaction to find potential category matches for.
     * @param limit the max number of potential categories to return
     * @return a list of potential {@link Category} matches, sorted by relevance descending
     */
    public List<Category> getCategoryFuzzy(Transaction transaction, int limit) {
        // TODO: is 80% match good enough? - make this configurable
        logger.debug("Fuzzy category matches for transaction description \"{}\":", transaction.getDescription());
        final Map<String, Category> descriptionCategories = descriptionCategoryDao.selectAll().stream()
                .collect(Collectors.toMap(DescriptionCategory::getDescription, DescriptionCategory::getCategory));

        return FuzzySearch.extractSorted(transaction.getDescription(), descriptionCategories.keySet(), 80)
                .stream()
                .sorted((o1, o2) -> o2.getScore() - o1.getScore()) // sort descending
                .map(er -> {
                    logger.debug("{}: {}", er.getString(), er.getScore());
                    return descriptionCategories.get(er.getString());
                })
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns an alphabetically sorted list of all known category names
     */
    public List<String> getCategoryNames() {
        return categoryDao.select().stream()
                .map(Category::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
