package ca.jonathanfritz.ofxcat.datastore;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.DescriptionCategory;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

class DescriptionCategoryDaoTest extends AbstractDatabaseTest {

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

    @Test
    void updateOrInsert_insertsNewMappingWhenNoneExists() throws SQLException {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);

        // Create a category
        final Category groceries = categoryDao.insert(new Category("Groceries")).orElseThrow();

        // No mapping exists for this description yet
        final String description = "WALMART STORE #1234";
        Assertions.assertTrue(descriptionCategoryDao.selectAll().isEmpty());

        // Call updateOrInsert - should insert a new mapping
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Optional<DescriptionCategory> result = descriptionCategoryDao.updateOrInsert(t, description, groceries);

            Assertions.assertTrue(result.isPresent());
            Assertions.assertEquals(description, result.get().getDescription());
            Assertions.assertEquals(groceries.getName(), result.get().getCategory().getName());
        }

        // Verify the mapping was created
        List<DescriptionCategory> all = descriptionCategoryDao.selectAll();
        Assertions.assertEquals(1, all.size());
        Assertions.assertEquals(description, all.getFirst().getDescription());
    }

    @Test
    void updateOrInsert_returnsExistingMappingWhenCategoryMatches() throws SQLException {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);

        // Create a category and an existing mapping
        final Category groceries = categoryDao.insert(new Category("Groceries")).orElseThrow();
        final String description = "COSTCO WHOLESALE";
        final DescriptionCategory existing = descriptionCategoryDao.insert(
                new DescriptionCategory(description, groceries)
        ).orElseThrow();

        // Call updateOrInsert with the same category - should return existing
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Optional<DescriptionCategory> result = descriptionCategoryDao.updateOrInsert(t, description, groceries);

            Assertions.assertTrue(result.isPresent());
            Assertions.assertEquals(existing.getId(), result.get().getId());
            Assertions.assertEquals(groceries.getName(), result.get().getCategory().getName());
        }

        // Verify no new mappings were created
        Assertions.assertEquals(1, descriptionCategoryDao.selectAll().size());
    }

    @Test
    void updateOrInsert_updatesExistingMappingWhenCategoryDiffers() throws SQLException {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);

        // Create two categories
        final Category unknown = categoryDao.insert(new Category("Unknown")).orElseThrow();
        final Category groceries = categoryDao.insert(new Category("Groceries")).orElseThrow();

        // Create an existing mapping with "Unknown" category
        final String description = "LOBLAWS STORE";
        descriptionCategoryDao.insert(new DescriptionCategory(description, unknown));

        // Verify initial state
        Optional<DescriptionCategory> initial = descriptionCategoryDao.selectByDescriptionAndCategory(description, unknown);
        Assertions.assertTrue(initial.isPresent());
        Assertions.assertEquals("UNKNOWN", initial.get().getCategory().getName());

        // Call updateOrInsert with a different category - should update the mapping
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Optional<DescriptionCategory> result = descriptionCategoryDao.updateOrInsert(t, description, groceries);

            Assertions.assertTrue(result.isPresent());
            Assertions.assertEquals("GROCERIES", result.get().getCategory().getName());
        }

        // Verify the mapping was updated (not duplicated)
        List<DescriptionCategory> all = descriptionCategoryDao.selectAll();
        Assertions.assertEquals(1, all.size());
        Assertions.assertEquals("GROCERIES", all.getFirst().getCategory().getName());

        // Old mapping should no longer exist
        Assertions.assertFalse(descriptionCategoryDao.selectByDescriptionAndCategory(description, unknown).isPresent());

        // New mapping should exist
        Assertions.assertTrue(descriptionCategoryDao.selectByDescriptionAndCategory(description, groceries).isPresent());
    }

    @Test
    void updateOrInsert_isCaseInsensitive() throws SQLException {
        final CategoryDao categoryDao = new CategoryDao(connection);
        final DescriptionCategoryDao descriptionCategoryDao = new DescriptionCategoryDao(connection, categoryDao);

        // Create categories
        final Category restaurants = categoryDao.insert(new Category("Restaurants")).orElseThrow();
        final Category fastFood = categoryDao.insert(new Category("Fast Food")).orElseThrow();

        // Create a mapping with lowercase description
        descriptionCategoryDao.insert(new DescriptionCategory("starbucks coffee", restaurants));

        // Call updateOrInsert with uppercase description - should find and update the existing mapping
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            Optional<DescriptionCategory> result = descriptionCategoryDao.updateOrInsert(t, "STARBUCKS COFFEE", fastFood);

            Assertions.assertTrue(result.isPresent());
            Assertions.assertEquals("FAST FOOD", result.get().getCategory().getName());
        }

        // Should still only have one mapping
        Assertions.assertEquals(1, descriptionCategoryDao.selectAll().size());
    }
}