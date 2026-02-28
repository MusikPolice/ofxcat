package ca.jonathanfritz.ofxcat.service;

import static ca.jonathanfritz.ofxcat.datastore.dto.Category.TRANSFER;
import static ca.jonathanfritz.ofxcat.datastore.dto.Category.UNKNOWN;
import static ca.jonathanfritz.ofxcat.service.ReportingService.CSV_DELIMITER;
import static ca.jonathanfritz.ofxcat.service.ReportingService.CURRENCY_FORMATTER;

import ca.jonathanfritz.ofxcat.AbstractDatabaseTest;
import ca.jonathanfritz.ofxcat.TestUtils;
import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReportingServiceTest extends AbstractDatabaseTest {

    private final CategorizedTransactionDao categorizedTransactionDao;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;

    ReportingServiceTest() {
        this.categorizedTransactionDao = injector.getInstance(CategorizedTransactionDao.class);
        this.accountDao = injector.getInstance(AccountDao.class);
        this.categoryDao = injector.getInstance(CategoryDao.class);
    }

    @Test
    void reportAccountsTest() {
        // create one test account
        final Account testAccount =
                accountDao.insert(TestUtils.createRandomAccount()).get();

        // we expect that account to be printed to the CLI
        final SpyCli spyCli = new SpyCli();

        // run the test
        final ReportingService reportingService = new ReportingService(null, accountDao, null, spyCli);
        reportingService.reportAccounts();

        // ensure that the right thing was printed
        final List<String> expectedLines = Arrays.asList(
                "Account Name,Account Number,Bank Id,Account Type",
                String.format(
                        "%s,%s,%s,%s",
                        testAccount.getName(),
                        testAccount.getAccountNumber(),
                        testAccount.getBankId(),
                        testAccount.getAccountType()));
        final List<String> actualLines = spyCli.getCapturedLines();
        Assertions.assertEquals(expectedLines, actualLines);
    }

    @Test
    void reportCategoriesTest() {
        // create a test category
        final Category testCategory =
                categoryDao.insert(TestUtils.createRandomCategory()).get();
        final List<Category> expectedCategories = Stream.of(testCategory, TRANSFER, UNKNOWN)
                .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()))
                .toList();

        // we expect that category to be printed to the CLI
        final SpyCli spyCli = new SpyCli();

        // run the test
        final ReportingService reportingService = new ReportingService(null, null, categoryDao, spyCli);
        reportingService.reportCategories();

        // ensure that the right thing was printed - category name should be uppercase!
        final List<String> expectedLines = Stream.concat(
                        Stream.of("ID" + CSV_DELIMITER + "NAME"),
                        expectedCategories.stream()
                                .map(category -> category.getId()
                                        + CSV_DELIMITER
                                        + category.getName().toUpperCase()))
                .collect(Collectors.toList());

        final List<String> actualLines = spyCli.getCapturedLines();
        Assertions.assertEquals(expectedLines, actualLines);
    }

    @Test
    public void reportTransactionsMonthlyNoDataTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);

        // with no transactions, no categories appear — header is just "MONTH" and month rows have no category columns
        reportingService.reportTransactionsMonthly(start, end);
        Assertions.assertEquals(11, spyCli.getCapturedLines().size());
        Assertions.assertEquals("MONTH", spyCli.getCapturedLines().get(0));
        Assertions.assertEquals("Jan-22", spyCli.getCapturedLines().get(1));
        Assertions.assertEquals("Feb-22", spyCli.getCapturedLines().get(2));
        Assertions.assertEquals("Mar-22", spyCli.getCapturedLines().get(3));
        Assertions.assertEquals("Apr-22", spyCli.getCapturedLines().get(4));
        Assertions.assertEquals("May-22", spyCli.getCapturedLines().get(5));
        Assertions.assertEquals("Jun-22", spyCli.getCapturedLines().get(6));

        // stats rows also have no category columns; 6 months → t3m + t6m + avg + total
        Assertions.assertEquals("t3m", spyCli.getCapturedLines().get(7));
        Assertions.assertEquals("t6m", spyCli.getCapturedLines().get(8));
        Assertions.assertEquals("avg", spyCli.getCapturedLines().get(9));
        Assertions.assertEquals("total", spyCli.getCapturedLines().get(10));
    }

    @Test
    public void reportTransactionsMonthlyAllCategoriesTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 1, 30);

        // add three categories to the default set of TRANSFER and UNKNOWN
        final List<Category> categories = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            categories.add(categoryDao.insert(TestUtils.createRandomCategory()).orElse(null));
        }

        // insert one transaction per category so all five categories appear after empty-category filtering
        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final LocalDate transactionDate = LocalDate.of(2022, 1, 15);
        for (Category category : categories) {
            categorizedTransactionDao.insert(new CategorizedTransaction(
                    TestUtils.createRandomTransaction(account, transactionDate, 0.0f), category));
        }
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, transactionDate, 0.0f), TRANSFER));
        categorizedTransactionDao.insert(
                new CategorizedTransaction(TestUtils.createRandomTransaction(account, transactionDate, 0.0f), UNKNOWN));

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);

        // the category headers should be printed in alphabetical order
        final String expected = "MONTH" + CSV_DELIMITER
                + Stream.concat(categories.stream(), Stream.of(TRANSFER, UNKNOWN))
                        .map(Category::getName)
                        .sorted()
                        .collect(Collectors.joining(CSV_DELIMITER));
        reportingService.reportTransactionsMonthly(start, end);
        // 1 month: trailing rows are suppressed (need ≥3 months for t3m, ≥6 for t6m) → avg + total only
        Assertions.assertEquals(4, spyCli.getCapturedLines().size());
        Assertions.assertEquals(expected, spyCli.getCapturedLines().get(0));

        // there will be one row printed for january with one decimal value column for each of the five categories
        Assertions.assertEquals(
                "Jan-22, 0.00, 0.00, 0.00, 0.00, 0.00",
                spyCli.getCapturedLines().get(1));

        Assertions.assertEquals(
                "avg, 0.00, 0.00, 0.00, 0.00, 0.00", spyCli.getCapturedLines().get(2));
        Assertions.assertEquals(
                "total, 0.00, 0.00, 0.00, 0.00, 0.00", spyCli.getCapturedLines().get(3));
    }

    @Test
    public void reportTransactionsMonthlyTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        // we need a random account
        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // create five transactions
        // list index is month offset, map value contains total spend for each category during that month
        final List<Map<Category, Float>> expected = new ArrayList<>();
        for (int month = 1; month < 7; month++) {
            final Map<Category, Float> categorySum = new HashMap<>();
            for (Category category : Arrays.asList(TRANSFER, UNKNOWN)) {
                final List<Float> amounts = new ArrayList<>();
                for (int day = 1; day < 6; day++) {
                    final Transaction t = TestUtils.createRandomTransaction(account, LocalDate.of(2022, month, day));
                    categorizedTransactionDao.insert(new CategorizedTransaction(t, category));
                    amounts.add(t.getAmount());
                }
                categorySum.put(category, amounts.stream().reduce(0f, Float::sum));
            }
            expected.add(categorySum);
        }

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);

        // the category headers should still be printed along with one row for each month
        reportingService.reportTransactionsMonthly(start, end);
        Assertions.assertEquals(11, spyCli.getCapturedLines().size());
        Assertions.assertEquals(
                "MONTH, TRANSFER, UNKNOWN", spyCli.getCapturedLines().get(0));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList(
                                "Jan-22",
                                CURRENCY_FORMATTER.format(expected.get(0).get(TRANSFER)),
                                CURRENCY_FORMATTER.format(expected.get(0).get(UNKNOWN)))),
                spyCli.getCapturedLines().get(1));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList(
                                "Feb-22",
                                CURRENCY_FORMATTER.format(expected.get(1).get(TRANSFER)),
                                CURRENCY_FORMATTER.format(expected.get(1).get(UNKNOWN)))),
                spyCli.getCapturedLines().get(2));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList(
                                "Mar-22",
                                CURRENCY_FORMATTER.format(expected.get(2).get(TRANSFER)),
                                CURRENCY_FORMATTER.format(expected.get(2).get(UNKNOWN)))),
                spyCli.getCapturedLines().get(3));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList(
                                "Apr-22",
                                CURRENCY_FORMATTER.format(expected.get(3).get(TRANSFER)),
                                CURRENCY_FORMATTER.format(expected.get(3).get(UNKNOWN)))),
                spyCli.getCapturedLines().get(4));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList(
                                "May-22",
                                CURRENCY_FORMATTER.format(expected.get(4).get(TRANSFER)),
                                CURRENCY_FORMATTER.format(expected.get(4).get(UNKNOWN)))),
                spyCli.getCapturedLines().get(5));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList(
                                "Jun-22",
                                CURRENCY_FORMATTER.format(expected.get(5).get(TRANSFER)),
                                CURRENCY_FORMATTER.format(expected.get(5).get(UNKNOWN)))),
                spyCli.getCapturedLines().get(6));

        // stats are tested in a dedicated method with fixed values
    }

    @Test
    public void reportTransactionsMonthlyStatsTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 4, 30);

        // we need a random account
        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        for (int i = 1; i < 5; i++) {
            // transactions in the TRANSFER category will all have positive amounts
            categorizedTransactionDao.insert(new CategorizedTransaction(
                    TestUtils.createRandomTransaction(account, start.plusMonths(i - 1), 2f * i), TRANSFER));

            // transactions in the UNKNOWN category will all have negative amounts
            categorizedTransactionDao.insert(new CategorizedTransaction(
                    TestUtils.createRandomTransaction(account, start.plusMonths(i - 1), 3f * -i), UNKNOWN));
        }

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);

        // 1 header + 4 months + 3 stats (t3m shown because ≥3 months; t6m suppressed; avg + total) = 8 lines
        reportingService.reportTransactionsMonthly(start, end);
        Assertions.assertEquals(8, spyCli.getCapturedLines().size());
        Assertions.assertEquals(
                "MONTH, TRANSFER, UNKNOWN", spyCli.getCapturedLines().get(0));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("Jan-22", CURRENCY_FORMATTER.format(2f), CURRENCY_FORMATTER.format(-3f))),
                spyCli.getCapturedLines().get(1));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("Feb-22", CURRENCY_FORMATTER.format(4f), CURRENCY_FORMATTER.format(-6f))),
                spyCli.getCapturedLines().get(2));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("Mar-22", CURRENCY_FORMATTER.format(6f), CURRENCY_FORMATTER.format(-9f))),
                spyCli.getCapturedLines().get(3));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("Apr-22", CURRENCY_FORMATTER.format(8f), CURRENCY_FORMATTER.format(-12f))),
                spyCli.getCapturedLines().get(4));

        // t3m = average of last 3 months: TRANSFER (4+6+8)/3=6, UNKNOWN (-6-9-12)/3=-9
        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("t3m", CURRENCY_FORMATTER.format(6f), CURRENCY_FORMATTER.format(-9f))),
                spyCli.getCapturedLines().get(5));

        // t6m suppressed (only 4 months in report)

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("avg", CURRENCY_FORMATTER.format(5f), CURRENCY_FORMATTER.format(-7.5f))),
                spyCli.getCapturedLines().get(6));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("total", CURRENCY_FORMATTER.format(20f), CURRENCY_FORMATTER.format(-30f))),
                spyCli.getCapturedLines().get(7));
    }

    @Test
    public void reportTransactionsMonthlyStatsIncludeZeroMonthsTest() {
        // a category with spend in months 1 and 3 but not month 2 should have its zero-spend month
        // reflected in the stats so they are consistent with the 0.00 printed in the matrix
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 3, 31);

        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 15), 10f), TRANSFER));
        // no TRANSFER transaction in February
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, LocalDate.of(2022, 3, 15), 20f), TRANSFER));

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsMonthly(start, end);

        // matrix should show 0.00 for Feb
        final List<String> lines = spyCli.getCapturedLines();
        Assertions.assertEquals("Feb-22" + CSV_DELIMITER + "0.00", lines.get(2));

        // stats are over [10, 0, 20] — three months including the zero
        // 3 months: t3m shown (window = all 3 months), t6m suppressed → 3 stat rows (t3m, avg, total)
        // t3m = (10+0+20)/3 = 10, avg = (10+0+20)/3 = 10, total = 30
        Assertions.assertEquals(7, lines.size()); // 1 header + 3 months + 3 stats
        Assertions.assertEquals("t3m" + CSV_DELIMITER + CURRENCY_FORMATTER.format(10f), lines.get(4));
        Assertions.assertEquals("avg" + CSV_DELIMITER + CURRENCY_FORMATTER.format(10f), lines.get(5));
        Assertions.assertEquals("total" + CSV_DELIMITER + CURRENCY_FORMATTER.format(30f), lines.get(6));
    }

    @Test
    public void reportTransactionsMonthlyTrailingAverageExactly6MonthsTest() {
        // exactly 6 months: both t3m and t6m rows must be emitted
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // TRANSFER amounts by month: 10, 20, 30, 40, 50, 60
        for (int month = 1; month <= 6; month++) {
            categorizedTransactionDao.insert(new CategorizedTransaction(
                    TestUtils.createRandomTransaction(account, LocalDate.of(2022, month, 15), 10f * month), TRANSFER));
        }

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsMonthly(start, end);

        final List<String> lines = spyCli.getCapturedLines();
        // 1 header + 6 months + 4 stats (t3m, t6m, avg, total) = 11 lines
        Assertions.assertEquals(11, lines.size());

        // t3m = average of last 3 months: (40+50+60)/3 = 50
        Assertions.assertEquals("t3m" + CSV_DELIMITER + CURRENCY_FORMATTER.format(50f), lines.get(7));
        // t6m = average of all 6 months: (10+20+30+40+50+60)/6 = 35
        Assertions.assertEquals("t6m" + CSV_DELIMITER + CURRENCY_FORMATTER.format(35f), lines.get(8));
        // avg = (10+20+30+40+50+60)/6 = 35
        Assertions.assertEquals("avg" + CSV_DELIMITER + CURRENCY_FORMATTER.format(35f), lines.get(9));
        // total = 210
        Assertions.assertEquals("total" + CSV_DELIMITER + CURRENCY_FORMATTER.format(210f), lines.get(10));
    }

    @Test
    public void reportTransactionsMonthlyTrailingAverageFewerThan3MonthsTest() {
        // fewer than 3 months: both t3m and t6m must be suppressed; only avg and total appear
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 2, 28);

        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 15), 100f), TRANSFER));
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, LocalDate.of(2022, 2, 15), 200f), TRANSFER));

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsMonthly(start, end);

        final List<String> lines = spyCli.getCapturedLines();
        // 1 header + 2 months + 2 stats (avg + total only) = 5 lines
        Assertions.assertEquals(5, lines.size());
        Assertions.assertEquals("avg" + CSV_DELIMITER + CURRENCY_FORMATTER.format(150f), lines.get(3));
        Assertions.assertEquals("total" + CSV_DELIMITER + CURRENCY_FORMATTER.format(300f), lines.get(4));
    }

    @Test
    public void reportTransactionsMonthlyTrailingAverageZeroSpendMonthsCountTest() {
        // zero-spend months must count as $0 in trailing averages, not be skipped
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // spend only in Jan and Apr; other months are $0
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 15), 60f), TRANSFER));
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, LocalDate.of(2022, 4, 15), 30f), TRANSFER));

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsMonthly(start, end);

        final List<String> lines = spyCli.getCapturedLines();
        // months: [60, 0, 0, 30, 0, 0]
        // t3m = last 3 months (Apr, May, Jun) = (30+0+0)/3 = 10
        Assertions.assertEquals("t3m" + CSV_DELIMITER + CURRENCY_FORMATTER.format(10f), lines.get(7));
        // t6m = all 6 months = (60+0+0+30+0+0)/6 = 15
        Assertions.assertEquals("t6m" + CSV_DELIMITER + CURRENCY_FORMATTER.format(15f), lines.get(8));
    }

    @Test
    public void reportTransactionsMonthlyMidMonthStartDateTest() {
        // transactions before startDate in the same month must not be included
        final LocalDate start = LocalDate.of(2022, 1, 15);
        final LocalDate end = LocalDate.of(2022, 1, 31);

        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Transaction before = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 10), 5f);
        final Transaction after = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 20), 7f);
        categorizedTransactionDao.insert(new CategorizedTransaction(before, TRANSFER));
        categorizedTransactionDao.insert(new CategorizedTransaction(after, TRANSFER));

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsMonthly(start, end);

        // only the Jan-20 transaction (after startDate) should appear; Jan-10 is outside the range
        Assertions.assertEquals(
                "Jan-22" + CSV_DELIMITER + CURRENCY_FORMATTER.format(7f),
                spyCli.getCapturedLines().get(1));
    }

    @Test
    public void reportTransactionsMonthlyMidMonthEndDateTest() {
        // transactions after endDate in the same month must not be included
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 1, 15);

        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Transaction before = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 10), 5f);
        final Transaction after = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 20), 7f);
        categorizedTransactionDao.insert(new CategorizedTransaction(before, TRANSFER));
        categorizedTransactionDao.insert(new CategorizedTransaction(after, TRANSFER));

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsMonthly(start, end);

        // only the Jan-10 transaction (before endDate) should appear; Jan-20 is outside the range
        Assertions.assertEquals(
                "Jan-22" + CSV_DELIMITER + CURRENCY_FORMATTER.format(5f),
                spyCli.getCapturedLines().get(1));
    }

    @Test
    public void reportTransactionsMonthlyMidMonthBothEndsTest() {
        // when both startDate and endDate are mid-month, each bucket must be clamped on both sides
        final LocalDate start = LocalDate.of(2022, 1, 15);
        final LocalDate end = LocalDate.of(2022, 2, 15);

        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        // Jan-10: before startDate – excluded
        // Jan-20: inside range – included in Jan bucket
        // Feb-10: inside range – included in Feb bucket
        // Feb-20: after endDate – excluded
        final Transaction janBefore = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 10), 1f);
        final Transaction janInside = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, 20), 3f);
        final Transaction febInside = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 2, 10), 5f);
        final Transaction febAfter = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 2, 20), 7f);
        for (Transaction t : List.of(janBefore, janInside, febInside, febAfter)) {
            categorizedTransactionDao.insert(new CategorizedTransaction(t, TRANSFER));
        }

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsMonthly(start, end);

        final List<String> lines = spyCli.getCapturedLines();
        // 2 months: trailing rows suppressed → avg + total only → 1 header + 2 months + 2 stats = 5 lines
        Assertions.assertEquals(5, lines.size());
        Assertions.assertEquals("Jan-22" + CSV_DELIMITER + CURRENCY_FORMATTER.format(3f), lines.get(1));
        Assertions.assertEquals("Feb-22" + CSV_DELIMITER + CURRENCY_FORMATTER.format(5f), lines.get(2));
    }

    @Test
    public void reportTransactionsMonthlyStartDateNullTest() {
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
        final IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> reportingService.reportTransactionsMonthly(null, LocalDate.of(2022, 6, 30)));
        Assertions.assertEquals("Start date must be specified", ex.getMessage());
    }

    @Test
    public void reportTransactionsMonthlyBadDateRangeTest() {
        final LocalDate start = LocalDate.of(2022, 6, 30);
        final LocalDate end = LocalDate.of(2022, 1, 1);
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
        final IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class, () -> reportingService.reportTransactionsMonthly(start, end));
        Assertions.assertEquals("Start date " + start + " must be before end date " + end, ex.getMessage());
    }

    @Test
    public void reportTransactionsBadCategoryTest() {
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
        final IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class, () -> reportingService.reportTransactionsInCategory(42L, null, null));
        Assertions.assertEquals("Category with id 42 does not exist", ex.getMessage());
    }

    @Test
    public void reportTransactionsStartDateNullTest() {
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
        final IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> reportingService.reportTransactionsInCategory(UNKNOWN.getId(), null, null));
        Assertions.assertEquals("Start date must be specified", ex.getMessage());
    }

    @Test
    public void reportTransactionsBadDateRangeTest() {
        final LocalDate start = LocalDate.of(2022, 6, 30);
        final LocalDate end = LocalDate.of(2022, 1, 1);
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
        final IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> reportingService.reportTransactionsInCategory(UNKNOWN.getId(), start, end));
        Assertions.assertEquals("Start date " + start + " must be before end date " + end, ex.getMessage());
    }

    @Test
    public void reportTransactionsBadDateRangeNullEndDateTest() {
        final LocalDate tomorrow = LocalDate.now().plusDays(1);
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
        final IllegalArgumentException ex = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> reportingService.reportTransactionsInCategory(UNKNOWN.getId(), tomorrow, null));
        Assertions.assertEquals(
                "Start date " + tomorrow + " must be before end date " + LocalDate.now(), ex.getMessage());
    }

    @Test
    public void reportTransactionsInCategoryNoDataTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsInCategory(UNKNOWN.getId(), start, end);

        // one line for headers, another four containing summary data
        final List<String> lines = spyCli.getCapturedLines();
        Assertions.assertEquals(5, lines.size());
        Assertions.assertEquals("DATE" + CSV_DELIMITER + "DESCRIPTION" + CSV_DELIMITER + "AMOUNT", lines.get(0));
        Assertions.assertEquals(CSV_DELIMITER + "p50" + CSV_DELIMITER + "0.00", lines.get(1));
        Assertions.assertEquals(CSV_DELIMITER + "p90" + CSV_DELIMITER + "0.00", lines.get(2));
        Assertions.assertEquals(CSV_DELIMITER + "avg" + CSV_DELIMITER + "0.00", lines.get(3));
        Assertions.assertEquals(CSV_DELIMITER + "total" + CSV_DELIMITER + "0.00", lines.get(4));
    }

    @Test
    public void reportTransactionsInCategoryTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        // we need a random account
        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Category category =
                categoryDao.insert(TestUtils.createRandomCategory()).orElse(null);

        // create five transactions for each month and category, keeping track of their sums
        final List<String> expected = new ArrayList<>();
        expected.add("DATE" + CSV_DELIMITER + "DESCRIPTION" + CSV_DELIMITER + "AMOUNT");

        final DateTimeFormatter dtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final List<Float> amounts = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            final Transaction t = TestUtils.createRandomTransaction(account, LocalDate.of(2022, 1, i));
            categorizedTransactionDao.insert(new CategorizedTransaction(t, category));
            amounts.add(t.getAmount());
            expected.add(dtFormat.format(t.getDate())
                    + CSV_DELIMITER
                    + t.getDescription()
                    + CSV_DELIMITER
                    + CURRENCY_FORMATTER.format(t.getAmount()));
        }

        // compute the expected stats
        final float p50Expected = amounts.stream().sorted().toList().get(4);
        expected.add(CSV_DELIMITER + "p50" + CSV_DELIMITER + CURRENCY_FORMATTER.format(p50Expected));

        final float p90Expected = amounts.stream().sorted().toList().get(8);
        expected.add(CSV_DELIMITER + "p90" + CSV_DELIMITER + CURRENCY_FORMATTER.format(p90Expected));

        final float totalExpected = amounts.stream().reduce(0f, Float::sum);
        final float avgExpected = totalExpected / 10f;
        expected.add(CSV_DELIMITER + "avg" + CSV_DELIMITER + CURRENCY_FORMATTER.format(avgExpected));
        expected.add(CSV_DELIMITER + "total" + CSV_DELIMITER + CURRENCY_FORMATTER.format(totalExpected));

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);
        reportingService.reportTransactionsInCategory(category.getId(), start, end);
        final List<String> actual = spyCli.getCapturedLines();

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void reportTransactionsMonthlyToFileProducesValidXlsxFile() throws Exception {
        // Setup: insert one transaction so the report has data
        final Account account =
                accountDao.insert(TestUtils.createRandomAccount()).orElse(null);
        final Category groceries = categoryDao.insert(new Category("GROCERIES")).orElse(null);
        final LocalDate transactionDate = LocalDate.of(2023, 6, 15);
        categorizedTransactionDao.insert(new CategorizedTransaction(
                TestUtils.createRandomTransaction(account, transactionDate, -100f), groceries));

        final LocalDate start = LocalDate.of(2023, 1, 1);
        final LocalDate end = LocalDate.of(2023, 12, 31);
        final java.nio.file.Path outputFile = java.nio.file.Files.createTempFile("ofxcat-test-", ".xlsx");
        outputFile.toFile().deleteOnExit();

        // Execute
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, new SpyCli());
        final java.nio.file.Path result = reportingService.reportTransactionsMonthlyToFile(start, end, outputFile);

        // Verify: file exists, is non-empty, and begins with the ZIP magic bytes that identify an XLSX file
        Assertions.assertEquals(outputFile, result);
        Assertions.assertTrue(java.nio.file.Files.exists(result), "Output file should exist");
        final byte[] bytes = java.nio.file.Files.readAllBytes(result);
        Assertions.assertTrue(bytes.length > 0, "Output file should not be empty");
        // XLSX files are ZIP archives; all ZIP archives start with the local file header signature 0x504B0304
        Assertions.assertEquals((byte) 0x50, bytes[0], "First byte should be 0x50 (ZIP magic)");
        Assertions.assertEquals((byte) 0x4B, bytes[1], "Second byte should be 0x4B (ZIP magic)");
        Assertions.assertEquals((byte) 0x03, bytes[2], "Third byte should be 0x03 (ZIP magic)");
        Assertions.assertEquals((byte) 0x04, bytes[3], "Fourth byte should be 0x04 (ZIP magic)");
    }

    @Test
    void reportTransactionsMonthlyToFileCreatesParentDirectory() throws Exception {
        // Setup: use a path whose parent directory does not yet exist
        final java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("ofxcat-test-");
        final java.nio.file.Path outputFile = tempDir.resolve("subdir").resolve("report.xlsx");

        final LocalDate start = LocalDate.of(2023, 1, 1);
        final LocalDate end = LocalDate.of(2023, 3, 31);

        // Execute
        final ReportingService reportingService =
                new ReportingService(categorizedTransactionDao, accountDao, categoryDao, new SpyCli());
        reportingService.reportTransactionsMonthlyToFile(start, end, outputFile);

        // Verify: parent directory was created and file exists
        final java.nio.file.Path subdir = outputFile.getParent();
        Assertions.assertNotNull(subdir, "Output file must have a parent directory");
        Assertions.assertTrue(java.nio.file.Files.isDirectory(subdir), "Parent directory should exist");
        Assertions.assertTrue(java.nio.file.Files.exists(outputFile), "Output file should exist");

        // Cleanup
        java.nio.file.Files.deleteIfExists(outputFile);
        java.nio.file.Files.deleteIfExists(subdir);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    private static class SpyCli extends CLI {

        private final List<String> capturedLines = new ArrayList<>();

        SpyCli() {
            super(null, null);
        }

        @Override
        public void println(List<String> lines) {
            capturedLines.addAll(lines);
        }

        public List<String> getCapturedLines() {
            return Collections.unmodifiableList(capturedLines);
        }
    }
}
