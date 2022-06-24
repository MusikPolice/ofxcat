package ca.jonathanfritz.ofxcat.service;

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
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.jonathanfritz.ofxcat.datastore.dto.Category.TRANSFER;
import static ca.jonathanfritz.ofxcat.datastore.dto.Category.UNKNOWN;
import static ca.jonathanfritz.ofxcat.service.ReportingService.CSV_DELIMITER;
import static ca.jonathanfritz.ofxcat.service.ReportingService.CURRENCY_FORMATTER;

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
        final Account testAccount = accountDao.insert(TestUtils.createRandomAccount()).get();

        // we expect that account to be printed to the CLI
        final SpyCli spyCli = new SpyCli();

        // run the test
        final ReportingService reportingService = new ReportingService(null, accountDao, null, spyCli);
        reportingService.reportAccounts();

        // ensure that the right thing was printed
        final List<String> expectedLines = Arrays.asList(
                "Account Name,Account Number,Bank Id,Account Type",
                String.format("%s,%s,%s,%s", testAccount.getName(), testAccount.getAccountNumber(), testAccount.getBankId(), testAccount.getAccountType())
        );
        final List<String> actualLines = spyCli.getCapturedLines();
        Assertions.assertEquals(expectedLines, actualLines);
    }

    @Test
    void reportCategoriesTest() {
        // create a test category
        final Category testCategory = categoryDao.insert(TestUtils.createRandomCategory()).get();
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
                expectedCategories.stream().map(category ->
                            category.getId() + CSV_DELIMITER + category.getName().toUpperCase()
                )
        ).collect(Collectors.toList());

        final List<String> actualLines = spyCli.getCapturedLines();
        Assertions.assertEquals(expectedLines, actualLines);
    }

    @Test
    public void reportTransactionsMonthlyNoDataTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService = new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);

        // there are no transactions, but the category headers should still be printed along with one row for each month
        reportingService.reportTransactionsMonthly(start, end);
        Assertions.assertEquals(7, spyCli.getCapturedLines().size());
        Assertions.assertEquals("TRANSFER, UNKNOWN" + System.lineSeparator(), spyCli.getCapturedLines().get(0));
        Assertions.assertEquals("January 2022, 0.00, 0.00" + System.lineSeparator(), spyCli.getCapturedLines().get(1));
        Assertions.assertEquals("February 2022, 0.00, 0.00" + System.lineSeparator(), spyCli.getCapturedLines().get(2));
        Assertions.assertEquals("March 2022, 0.00, 0.00" + System.lineSeparator(), spyCli.getCapturedLines().get(3));
        Assertions.assertEquals("April 2022, 0.00, 0.00" + System.lineSeparator(), spyCli.getCapturedLines().get(4));
        Assertions.assertEquals("May 2022, 0.00, 0.00" + System.lineSeparator(), spyCli.getCapturedLines().get(5));
        Assertions.assertEquals("June 2022, 0.00, 0.00" + System.lineSeparator(), spyCli.getCapturedLines().get(6));
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

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService = new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);

        // the category headers should be printed in alphabetical order
        final String expected = Stream.concat(categories.stream(), Stream.of(TRANSFER, UNKNOWN))
                .map(Category::getName).sorted()
                .collect(Collectors.joining(CSV_DELIMITER));
        reportingService.reportTransactionsMonthly(start, end);
        Assertions.assertEquals(2, spyCli.getCapturedLines().size());
        Assertions.assertEquals(expected + System.lineSeparator(), spyCli.getCapturedLines().get(0));

        // and there will be one row printed for january with one decimal value column for each of the five categories
        Assertions.assertEquals("January 2022, 0.00, 0.00, 0.00, 0.00, 0.00" + System.lineSeparator(), spyCli.getCapturedLines().get(1));
    }

    @Test
    public void reportTransactionsMonthlyTest() {
        final LocalDate start = LocalDate.of(2022, 1, 1);
        final LocalDate end = LocalDate.of(2022, 6, 30);

        // we need a random account
        final Account account = accountDao.insert(TestUtils.createRandomAccount()).orElse(null);

        // create five transactions for each month and category, keeping track of their sums
        final List<Map<Category, Float>> expected = new ArrayList<>();
        for (int month = 1; month < 7; month++) {
            final Map<Category, Float> categorySpend = new HashMap<>();
            for (Category category : Arrays.asList(TRANSFER, UNKNOWN)) {
                float sum = 0;
                for (int day = 1; day < 6; day++) {
                    final Transaction t = TestUtils.createRandomTransaction(account, LocalDate.of(2022, month, day));
                    categorizedTransactionDao.insert(new CategorizedTransaction(t, category));
                    sum += t.getAmount();
                }
                categorySpend.put(category, sum);
            }
            expected.add(categorySpend);
        }

        final SpyCli spyCli = new SpyCli();
        final ReportingService reportingService = new ReportingService(categorizedTransactionDao, accountDao, categoryDao, spyCli);

        // the category headers should still be printed along with one row for each month
        reportingService.reportTransactionsMonthly(start, end);
        Assertions.assertEquals(7, spyCli.getCapturedLines().size());
        Assertions.assertEquals("TRANSFER, UNKNOWN" + System.lineSeparator(), spyCli.getCapturedLines().get(0));

        Assertions.assertEquals(String.join(CSV_DELIMITER, Arrays.asList(
                "January 2022",
                CURRENCY_FORMATTER.format(expected.get(0).get(TRANSFER)),
                CURRENCY_FORMATTER.format(expected.get(0).get(UNKNOWN)))
        ) + System.lineSeparator(),spyCli.getCapturedLines().get(1));

        Assertions.assertEquals(String.join(CSV_DELIMITER, Arrays.asList(
                "February 2022",
                CURRENCY_FORMATTER.format(expected.get(1).get(TRANSFER)),
                CURRENCY_FORMATTER.format(expected.get(1).get(UNKNOWN)))
        ) + System.lineSeparator(),spyCli.getCapturedLines().get(2));

        Assertions.assertEquals(String.join(CSV_DELIMITER, Arrays.asList(
                "March 2022",
                CURRENCY_FORMATTER.format(expected.get(2).get(TRANSFER)),
                CURRENCY_FORMATTER.format(expected.get(2).get(UNKNOWN)))
        ) + System.lineSeparator(),spyCli.getCapturedLines().get(3));

        Assertions.assertEquals(String.join(CSV_DELIMITER, Arrays.asList(
                "April 2022",
                CURRENCY_FORMATTER.format(expected.get(3).get(TRANSFER)),
                CURRENCY_FORMATTER.format(expected.get(3).get(UNKNOWN)))
        ) + System.lineSeparator(),spyCli.getCapturedLines().get(4));

        Assertions.assertEquals(String.join(CSV_DELIMITER, Arrays.asList(
                "May 2022",
                CURRENCY_FORMATTER.format(expected.get(4).get(TRANSFER)),
                CURRENCY_FORMATTER.format(expected.get(4).get(UNKNOWN)))
        ) + System.lineSeparator(),spyCli.getCapturedLines().get(5));

        Assertions.assertEquals(String.join(CSV_DELIMITER, Arrays.asList(
                "June 2022",
                CURRENCY_FORMATTER.format(expected.get(5).get(TRANSFER)),
                CURRENCY_FORMATTER.format(expected.get(5).get(UNKNOWN)))
        ) + System.lineSeparator(),spyCli.getCapturedLines().get(6));
    }

    private static class SpyCli extends CLI {

        private final List<String> capturedLines = new ArrayList<>();

        public SpyCli() {
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