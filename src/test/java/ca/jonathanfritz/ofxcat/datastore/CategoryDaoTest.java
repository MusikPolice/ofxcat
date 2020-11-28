package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
        final Category category1 = categoryDao.insert(new Category("The Pen is Mightier")).get();
        final Category category2 = categoryDao.insert(new Category("Japan US Relations")).get();

        final List<Category> categories = categoryDao.select();
        Assertions.assertEquals(2, categories.size());
        Assertions.assertTrue(categories.stream().anyMatch(c -> c.equals(category1)));
        Assertions.assertTrue(categories.stream().anyMatch(c -> c.equals(category2)));
    }
}