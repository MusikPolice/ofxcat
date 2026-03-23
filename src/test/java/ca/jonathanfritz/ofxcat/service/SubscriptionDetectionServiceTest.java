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
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionDetectionServiceTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final CategoryDao categoryDao;
    private final TransactionTokenDao transactionTokenDao;
    private SubscriptionDetectionService subscriptionDetectionService;

    private Category grocery;
    private Account account;

    private static final LocalDate JAN_01_2024 = LocalDate.of(2024, 1, 1);
    private static final LocalDate DEC_31_2024 = LocalDate.of(2024, 12, 31);

    SubscriptionDetectionServiceTest() {
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
        subscriptionDetectionService = new SubscriptionDetectionService(groupingService, new AppConfig());
    }

    // -- no transactions --

    @Test
    void emptyDateRangeReturnsNoSubscriptions() {
        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertTrue(result.isEmpty());
    }

    // -- min_occurrences threshold --

    @Test
    void fewerThanMinOccurrencesIsNotDetected() {
        // default min_occurrences = 3; insert only 2 transactions
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(30));

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertTrue(result.isEmpty());
    }

    // -- amount tolerance --

    @Test
    void amountVarianceExceedingToleranceIsNotDetected() {
        // default tolerance = 5%; vary by ~15% which exceeds it
        insertWithTokens("NETFLIX.COM", -15.00f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -17.25f, Set.of("netflix", "com"), JAN_01_2024.plusDays(30));
        insertWithTokens("NETFLIX.COM", -15.00f, Set.of("netflix", "com"), JAN_01_2024.plusDays(60));

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertTrue(result.isEmpty());
    }

    @Test
    void amountVarianceWithinToleranceIsDetected() {
        // within 5%: 15.00 * 1.04 = 15.60
        insertWithTokens("NETFLIX.COM", -15.00f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -15.60f, Set.of("netflix", "com"), JAN_01_2024.plusDays(30));
        insertWithTokens("NETFLIX.COM", -15.00f, Set.of("netflix", "com"), JAN_01_2024.plusDays(60));

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertEquals(1, result.size());
    }

    // -- interval detection --

    @Test
    void monthlySubscriptionIsDetected() {
        // Exactly 30-day intervals
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(30));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(60));

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);

        assertEquals(1, result.size());
        assertEquals("MONTHLY", result.get(0).frequency());
        assertEquals("Netflix Com", result.get(0).vendorName());
        assertEquals(-15f, result.get(0).typicalAmount(), 0.01f);
    }

    @Test
    void monthlySubscriptionWithCalendarVarianceIsDetected() {
        // Simulate real monthly billing on the 1st: Jan→Feb→Mar intervals are 31, 29 days (2024 is
        // a leap year). Both are within 3 days of 30.
        insertWithTokens("SPOTIFY PREMIUM", -10f, Set.of("spotify", "premium"), LocalDate.of(2024, 1, 1));
        insertWithTokens("SPOTIFY PREMIUM", -10f, Set.of("spotify", "premium"), LocalDate.of(2024, 2, 1));
        insertWithTokens("SPOTIFY PREMIUM", -10f, Set.of("spotify", "premium"), LocalDate.of(2024, 3, 1));

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);

        assertEquals(1, result.size());
        assertEquals("MONTHLY", result.get(0).frequency());
    }

    @Test
    void annualSubscriptionIsDetected() {
        // Two years' worth of annual charges, 365-day interval
        insertWithTokens("DROPBOX ANNUAL", -120f, Set.of("dropbox", "annual"), LocalDate.of(2023, 1, 1));
        insertWithTokens("DROPBOX ANNUAL", -120f, Set.of("dropbox", "annual"), LocalDate.of(2024, 1, 1));
        insertWithTokens("DROPBOX ANNUAL", -120f, Set.of("dropbox", "annual"), LocalDate.of(2025, 1, 1));

        List<Subscription> result =
                subscriptionDetectionService.detectSubscriptions(LocalDate.of(2023, 1, 1), LocalDate.of(2025, 12, 31));

        assertEquals(1, result.size());
        assertEquals("ANNUAL", result.get(0).frequency());
    }

    @Test
    void weeklySubscriptionIsDetected() {
        insertWithTokens("GYM WEEKLY", -20f, Set.of("gym", "weekly"), JAN_01_2024);
        insertWithTokens("GYM WEEKLY", -20f, Set.of("gym", "weekly"), JAN_01_2024.plusDays(7));
        insertWithTokens("GYM WEEKLY", -20f, Set.of("gym", "weekly"), JAN_01_2024.plusDays(14));

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);

        assertEquals(1, result.size());
        assertEquals("WEEKLY", result.get(0).frequency());
    }

    @Test
    void irregularIntervalsAreNotDetected() {
        // Intervals: 15, 45 days — not consistent with any billing period
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(15));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(60));

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertTrue(result.isEmpty());
    }

    // -- nextExpected computation --

    @Test
    void nextExpectedIsLastChargePlusBillingPeriodDays() {
        LocalDate lastCharge = LocalDate.of(2024, 3, 1);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), LocalDate.of(2024, 2, 1));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), lastCharge);

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);

        assertEquals(1, result.size());
        assertEquals(lastCharge, result.get(0).lastCharge());
        // MONTHLY period = 30 days
        assertEquals(lastCharge.plusDays(30), result.get(0).nextExpected());
    }

    // -- sort order --

    @Test
    void subscriptionsSortedByTypicalAmountAscending() {
        // Netflix: -15 total, Spotify: -10 total
        LocalDate d1 = JAN_01_2024;
        LocalDate d2 = JAN_01_2024.plusDays(30);
        LocalDate d3 = JAN_01_2024.plusDays(60);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), d1);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), d2);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), d3);
        insertWithTokens("SPOTIFY PREMIUM", -10f, Set.of("spotify", "premium"), d1);
        insertWithTokens("SPOTIFY PREMIUM", -10f, Set.of("spotify", "premium"), d2);
        insertWithTokens("SPOTIFY PREMIUM", -10f, Set.of("spotify", "premium"), d3);

        List<Subscription> result = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);

        assertEquals(2, result.size());
        // Netflix is more negative → comes first
        assertEquals("Netflix Com", result.get(0).vendorName());
        assertEquals("Spotify Premium", result.get(1).vendorName());
    }

    // -- configurable thresholds --

    @Test
    void customMinOccurrencesIsRespected() {
        // Insert only 2 transactions; with min_occurrences=2 this should be detected
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(30));

        AppConfig config = new AppConfig();
        config.getSubscriptionDetection().setMinOccurrences(2);
        VendorGroupingService groupingService = new VendorGroupingService(
                categorizedTransactionDao, transactionTokenDao, connection, tokenNormalizer, config);
        SubscriptionDetectionService service = new SubscriptionDetectionService(groupingService, config);

        List<Subscription> result = service.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertEquals(1, result.size());
    }

    @Test
    void customIntervalToleranceIsRespected() {
        // Intervals of 34 days — outside default 3-day tolerance for MONTHLY (30±3=27-33), but within ±5
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024);
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(34));
        insertWithTokens("NETFLIX.COM", -15f, Set.of("netflix", "com"), JAN_01_2024.plusDays(68));

        // Default tolerance: not detected
        List<Subscription> defaultResult = subscriptionDetectionService.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertTrue(defaultResult.isEmpty());

        // Relaxed tolerance of 5: detected
        AppConfig config = new AppConfig();
        config.getSubscriptionDetection().setIntervalToleranceDays(5);
        VendorGroupingService groupingService = new VendorGroupingService(
                categorizedTransactionDao, transactionTokenDao, connection, tokenNormalizer, config);
        SubscriptionDetectionService service = new SubscriptionDetectionService(groupingService, config);

        List<Subscription> relaxedResult = service.detectSubscriptions(JAN_01_2024, DEC_31_2024);
        assertEquals(1, relaxedResult.size());
        assertEquals("MONTHLY", relaxedResult.get(0).frequency());
    }

    // -- helpers --

    private void insertWithTokens(String description, float amount, Set<String> tokens, LocalDate date) {
        Transaction tx = Transaction.newBuilder(UUID.randomUUID().toString())
                .setAccount(account)
                .setDate(date)
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
