package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// TODO: mock connection to test failure cases
class CategoryDaoTest extends AbstractDatabaseTest {

    @Test
    void insertSuccessTest() {
        // create a category that will be inserted into the database
        final Category category = new Category("Potent Potables");

        // connect to the database and insert the category object
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Optional<Category> optional = categoryDao.insert(category);

        // verify that the returned object is the same as the inserted object, and that it has been assigned a unique id
        if (optional.isPresent()) {
            final Category inserted = optional.get();
            Assertions.assertEquals(category.getName(), inserted.getName(), "Category names do not match");
            Assertions.assertNotNull(inserted.getId(), "Inserted Category does not have a unique id");
        } else {
            Assertions.fail("Inserted Category was not returned");
        }
    }

    @Test
    void selectSuccessTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // create a category that will be inserted into the database
        final Category newCategory = new Category("Therapists");

        // insert the category object
        final Optional<Category> insertedCategoryOptional = categoryDao.insert(newCategory);
        Assertions.assertTrue(insertedCategoryOptional.isPresent());
        final Category insertedCategory = insertedCategoryOptional.get();

        // get the inserted object out of the database
        final Optional<Category> selectedCategoryOptional = categoryDao.select(insertedCategory.getId());
        Assertions.assertTrue(selectedCategoryOptional.isPresent());
        final Category selectedCategory = selectedCategoryOptional.get();

        // make sure that they are not the same object, but that they are logically equal
        Assertions.assertNotSame(insertedCategory, selectedCategory);
        Assertions.assertEquals(insertedCategory, selectedCategory);
    }

    @Test
    void selectByNameSuccessTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // create a category that will be inserted into the database
        final String categoryName = "S Words";
        final Category newCategory = new Category(categoryName);

        // insert the category object - this is the first transaction
        final Optional<Category> insertedCategoryOptional = categoryDao.insert(newCategory);
        Assertions.assertTrue(insertedCategoryOptional.isPresent());
        final Category insertedCategory = insertedCategoryOptional.get();

        // get the inserted object by name - this is a second transaction that takes place after the first is committed
        final Optional<Category> selectedCategoryOptional = categoryDao.select(categoryName);
        Assertions.assertTrue(selectedCategoryOptional.isPresent());
        final Category selectedCategory = selectedCategoryOptional.get();

        // make sure that they are not the same object, but that they are logically equal
        Assertions.assertNotSame(insertedCategory, selectedCategory);
        Assertions.assertEquals(insertedCategory, selectedCategory);
    }

    @Test
    void selectAllSuccessTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Category category1 =
                categoryDao.insert(new Category("The Pen is Mightier")).get();
        final Category category2 =
                categoryDao.insert(new Category("Japan US Relations")).get();

        final List<Category> categories = categoryDao.select();

        // the default UNKNOWN and TRANSFER categories will be returned, along with the two new categories that were
        // inserted
        Assertions.assertEquals(4, categories.size());
        Assertions.assertTrue(categories.stream().anyMatch(c -> c.equals(Category.UNKNOWN)));
        Assertions.assertTrue(categories.stream().anyMatch(c -> c.equals(Category.TRANSFER)));
        Assertions.assertTrue(categories.stream().anyMatch(c -> c.equals(category1)));
        Assertions.assertTrue(categories.stream().anyMatch(c -> c.equals(category2)));
    }

    @Test
    void getOrCreateReturnsExistingCategory() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // First, insert a category
        final Category inserted = categoryDao.insert(new Category("GROCERIES")).get();

        // Now use getOrCreate with the same name
        final Optional<Category> result = categoryDao.getOrCreate("GROCERIES");

        // Should return the existing category, not create a new one
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(inserted.getId(), result.get().getId());
        Assertions.assertEquals(inserted.getName(), result.get().getName());
    }

    @Test
    void getOrCreateCreatesNewCategory() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // Use getOrCreate with a name that doesn't exist
        final Optional<Category> result = categoryDao.getOrCreate("NEW_CATEGORY");

        // Should create and return the new category
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals("NEW_CATEGORY", result.get().getName());
        Assertions.assertNotNull(result.get().getId());

        // Verify it was actually saved to the database
        final Optional<Category> fromDb = categoryDao.select("NEW_CATEGORY");
        Assertions.assertTrue(fromDb.isPresent());
        Assertions.assertEquals(result.get(), fromDb.get());
    }

    @Test
    void getOrCreateIsCaseInsensitive() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // Insert a category with uppercase name
        final Category inserted =
                categoryDao.insert(new Category("RESTAURANTS")).get();

        // Use getOrCreate with different case
        final Optional<Category> result = categoryDao.getOrCreate("restaurants");

        // Should return the existing category
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(inserted.getId(), result.get().getId());
    }

    @Test
    void deleteSuccessTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // insert a category, then delete it
        final Category inserted =
                categoryDao.insert(new Category("Doomed Category")).get();
        final boolean deleted = categoryDao.delete(inserted.getId());

        // verify it was deleted
        Assertions.assertTrue(deleted);
        Assertions.assertFalse(categoryDao.select(inserted.getId()).isPresent());
    }

    @Test
    void deleteNonExistentCategoryReturnsFalse() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // attempt to delete a category that doesn't exist
        final boolean deleted = categoryDao.delete(99999L);

        Assertions.assertFalse(deleted);
    }

    @Test
    void getOrCreateReturnsDefaultCategoriesIfQueried() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // The UNKNOWN category should already exist from migrations
        final Optional<Category> unknown = categoryDao.getOrCreate("UNKNOWN");

        Assertions.assertTrue(unknown.isPresent());
        Assertions.assertEquals(Category.UNKNOWN.getName(), unknown.get().getName());
    }
}
