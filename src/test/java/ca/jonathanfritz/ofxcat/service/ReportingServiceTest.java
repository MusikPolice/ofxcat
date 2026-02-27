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

        // stats rows also have no category columns
        Assertions.assertEquals("p50", spyCli.getCapturedLines().get(7));
        Assertions.assertEquals("p90", spyCli.getCapturedLines().get(8));
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
        Assertions.assertEquals(6, spyCli.getCapturedLines().size());
        Assertions.assertEquals(expected, spyCli.getCapturedLines().get(0));

        // there will be one row printed for january with one decimal value column for each of the five categories
        Assertions.assertEquals(
                "Jan-22, 0.00, 0.00, 0.00, 0.00, 0.00",
                spyCli.getCapturedLines().get(1));

        // and there will be four stats rows
        Assertions.assertEquals(
                "p50, 0.00, 0.00, 0.00, 0.00, 0.00", spyCli.getCapturedLines().get(2));
        Assertions.assertEquals(
                "p90, 0.00, 0.00, 0.00, 0.00, 0.00", spyCli.getCapturedLines().get(3));
        Assertions.assertEquals(
                "avg, 0.00, 0.00, 0.00, 0.00, 0.00", spyCli.getCapturedLines().get(4));
        Assertions.assertEquals(
                "total, 0.00, 0.00, 0.00, 0.00, 0.00", spyCli.getCapturedLines().get(5));
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

        // one header + four months + four stats = 10 lines
        reportingService.reportTransactionsMonthly(start, end);
        Assertions.assertEquals(9, spyCli.getCapturedLines().size());
        Assertions.assertEquals(
                "MONTH, TRANSFER, UNKNOWN", spyCli.getCapturedLines().get(0));

        // we only have transactions for one month
        // we know that we spent a total of $20 on TRANSFER and $0 on UNKNOWN
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

        // stats
        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("p50", CURRENCY_FORMATTER.format(4f), CURRENCY_FORMATTER.format(-6f))),
                spyCli.getCapturedLines().get(5));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("p90", CURRENCY_FORMATTER.format(6f), CURRENCY_FORMATTER.format(-9f))),
                spyCli.getCapturedLines().get(6));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("avg", CURRENCY_FORMATTER.format(5f), CURRENCY_FORMATTER.format(-7.5f))),
                spyCli.getCapturedLines().get(7));

        Assertions.assertEquals(
                String.join(
                        CSV_DELIMITER,
                        Arrays.asList("total", CURRENCY_FORMATTER.format(20f), CURRENCY_FORMATTER.format(-30f))),
                spyCli.getCapturedLines().get(8));
    }

    @Test
    public void reportTransactionsMonthlyStartDateNullTest() {
        try {
            // the start date is null, so an exception will be thrown
            final ReportingService reportingService =
                    new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
            reportingService.reportTransactionsMonthly(null, LocalDate.of(2022, 6, 30));
        } catch (IllegalArgumentException ex) {
            Assertions.assertEquals("Start date must be specified", ex.getMessage());
        }
    }

    @Test
    public void reportTransactionsMonthlyBadDateRangeTest() {
        final LocalDate start = LocalDate.of(2022, 6, 30);
        final LocalDate end = LocalDate.of(2022, 1, 1);

        try {
            // end date is before start date, so an exception will be thrown
            final ReportingService reportingService =
                    new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
            reportingService.reportTransactionsMonthly(start, end);
        } catch (IllegalArgumentException ex) {
            Assertions.assertEquals("Start date " + start + " must be before end date " + end, ex.getMessage());
        }
    }

    @Test
    public void reportTransactionsBadCategoryTest() {
        try {
            // this category id does not exist, so an exception will be thrown
            final ReportingService reportingService =
                    new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
            reportingService.reportTransactionsInCategory(42L, null, null);
        } catch (IllegalArgumentException ex) {
            Assertions.assertEquals("Category with id 42 does not exist", ex.getMessage());
        }
    }

    @Test
    public void reportTransactionsStartDateNullTest() {
        try {
            // the start date is null, so an exception will be thrown
            final ReportingService reportingService =
                    new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
            reportingService.reportTransactionsInCategory(UNKNOWN.getId(), null, null);
        } catch (IllegalArgumentException ex) {
            Assertions.assertEquals("Start date must be specified", ex.getMessage());
        }
    }

    @Test
    public void reportTransactionsBadDateRangeTest() {
        final LocalDate start = LocalDate.of(2022, 6, 30);
        final LocalDate end = LocalDate.of(2022, 1, 1);

        try {
            // end date is before start date, so an exception will be thrown
            final ReportingService reportingService =
                    new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
            reportingService.reportTransactionsInCategory(UNKNOWN.getId(), start, end);
        } catch (IllegalArgumentException ex) {
            Assertions.assertEquals("Start date " + start + " must be before end date " + end, ex.getMessage());
        }

        final LocalDate tomorrow = LocalDate.now().plusDays(1);
        try {
            // end date not specified, so current date is used; start is after end, so an exception will be thrown
            final ReportingService reportingService =
                    new ReportingService(categorizedTransactionDao, accountDao, categoryDao, null);
            reportingService.reportTransactionsInCategory(UNKNOWN.getId(), tomorrow, null);
        } catch (IllegalArgumentException ex) {
            Assertions.assertEquals(
                    "Start date " + tomorrow + " must be before end date " + LocalDate.now(), ex.getMessage());
        }
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
