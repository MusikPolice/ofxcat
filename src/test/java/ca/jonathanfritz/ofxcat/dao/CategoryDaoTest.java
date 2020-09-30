package ca.jonathanfritz.ofxcat.dao;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.transactions.Category;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class CategoryDaoTest extends AbstractDatabaseTest {

    @Test
    void selectSuccessTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);

        // create a category that will be inserted into the database
        final Category newCategory = new Category("Potent Potables");

        // insert the category object
        final Optional<Category> insertedCategoryOptional = categoryDao.insert(newCategory);
        Assertions.assertTrue(insertedCategoryOptional.isPresent());
        final Category insertedCategory = insertedCategoryOptional.get();

        // get the inserted object out of the database
        final Optional<Category> selectedCategoryOptional = categoryDao.select(insertedCategory.getId());
        Assertions.assertTrue(selectedCategoryOptional.isPresent());
        final Category selectedCategory = selectedCategoryOptional.get();

        // make sure that they are the same object, but that they are logically equal
        Assertions.assertEquals(newCategory.getName(), insertedCategory.getName());
        Assertions.assertNotSame(insertedCategory, selectedCategory);

        // TODO: this assertion fails with the two objects having different ids... were two inserted?
        Assertions.assertEquals(insertedCategory, selectedCategory);
    }

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
}