package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.transactions.Category;
import ca.jonathanfritz.ofxcat.transactions.DescriptionCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class DescriptionCategoryDaoTest extends AbstractDatabaseTest {

    @AfterEach
    void cleanup() {
        // drop all tables and re-init the schema after each test to avoid conflicts
        cleanDatabase();
    }

    @Test
    void insertWithNewCategorySuccessTest() {
        // create a category that will be implicitly inserted into the database
        final Category category = new Category("Famous Horsemen");

        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);

        // insert a DescriptionCategory that references a Category that isn't yet in the database
        final DescriptionCategory descriptionCategoryToInsert = new DescriptionCategory("Horses 'r Us", category);
        final Optional<DescriptionCategory> insertedDescriptionCategoryOptional = descriptionCategoryDao.insert(descriptionCategoryToInsert);
        final DescriptionCategory insertedDescriptionCategory = insertedDescriptionCategoryOptional.get();

        // verify that the DescriptionCategory was created as expected
        Assertions.assertNotNull(insertedDescriptionCategory.getId());
        Assertions.assertEquals(descriptionCategoryToInsert.getDescription(), insertedDescriptionCategory.getDescription());
        Assertions.assertNotNull(insertedDescriptionCategory.getCategory().getId());
        Assertions.assertEquals(descriptionCategoryToInsert.getCategory().getName(), insertedDescriptionCategory.getCategory().getName());

        // verify that the associated Category was created as expected
        final Optional<Category> implicitlyCreatedCategoryOptional = categoryDao.select(insertedDescriptionCategoryOptional.get().getCategory().getId());
        Assertions.assertNotSame(insertedDescriptionCategory.getCategory(), implicitlyCreatedCategoryOptional.get());
        Assertions.assertEquals(insertedDescriptionCategory.getCategory(), implicitlyCreatedCategoryOptional.get());
    }

    @Test
    void insertWithExistingCategorySuccessTest() {
        // create a category that a new DescriptionCategory will reference
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Category category = categoryDao.insert(new Category("Condiments")).get();

        // insert a DescriptionCategory that references that Category
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);
        final DescriptionCategory descriptionCategoryToInsert = new DescriptionCategory("Ketchup 4 Kidz", category);
        final DescriptionCategory insertedDescriptionCategory = descriptionCategoryDao.insert(descriptionCategoryToInsert).get();

        // verify that the DescriptionCategory was created as expected, and that it was linked to the existing Category
        Assertions.assertNotNull(insertedDescriptionCategory.getId());
        Assertions.assertEquals(descriptionCategoryToInsert.getDescription(), insertedDescriptionCategory.getDescription());
        Assertions.assertEquals(category, insertedDescriptionCategory.getCategory());
    }

    @Test
    void selectAllSuccessTest() {
        // create a category that a new DescriptionCategory will reference
        final CategoryDao categoryDao = new CategoryDao(connection);
        final Category category = categoryDao.insert(new Category("Catch These Men")).get();

        // insert two DescriptionCategory objects
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);
        final DescriptionCategory descriptionCategoryToInsert = new DescriptionCategory("Mens' Buy n' Sell", category);
        final DescriptionCategory insertedDescriptionCategory = descriptionCategoryDao.insert(descriptionCategoryToInsert).get();
        final DescriptionCategory descriptionCategoryToInsert2 = new DescriptionCategory("Man Catcher", category);
        final DescriptionCategory insertedDescriptionCategory2 = descriptionCategoryDao.insert(descriptionCategoryToInsert).get();

        // select all and ensure that both are present in the results
        final List<DescriptionCategory> descriptionCategories = descriptionCategoryDao.selectAll();
        Assertions.assertEquals(2, descriptionCategories.size());
        Assertions.assertTrue(descriptionCategories.stream().anyMatch(dc -> dc.equals(insertedDescriptionCategory)));
        Assertions.assertTrue(descriptionCategories.stream().anyMatch(dc -> dc.equals(insertedDescriptionCategory2)));
    }

    @Test
    void selectByDescriptionAndCategorySuccessTest() {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);

        // insert a DescriptionCategory object
        final String descriptionName = "Put it on Wax Records";
        final Category category = new Category("An Album Cover");
        descriptionCategoryDao.insert(new DescriptionCategory(descriptionName, category));

        // find it by description and Category
        final Optional<DescriptionCategory> found = descriptionCategoryDao.selectByDescriptionAndCategory(descriptionName, category);
        Assertions.assertTrue(found.isPresent());

        final Optional<DescriptionCategory> badDescription = descriptionCategoryDao.selectByDescriptionAndCategory("Fake Description", category);
        Assertions.assertFalse(badDescription.isPresent());

        final Optional<DescriptionCategory> badCategory = descriptionCategoryDao.selectByDescriptionAndCategory(descriptionName, new Category("Fake Category"));
        Assertions.assertFalse(badCategory.isPresent());
    }
}