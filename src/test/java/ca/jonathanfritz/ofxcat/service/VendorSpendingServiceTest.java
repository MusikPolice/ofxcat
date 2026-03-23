package ca.jonathanfritz.ofxcat.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.config.AppConfig;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import ca.jonathanfritz.ofxcat.datastore.utils.DatabaseTransaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VendorSpendingServiceTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final CategoryDao categoryDao;
    private final TransactionTokenDao transactionTokenDao;
    private VendorSpendingService vendorSpendingService;

    private Category grocery;
    private Account account;

    private static final LocalDate JAN_01 = LocalDate.of(2024, 1, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2024, 12, 31);

    VendorSpendingServiceTest() {
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        categoryDao = injector.getInstance(CategoryDao.class);
        transactionTokenDao = new TransactionTokenDao();
    }

    @BeforeEach
    void setUp() {
        grocery = categoryDao.insert(new Category("GROCERY")).orElseThrow();
        account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        VendorGroupingService groupingService = new VendorGroupingService(
                categorizedTransactionDao, transactionTokenDao, connection, tokenNormalizer, new AppConfig());
        vendorSpendingService = new VendorSpendingService(groupingService);
    }

    @Test
    void getVendorSpend_returnsEmptyListWhenNoTransactions() {
        List<VendorGroup> groups = vendorSpendingService.getVendorSpend(JAN_01, DEC_31);
        assertTrue(groups.isEmpty());
    }

    @Test
    void getVendorSpend_singleVendorGroup() {
        insertWithTokens("SHOPPERS DRUG MART #123", -15f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG MART #456", -20f, Set.of("shoppers", "drug", "mart"));

        List<VendorGroup> groups = vendorSpendingService.getVendorSpend(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals("Shoppers Drug Mart", groups.get(0).displayName());
        assertEquals(2, groups.get(0).transactionCount());
        assertEquals(-35f, groups.get(0).totalAmount(), 0.01f);
    }

    @Test
    void getVendorSpend_multipleGroupsSortedByTotalAmountAscending() {
        // Shoppers: -100 total, Netflix: -30 total
        insertWithTokens("SHOPPERS DRUG MART #123", -50f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG MART #456", -50f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));

        List<VendorGroup> groups = vendorSpendingService.getVendorSpend(JAN_01, DEC_31);

        assertEquals(2, groups.size());
        // Shoppers has larger absolute spend → most negative total → comes first
        assertEquals(-100f, groups.get(0).totalAmount(), 0.01f);
        assertEquals(-30f, groups.get(1).totalAmount(), 0.01f);
    }

    @Test
    void getVendorSpend_excludesTransactionsOutsideDateRange() {
        insertWithTokens("SHOPPERS DRUG MART #123", -50f, Set.of("shoppers", "drug", "mart"));

        // date range that excludes the inserted transaction (JAN_01 was used as the date)
        List<VendorGroup> groups =
                vendorSpendingService.getVendorSpend(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertTrue(groups.isEmpty());
    }

    @Test
    void writeToFile_createsXlsxFile(@TempDir Path tempDir) throws IOException {
        insertWithTokens("SHOPPERS DRUG MART #123", -50f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));

        List<VendorGroup> groups = vendorSpendingService.getVendorSpend(JAN_01, DEC_31);
        Path outputFile = tempDir.resolve("vendors.xlsx");

        Path written = vendorSpendingService.writeToFile(groups, outputFile);

        assertTrue(Files.exists(written));
        assertTrue(Files.size(written) > 0);
    }

    @Test
    void writeToFile_createsParentDirectoriesIfMissing(@TempDir Path tempDir) throws IOException {
        insertWithTokens("SHOPPERS DRUG MART #123", -50f, Set.of("shoppers", "drug", "mart"));
        List<VendorGroup> groups = vendorSpendingService.getVendorSpend(JAN_01, DEC_31);

        Path outputFile = tempDir.resolve("nested/subdir/vendors.xlsx");
        assertFalse(Files.exists(outputFile.getParent()));

        vendorSpendingService.writeToFile(groups, outputFile);

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void writeToFile_createsEmptyReportWhenNoGroups(@TempDir Path tempDir) throws IOException {
        List<VendorGroup> groups = vendorSpendingService.getVendorSpend(JAN_01, DEC_31);
        assertTrue(groups.isEmpty());

        Path outputFile = tempDir.resolve("vendors.xlsx");
        vendorSpendingService.writeToFile(groups, outputFile);

        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);
    }

    // -- helpers --

    private void insertWithTokens(String description, float amount, Set<String> tokens) {
        Transaction tx = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDate(JAN_01)
                .setAmount(amount)
                .setDescription(description)
                .setType(amount < 0 ? Transaction.TransactionType.DEBIT : Transaction.TransactionType.CREDIT)
                .setBalance(1000f + amount)
                .build();
        CategorizedTransaction saved = categorizedTransactionDao
                .insert(new CategorizedTransaction(tx, grocery))
                .orElseThrow();
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, saved.getId(), tokens);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert tokens in test", e);
        }
    }
}
