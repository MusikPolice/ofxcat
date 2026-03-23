package ca.jonathanfritz.ofxcat.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import ca.jonathanfritz.ofxcat.model.VendorGroup;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VendorGroupingServiceTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final CategoryDao categoryDao;
    private final TransactionTokenDao transactionTokenDao;
    private VendorGroupingService vendorGroupingService;

    private Category grocery;
    private Account account;

    private static final LocalDate JAN_01 = LocalDate.of(2024, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2024, 1, 31);
    private static final LocalDate DEC_31 = LocalDate.of(2024, 12, 31);

    VendorGroupingServiceTest() {
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        categoryDao = injector.getInstance(CategoryDao.class);
        transactionTokenDao = new TransactionTokenDao();
    }

    @BeforeEach
    void setUp() {
        grocery = categoryDao.insert(new Category("GROCERY")).orElseThrow();
        account = accountDao.insert(TestUtils.createRandomAccount()).orElseThrow();
        vendorGroupingService = new VendorGroupingService(
                categorizedTransactionDao, transactionTokenDao, connection, tokenNormalizer, new AppConfig());
    }

    // -- groupByVendor: basic grouping --

    @Test
    void emptyDateRangeReturnsNoGroups() {
        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, JAN_31);
        assertTrue(groups.isEmpty());
    }

    @Test
    void singleTransactionFormsSingleGroup() {
        insertWithTokens("SHOPPERS DRUG MART #123", -15f, Set.of("shoppers", "drug", "mart"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).transactionCount());
    }

    @Test
    void transactionsWithIdenticalTokensGroupTogether() {
        insertWithTokens("SHOPPERS DRUG MART #123", -15f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG MART #456", -22f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG MART TORONTO", -8f, Set.of("shoppers", "drug", "mart"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).transactionCount());
        assertEquals(-45f, groups.get(0).totalAmount(), 0.01f);
    }

    @Test
    void transactionsWithSufficientOverlapGroupTogether() {
        // 2 of 3 tokens overlap → ratio = 2/2 = 1.0 (above default 0.6 threshold)
        insertWithTokens("NETFLIX MONTHLY CA", -15f, Set.of("netflix", "monthly", "ca"));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        // "netflix" appears in both; overlap = 1/min(3,2) = 1/2 = 0.5 which is below default 0.6
        // so these should be separate groups
        assertEquals(2, groups.size());
    }

    @Test
    void transactionsWithInsufficientOverlapFormSeparateGroups() {
        // No shared tokens → no overlap → separate groups
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));
        insertWithTokens("SPOTIFY PREMIUM", -10f, Set.of("spotify", "premium"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(2, groups.size());
    }

    @Test
    void transitiveClusteringGroupsConnectedTransactions() {
        // A shares tokens with B, B shares tokens with C, but A and C share no tokens.
        // All three should end up in one group.
        insertWithTokens("SHOPPERS DRUG MART", -10f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG TORONTO", -20f, Set.of("shoppers", "drug", "toronto"));
        insertWithTokens("DRUG TORONTO LOCATION", -30f, Set.of("drug", "toronto", "location"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).transactionCount());
    }

    // -- groupByVendor: excluded categories --

    @Test
    void transferTransactionsAreExcluded() {
        insertWithTokens("TRANSFER TO SAVINGS", -500f, Set.of("transfer", "savings"), Category.TRANSFER);
        insertWithTokens("SHOPPERS DRUG MART #123", -15f, Set.of("shoppers", "drug", "mart"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals("Shoppers Drug Mart", groups.get(0).displayName());
    }

    @Test
    void unknownTransactionsAreExcluded() {
        insertWithTokens("MYSTERY CHARGE 99", -99f, Set.of("mystery", "charge"), Category.UNKNOWN);
        insertWithTokens("SHOPPERS DRUG MART #123", -15f, Set.of("shoppers", "drug", "mart"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals("Shoppers Drug Mart", groups.get(0).displayName());
    }

    // -- groupByVendor: display name reconstruction --

    @Test
    void displayNameForSingleTokenVendorIsTitleCased() {
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));
        insertWithTokens("NETFLIX *MONTHLY", -15f, Set.of("netflix", "monthly"));

        // "netflix" is the only core token (appears in both; "com" and "monthly" appear in only one)
        // overlap between these two: shared=1 ("netflix"), min(2,2)=2 → ratio=0.5 < 0.6 → separate groups
        // so each forms its own group; display name for the .COM one uses its core token
        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        List<String> names = groups.stream().map(VendorGroup::displayName).toList();
        assertThat(names, containsInAnyOrder("Netflix Com", "Netflix Monthly"));
    }

    @Test
    void displayNameForMultiTokenVendorReconstrucksTokenOrder() {
        // Core tokens: shoppers, drug, mart (all three appear in all transactions)
        insertWithTokens("SHOPPERS DRUG MART #123", -15f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG MART #456", -20f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG MART TORONTO", -8f, Set.of("shoppers", "drug", "mart"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals("Shoppers Drug Mart", groups.get(0).displayName());
    }

    @Test
    void stopWordsAreExcludedFromDisplayName() {
        // "THE" is a stop word stripped by TokenNormalizer; core tokens are {home, depot}
        insertWithTokens("THE HOME DEPOT #4201", -80f, Set.of("home", "depot"));
        insertWithTokens("HOME DEPOT ONLINE", -50f, Set.of("home", "depot"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(1, groups.size());
        assertEquals("Home Depot", groups.get(0).displayName());
    }

    // -- groupByVendor: sort order --

    @Test
    void groupsSortedByTotalAmountAscending() {
        // Netflix: total -30, Shoppers: total -100
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"));
        insertWithTokens("SHOPPERS DRUG MART #123", -50f, Set.of("shoppers", "drug", "mart"));
        insertWithTokens("SHOPPERS DRUG MART #456", -50f, Set.of("shoppers", "drug", "mart"));

        List<VendorGroup> groups = vendorGroupingService.groupByVendor(JAN_01, DEC_31);

        assertEquals(2, groups.size());
        // Shoppers has larger spend (more negative total) → comes first
        assertEquals("Shoppers Drug Mart", groups.get(0).displayName());
        assertEquals(-100f, groups.get(0).totalAmount(), 0.01f);
        assertEquals(-30f, groups.get(1).totalAmount(), 0.01f);
    }

    // -- helpers --

    private CategorizedTransaction insertWithTokens(String description, float amount, Set<String> tokens) {
        return insertWithTokens(description, amount, tokens, grocery);
    }

    private CategorizedTransaction insertWithTokens(
            String description, float amount, Set<String> tokens, Category category) {
        Transaction tx = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDate(JAN_01)
                .setAmount(amount)
                .setDescription(description)
                .setType(amount < 0 ? Transaction.TransactionType.DEBIT : Transaction.TransactionType.CREDIT)
                .setBalance(1000f + amount)
                .build();
        CategorizedTransaction saved = categorizedTransactionDao
                .insert(new CategorizedTransaction(tx, category))
                .orElseThrow();
        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            transactionTokenDao.insertTokens(t, saved.getId(), tokens);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert tokens in test", e);
        }
        return saved;
    }
}
