package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import ca.jonathanfritz.ofxcat.datastore.dto.Transaction;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReportingService {

    public static final DecimalFormat CURRENCY_FORMATTER = new DecimalFormat("0.00");
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CLI cli;

    public static final String CSV_DELIMITER = ", ";

    @Inject
    public ReportingService(CategorizedTransactionDao categorizedTransactionDao, AccountDao accountDao, CategoryDao categoryDao, CLI cli) {
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.accountDao = accountDao;
        this.categoryDao = categoryDao;
        this.cli = cli;
    }

    public void reportTransactionsMonthly(final LocalDate startDate, final LocalDate endDate) {
        // each list entry represents the start and end of a month within the specified date range
        LocalDate startMonth = startDate.withDayOfMonth(1);
        final List<LocalDateRange> months = new ArrayList<>();
        do {
            months.add(new LocalDateRange(startMonth, startMonth.withDayOfMonth(startMonth.lengthOfMonth())));
            startMonth = startMonth.plusMonths(1);
        } while (startMonth.isBefore(endDate));

        // get amount spent in each category for every 1 month long bucket
        final Map<LocalDate, Map<Category, Float>> dateSpend = months.stream().map(localDateRange -> {
            final Map<Category, List<CategorizedTransaction>> categorizedTransactions =
                    categorizedTransactionDao.selectGroupByCategory(localDateRange.start, localDateRange.end);

            return Pair.of(
                    localDateRange.start,
                    categorizedTransactions.entrySet().stream()
                            .map(categoryListEntry -> Pair.of(
                                    categoryListEntry.getKey(),
                                    categoryListEntry.getValue().stream()
                                            .map(Transaction::getAmount)
                                            .reduce(0f, Float::sum))
                            ).collect(Collectors.toMap(Pair::getKey, Pair::getValue))
            );
        }).collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        // all categories, sorted by name
        final List<Category> categories = categoryDao.select().stream()
                .sorted((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()))
                .toList();

        // print a matrix with categories along the x axis, months along the y axis, category spend for each month at the intersection
        final List<String> lines = new ArrayList<>();
        lines.add(categories.stream().map(Category::getName).collect(Collectors.joining(CSV_DELIMITER)) + System.lineSeparator());

        for (Map.Entry<LocalDate, Map<Category, Float>> entry : dateSpend.entrySet()) {
            // first column is the month/year
            final StringBuilder sb = new StringBuilder();
            sb.append(entry.getKey().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            sb.append(CSV_DELIMITER);

            // subsequent columns hold sum of transactions for that month and category
            // TODO: unit tests suggest that this reporting code is ok - check the database to see if transactions are miscategorized?
            sb.append(
                    categories.stream()
                            .map(category -> entry.getValue().getOrDefault(category, 0f))
                            .map(CURRENCY_FORMATTER::format)
                            .collect(Collectors.joining(CSV_DELIMITER))
            );
            sb.append(System.lineSeparator());
            lines.add(sb.toString());
        }

        // TODO: add rows for p50, p90, average, and total

        cli.println(lines);
    }

    private record LocalDateRange(LocalDate start, LocalDate end) { }

    /**
     * Prints out a CSV list of accounts in the database
     * TODO: add a table-formatted option
     */
    public void reportAccounts() {
        final List<Account> accounts = accountDao.select();
        cli.println(Streams.concat(
                Stream.of("Account Name,Account Number,Bank Id,Account Type"),
                accounts.stream()
                .map(a -> String.format("%s,%s,%s,%s", a.getName(), a.getAccountNumber(), a.getBankId(), a.getAccountType()))
            )
            .collect(Collectors.toList()));
    }

    /**
     * Prints out a CSV list of categories in the database
     * TODO: add a table-formatted option
     */
    public void reportCategories() {
        final List<String> lines = new ArrayList<>();
        lines.add("ID" + CSV_DELIMITER + "NAME");

        // print category ids and names, sorted by name alphabetically
        categoryDao.select().stream()
                .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()))
                .forEach(category ->
                        lines.add(category.getId() + CSV_DELIMITER + category.getName())
                );
        cli.println(lines);
    }
}
