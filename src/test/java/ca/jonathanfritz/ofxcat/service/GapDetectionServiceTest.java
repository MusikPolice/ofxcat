package ca.jonathanfritz.ofxcat.service;

import static org.junit.jupiter.api.Assertions.*;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GapDetectionServiceTest extends AbstractDatabaseTest {

    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final CategoryDao categoryDao;
    private GapDetectionService gapDetectionService;

    private Category testCategory;

    GapDetectionServiceTest() {
        accountDao = injector.getInstance(AccountDao.class);
        categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        categoryDao = injector.getInstance(CategoryDao.class);
    }

    @BeforeEach
    void setUp() {
        gapDetectionService = new GapDetectionService(accountDao, categorizedTransactionDao);
        testCategory = categoryDao.insert(new Category("TEST")).orElse(null);
    }

    private CategorizedTransaction insert(Account account, LocalDate date, float amount, float balance) {
        return categorizedTransactionDao
                .insert(new CategorizedTransaction(
                        TestUtils.createTransactionWithBalance(account, date, amount, balance), testCategory))
                .orElse(null);
    }

    // --- detectGaps(Account) ---

    @Test
    void noTransactionsProducesNoGaps() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        assertTrue(gapDetectionService.detectGaps(account).isEmpty());
    }

    @Test
    void oneTransactionProducesNoGaps() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        insert(account, LocalDate.of(2023, 1, 1), -50f, 950f);
        assertTrue(gapDetectionService.detectGaps(account).isEmpty());
    }

    @Test
    void twoConsecutiveTransactionsWithNoGap() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // balance[1] = 950, balance[2] = 950 + (-30) = 920 ✓
        insert(account, LocalDate.of(2023, 1, 1), -50f, 950f);
        insert(account, LocalDate.of(2023, 1, 5), -30f, 920f);
        assertTrue(gapDetectionService.detectGaps(account).isEmpty());
    }

    @Test
    void twoTransactionsWithGapProducesOneGap() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        LocalDate date1 = LocalDate.of(2023, 1, 15);
        LocalDate date2 = LocalDate.of(2023, 2, 1);
        insert(account, date1, -50f, 950f);
        // expected balance = 950 + (-30) = 920, actual = 850 → missingAmount = 850 - 920 = -70
        insert(account, date2, -30f, 850f);

        List<GapDetectionService.Gap> gaps = gapDetectionService.detectGaps(account);
        assertEquals(1, gaps.size());
        GapDetectionService.Gap gap = gaps.get(0);
        assertEquals(account, gap.account());
        assertEquals(date1, gap.lastGoodDate());
        assertEquals(date2, gap.firstDateAfterGap());
        assertEquals(-70f, gap.missingAmount(), 0.001f);
    }

    @Test
    void gapAtMonthBoundary() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        LocalDate lastOfJan = LocalDate.of(2023, 1, 31);
        LocalDate firstOfFeb = LocalDate.of(2023, 2, 1);
        insert(account, lastOfJan, -50f, 950f);
        insert(account, firstOfFeb, -30f, 850f); // expected 920, actual 850 → gap

        List<GapDetectionService.Gap> gaps = gapDetectionService.detectGaps(account);
        assertEquals(1, gaps.size());
        assertEquals(lastOfJan, gaps.get(0).lastGoodDate());
        assertEquals(firstOfFeb, gaps.get(0).firstDateAfterGap());
    }

    @Test
    void multipleGapsInOneAccount() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // Jan→Feb: no gap (150 - 30 = 120 ✓)
        insert(account, LocalDate.of(2023, 1, 1), -50f, 150f);
        insert(account, LocalDate.of(2023, 2, 1), -30f, 120f);
        // Feb→Mar: gap (expected 120 - 20 = 100, actual 50 → missingAmount = -50)
        insert(account, LocalDate.of(2023, 3, 1), -20f, 50f);
        // Mar→Apr: no gap (50 - 10 = 40 ✓)
        insert(account, LocalDate.of(2023, 4, 1), -10f, 40f);
        // Apr→May: gap (expected 40 - 5 = 35, actual 10 → missingAmount = -25)
        insert(account, LocalDate.of(2023, 5, 1), -5f, 10f);

        List<GapDetectionService.Gap> gaps = gapDetectionService.detectGaps(account);
        assertEquals(2, gaps.size());
    }

    @Test
    void sameDayTransactionsWithNoGap() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        LocalDate sameDay = LocalDate.of(2023, 1, 15);
        // balance[1]=950, balance[2]=950+(-30)=920 ✓
        insert(account, sameDay, -50f, 950f);
        insert(account, sameDay, -30f, 920f);
        assertTrue(gapDetectionService.detectGaps(account).isEmpty());
    }

    @Test
    void sameDayTransactionsWithBalanceDiscrepancyProducesNoGap() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        LocalDate sameDay = LocalDate.of(2023, 1, 15);
        // expected balance[2] = 950 + (-30) = 920, but stored as 800 — simulates the import-ordering
        // artifact that occurs when two OFX exports overlap on this date and list transactions in
        // different sequences. Same-day pairs are excluded from the invariant check.
        insert(account, sameDay, -50f, 950f);
        insert(account, sameDay, -30f, 800f);
        assertTrue(gapDetectionService.detectGaps(account).isEmpty());
    }

    // --- detectGaps() / detectGaps(ProgressCallback) ---

    @Test
    void gapInOneAccountNotInOther() {
        Account accountA = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        Account accountB = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // Account A has a gap
        insert(accountA, LocalDate.of(2023, 1, 1), -50f, 950f);
        insert(accountA, LocalDate.of(2023, 2, 1), -30f, 850f); // gap
        // Account B has no gap
        insert(accountB, LocalDate.of(2023, 1, 1), -50f, 950f);
        insert(accountB, LocalDate.of(2023, 2, 1), -30f, 920f); // 950-30=920 ✓

        List<GapDetectionService.Gap> gaps = gapDetectionService.detectGaps();
        assertEquals(1, gaps.size());
        assertEquals(accountA, gaps.get(0).account());
    }

    @Test
    void progressCallbackInvokedOncePerAccount() {
        Account accountA = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        Account accountB = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        insert(accountA, LocalDate.of(2023, 1, 1), -50f, 950f);
        insert(accountB, LocalDate.of(2023, 1, 1), -50f, 950f);

        List<int[]> calls = new ArrayList<>();
        gapDetectionService.detectGaps((current, total) -> calls.add(new int[] {current, total}));

        assertEquals(2, calls.size());
        assertArrayEquals(new int[] {1, 2}, calls.get(0));
        assertArrayEquals(new int[] {2, 2}, calls.get(1));
    }

    // --- gapAmountsByMonth ---

    @Test
    void gapAmountsByMonthReturnsGapInRange() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // Gap starts in Jan 2023 (lastGoodDate=Jan 15); missingAmount = 850 - 920 = -70
        insert(account, LocalDate.of(2023, 1, 15), -50f, 950f);
        insert(account, LocalDate.of(2023, 3, 1), -30f, 850f);

        Map<LocalDate, Float> result =
                gapDetectionService.gapAmountsByMonth(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 30));

        assertEquals(1, result.size());
        assertTrue(result.containsKey(LocalDate.of(2023, 1, 1)));
        assertEquals(-70f, result.get(LocalDate.of(2023, 1, 1)), 0.001f);
    }

    @Test
    void gapAmountsByMonthExcludesGapsOutsideRange() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // Gap starts in Jan 2023
        insert(account, LocalDate.of(2023, 1, 15), -50f, 950f);
        insert(account, LocalDate.of(2023, 3, 1), -30f, 850f);

        // Query range starts in Feb — Jan gap should be excluded
        Map<LocalDate, Float> result =
                gapDetectionService.gapAmountsByMonth(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 6, 30));

        assertTrue(result.isEmpty());
    }

    // --- fullyMissingMonths ---

    @Test
    void fullyMissingMonthsIdentifiesMonthsEntirelyWithinGap() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // Gap: lastGoodDate=Jan 15, firstDateAfterGap=Apr 1 → Feb and Mar are fully missing
        insert(account, LocalDate.of(2023, 1, 15), -50f, 950f);
        insert(account, LocalDate.of(2023, 4, 1), -30f, 850f);

        Set<LocalDate> result =
                gapDetectionService.fullyMissingMonths(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 4, 30));

        assertEquals(Set.of(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 3, 1)), result);
    }

    @Test
    void fullyMissingMonthsExcludesMonthWithTransactionMidMonth() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // Gap: lastGoodDate=Jan 15, firstDateAfterGap=Feb 15 → no month is fully missing
        insert(account, LocalDate.of(2023, 1, 15), -50f, 950f);
        insert(account, LocalDate.of(2023, 2, 15), -30f, 850f);

        Set<LocalDate> result =
                gapDetectionService.fullyMissingMonths(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 3, 31));

        assertTrue(result.isEmpty());
    }

    // --- indeterminateAccounts ---

    @Test
    void indeterminateAccountsReturnsAccountsWithFewerThanTwoTransactions() {
        Account zeroTxn = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        Account oneTxn = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        Account twoTxn = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        insert(oneTxn, LocalDate.of(2023, 1, 1), -50f, 950f);
        insert(twoTxn, LocalDate.of(2023, 1, 1), -50f, 950f);
        insert(twoTxn, LocalDate.of(2023, 2, 1), -30f, 920f);

        List<Account> result = gapDetectionService.indeterminateAccounts();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(zeroTxn.getId())));
        assertTrue(result.stream().anyMatch(a -> a.getId().equals(oneTxn.getId())));
        assertFalse(result.stream().anyMatch(a -> a.getId().equals(twoTxn.getId())));
    }

    @Test
    void fullyMissingMonthsReturnsEmptyWhenNoGaps() {
        Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        insert(account, LocalDate.of(2023, 1, 1), -50f, 950f);
        insert(account, LocalDate.of(2023, 2, 1), -30f, 920f); // no gap

        Set<LocalDate> result =
                gapDetectionService.fullyMissingMonths(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 30));

        assertTrue(result.isEmpty());
    }
}
