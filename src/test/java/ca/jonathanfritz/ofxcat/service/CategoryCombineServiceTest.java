package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CategoryCombineServiceTest extends AbstractDatabaseTest {

    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private CategoryCombineService categoryCombineService;

    private Account testAccount;

    public CategoryCombineServiceTest() {
        categoryDao = injector.getInstance(CategoryDao.class);
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
    }

    @BeforeEach
    void setUp() {
        testAccount = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        assertNotNull(testAccount);
        categoryCombineService = new CategoryCombineService(connection, categoryDao, categorizedTransactionDao);
    }

    @Test
    void combineMovesAllTransactionsFromSourceToTarget() {
        // Given: two categories with transactions in the source
        Category source = categoryDao.insert(new Category("BANK_FEES")).orElse(null);
        Category target = categoryDao.insert(new Category("BANK FEES")).orElse(null);
        CategorizedTransaction txn1 = insertTransaction("FEE CHARGE 1", source);
        CategorizedTransaction txn2 = insertTransaction("FEE CHARGE 2", source);

        // When: we combine source into target
        CategoryCombineService.CombineResult result = categoryCombineService.combine(
                "BANK_FEES", "BANK FEES", MigrationProgressCallback.NOOP
        );

        // Then: both transactions were moved
        assertEquals(2, result.transactionsMoved());

        // And: transactions now belong to the target category
        CategorizedTransaction updated1 = categorizedTransactionDao.select(txn1.getId()).orElse(null);
        CategorizedTransaction updated2 = categorizedTransactionDao.select(txn2.getId()).orElse(null);
        assertNotNull(updated1);
        assertNotNull(updated2);
        assertEquals(target.getId(), updated1.getCategory().getId());
        assertEquals(target.getId(), updated2.getCategory().getId());
    }

    @Test
    void combineDeletesSourceCategory() {
        // Given: two categories
        Category source = categoryDao.insert(new Category("OLD_NAME")).orElse(null);
        categoryDao.insert(new Category("CORRECT NAME")).orElse(null);

        // When: we combine
        categoryCombineService.combine("OLD_NAME", "CORRECT NAME", MigrationProgressCallback.NOOP);

        // Then: the source category no longer exists
        assertFalse(categoryDao.select(source.getId()).isPresent());
    }

    @Test
    void combineWithEmptySourceCategoryStillDeletesIt() {
        // Given: source category exists but has no transactions
        categoryDao.insert(new Category("EMPTY_CAT")).orElse(null);
        categoryDao.insert(new Category("TARGET_CAT")).orElse(null);

        // When: we combine
        CategoryCombineService.CombineResult result = categoryCombineService.combine(
                "EMPTY_CAT", "TARGET_CAT", MigrationProgressCallback.NOOP
        );

        // Then: 0 transactions moved, source is deleted
        assertEquals(0, result.transactionsMoved());
        assertFalse(categoryDao.select("EMPTY_CAT").isPresent());
    }

    @Test
    void combineFailsWhenSourceDoesNotExist() {
        // Given: only target exists
        categoryDao.insert(new Category("TARGET")).orElse(null);

        // When/Then: combining with nonexistent source throws
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                categoryCombineService.combine("NONEXISTENT", "TARGET", MigrationProgressCallback.NOOP)
        );
        assertTrue(ex.getMessage().contains("NONEXISTENT"));
    }

    @Test
    void combineFailsWhenTargetDoesNotExist() {
        // Given: only source exists
        categoryDao.insert(new Category("SOURCE")).orElse(null);

        // When/Then: combining with nonexistent target throws
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                categoryCombineService.combine("SOURCE", "NONEXISTENT", MigrationProgressCallback.NOOP)
        );
        assertTrue(ex.getMessage().contains("NONEXISTENT"));
    }

    @Test
    void combineFailsWhenSourceAndTargetAreTheSame() {
        // Given: a category
        categoryDao.insert(new Category("SAME")).orElse(null);

        // When/Then: combining a category with itself throws
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                categoryCombineService.combine("SAME", "SAME", MigrationProgressCallback.NOOP)
        );
        assertTrue(ex.getMessage().contains("same"));
    }

    @Test
    void combineReportsCorrectSourceAndTargetNames() {
        // Given: two categories
        categoryDao.insert(new Category("SRC")).orElse(null);
        categoryDao.insert(new Category("DST")).orElse(null);
        insertTransaction("SOME TXN", categoryDao.select("SRC").get());

        // When: we combine
        CategoryCombineService.CombineResult result = categoryCombineService.combine(
                "SRC", "DST", MigrationProgressCallback.NOOP
        );

        // Then: result has correct names
        assertEquals("SRC", result.sourceName());
        assertEquals("DST", result.targetName());
        assertEquals(1, result.transactionsMoved());
    }

    @Test
    void combineDoesNotAffectTransactionsInOtherCategories() {
        // Given: three categories, transactions in source and unrelated
        Category source = categoryDao.insert(new Category("SOURCE")).orElse(null);
        Category target = categoryDao.insert(new Category("TARGET")).orElse(null);
        Category unrelated = categoryDao.insert(new Category("UNRELATED")).orElse(null);
        CategorizedTransaction unrelatedTxn = insertTransaction("UNRELATED TXN", unrelated);

        insertTransaction("SOURCE TXN", source);

        // When: we combine source into target
        categoryCombineService.combine("SOURCE", "TARGET", MigrationProgressCallback.NOOP);

        // Then: unrelated transaction is unchanged
        CategorizedTransaction unchanged = categorizedTransactionDao.select(unrelatedTxn.getId()).orElse(null);
        assertNotNull(unchanged);
        assertEquals(unrelated.getId(), unchanged.getCategory().getId());
    }

    @Test
    void combineInvokesProgressCallback() {
        // Given: source has 3 transactions
        Category source = categoryDao.insert(new Category("SRC")).orElse(null);
        categoryDao.insert(new Category("DST")).orElse(null);
        insertTransaction("TXN 1", source);
        insertTransaction("TXN 2", source);
        insertTransaction("TXN 3", source);

        // When: we combine with a progress tracking callback
        int[] lastProgress = {0, 0};
        categoryCombineService.combine("SRC", "DST", (current, total) -> {
            lastProgress[0] = current;
            lastProgress[1] = total;
        });

        // Then: callback was invoked with final values
        assertEquals(3, lastProgress[0]);
        assertEquals(3, lastProgress[1]);
    }

    private CategorizedTransaction insertTransaction(String description, Category category) {
        Transaction transaction = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(testAccount)
                .setDescription(description)
                .setDate(LocalDate.now())
                .setType(Transaction.TransactionType.DEBIT)
                .setAmount(-25.0f)
                .build();
        return categorizedTransactionDao.insert(new CategorizedTransaction(transaction, category)).orElse(null);
    }
}
