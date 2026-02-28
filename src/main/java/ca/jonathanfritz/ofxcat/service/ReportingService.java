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
import jakarta.inject.Inject;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class ReportingService {

    public static final DecimalFormat CURRENCY_FORMATTER = new DecimalFormat("0.00");
    private final CategorizedTransactionDao categorizedTransactionDao;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CLI cli;

    public static final String CSV_DELIMITER = ", ";

    @Inject
    public ReportingService(
            CategorizedTransactionDao categorizedTransactionDao,
            AccountDao accountDao,
            CategoryDao categoryDao,
            CLI cli) {
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.accountDao = accountDao;
        this.categoryDao = categoryDao;
        this.cli = cli;
    }

    public void reportTransactionsMonthly(final LocalDate startDate, final LocalDate endDate) {
        // input validation
        if (startDate == null) {
            throw new IllegalArgumentException("Start date must be specified");
        }
        final LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
        if (startDate.isAfter(effectiveEndDate)) {
            throw new IllegalArgumentException(
                    "Start date " + startDate + " must be before end date " + effectiveEndDate);
        }

        // each list entry represents the start and end of a month within the specified date range;
        // clamp the first range's start to startDate and each range's end to effectiveEndDate so that
        // DAO queries never exceed the user-supplied inclusive date range
        LocalDate startMonth = startDate.withDayOfMonth(1);
        final List<LocalDateRange> months = new ArrayList<>();
        do {
            final LocalDate rangeStart = startMonth.isBefore(startDate) ? startDate : startMonth;
            final LocalDate endOfMonth = startMonth.withDayOfMonth(startMonth.lengthOfMonth());
            final LocalDate rangeEnd = endOfMonth.isAfter(effectiveEndDate) ? effectiveEndDate : endOfMonth;
            months.add(new LocalDateRange(rangeStart, rangeEnd));
            startMonth = startMonth.plusMonths(1);
        } while (!startMonth.isAfter(effectiveEndDate));

        // get amount spent in each category for every 1 month long bucket, preserving chronological order
        final Map<LocalDate, Map<Category, Float>> dateSpend = months.stream()
                .map(localDateRange -> {
                    final Map<Category, List<CategorizedTransaction>> categorizedTransactions =
                            categorizedTransactionDao.selectGroupByCategory(localDateRange.start, localDateRange.end);

                    return Pair.of(
                            localDateRange.start,
                            categorizedTransactions.entrySet().stream()
                                    .map(categoryListEntry -> Pair.of(
                                            categoryListEntry.getKey(),
                                            categoryListEntry.getValue().stream()
                                                    .map(Transaction::getAmount)
                                                    .reduce(0f, Float::sum)))
                                    .collect(Collectors.toMap(Pair::getKey, Pair::getValue)));
                })
                .collect(Collectors.toMap(
                        Pair::getKey,
                        Pair::getValue,
                        (a, b) -> {
                            throw new IllegalStateException("Duplicate key");
                        },
                        LinkedHashMap::new));

        // only include categories that have at least one transaction in the date range
        final List<Category> sortedCategories = dateSpend.values().stream()
                .flatMap(categoryMap -> categoryMap.keySet().stream())
                .distinct()
                .sorted((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()))
                .toList();

        // print a matrix with categories along the x axis, months along the y axis, category spend for each month at
        // the intersection
        final List<String> lines = new ArrayList<>();
        final String categoryHeader =
                sortedCategories.stream().map(Category::getName).collect(Collectors.joining(CSV_DELIMITER));
        lines.add(categoryHeader.isEmpty() ? "MONTH" : "MONTH" + CSV_DELIMITER + categoryHeader);

        final Map<Category, List<Float>> categoryTransactionAmounts = new HashMap<>();
        for (Map.Entry<LocalDate, Map<Category, Float>> entry : dateSpend.entrySet()) {
            // first column is the month/year
            final StringBuilder sb = new StringBuilder();
            sb.append(entry.getKey().format(DateTimeFormatter.ofPattern("MMM-yy")));

            // subsequent columns hold sum of transactions for that month and category
            if (!sortedCategories.isEmpty()) {
                sb.append(CSV_DELIMITER);
                sb.append(sortedCategories.stream()
                        .map(category -> entry.getValue().getOrDefault(category, 0f))
                        .map(CURRENCY_FORMATTER::format)
                        .collect(Collectors.joining(CSV_DELIMITER)));
            }
            lines.add(sb.toString());

            // group the monthly spend amounts for each category together, including zero-spend months
            // so that stats are consistent with the 0.00 values printed in the matrix
            for (Category category : sortedCategories) {
                final List<Float> monthlyAmounts = categoryTransactionAmounts.getOrDefault(category, new ArrayList<>());
                monthlyAmounts.add(entry.getValue().getOrDefault(category, 0f));
                categoryTransactionAmounts.putIfAbsent(category, monthlyAmounts);
            }
        }

        // calculate stats for each category
        final Map<Category, Stats> categoryStats = categoryTransactionAmounts.entrySet().stream()
                .map(categorySpendEntry ->
                        Pair.of(categorySpendEntry.getKey(), computeStats(categorySpendEntry.getValue())))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        // trailing average rows are only emitted when the report spans at least as many months as the window size
        if (months.size() >= 3) {
            lines.add(generateStatsString("t3m", stats -> stats.trailing3m, sortedCategories, categoryStats));
        }
        if (months.size() >= 6) {
            lines.add(generateStatsString("t6m", stats -> stats.trailing6m, sortedCategories, categoryStats));
        }
        lines.add(generateStatsString("avg", stats -> stats.avg, sortedCategories, categoryStats));
        lines.add(generateStatsString("total", stats -> stats.total, sortedCategories, categoryStats));

        cli.println(lines);
    }

    private String generateStatsString(
            String name,
            Function<Stats, Float> func,
            List<Category> sortedCategories,
            Map<Category, Stats> categoryStats) {
        if (sortedCategories.isEmpty()) {
            return name;
        }
        return name
                + CSV_DELIMITER
                + sortedCategories.stream()
                        .map(categoryStats::get)
                        .map(stats -> {
                            if (stats == null) {
                                return CURRENCY_FORMATTER.format(0f);
                            }
                            return CURRENCY_FORMATTER.format(func.apply(stats));
                        })
                        .collect(Collectors.joining(CSV_DELIMITER));
    }

    public void reportTransactionsInCategory(
            final Long categoryId, final LocalDate startDate, final LocalDate endDate) {
        // input validation
        final Category category = categoryDao
                .select(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category with id " + categoryId + " does not exist"));
        if (startDate == null) {
            throw new IllegalArgumentException("Start date must be specified");
        }
        final LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
        if (startDate.isAfter(effectiveEndDate)) {
            throw new IllegalArgumentException(
                    "Start date " + startDate + " must be before end date " + effectiveEndDate);
        }

        // get all transactions in the specified category
        final List<String> lines = new ArrayList<>();
        lines.add("DATE" + CSV_DELIMITER + "DESCRIPTION" + CSV_DELIMITER + "AMOUNT");

        // keep track of the amount of each transaction so that we can compute p50, p90, avg, and total
        final List<Float> amounts = new ArrayList<>();

        // generate one line for each transaction
        final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        categorizedTransactionDao.selectByCategory(category, startDate, effectiveEndDate).stream()
                .map(CategorizedTransaction::getTransaction)
                .forEach(transaction -> {
                    amounts.add(transaction.getAmount());
                    lines.add(dateFormatter.format(transaction.getDate())
                            + CSV_DELIMITER
                            + transaction.getDescription()
                            + CSV_DELIMITER
                            + CURRENCY_FORMATTER.format(transaction.getAmount()));
                });

        // compute and append per-transaction stats
        if (amounts.isEmpty()) {
            lines.add(CSV_DELIMITER + "p50" + CSV_DELIMITER + CURRENCY_FORMATTER.format(0f));
            lines.add(CSV_DELIMITER + "p90" + CSV_DELIMITER + CURRENCY_FORMATTER.format(0f));
            lines.add(CSV_DELIMITER + "avg" + CSV_DELIMITER + CURRENCY_FORMATTER.format(0f));
            lines.add(CSV_DELIMITER + "total" + CSV_DELIMITER + CURRENCY_FORMATTER.format(0f));
        } else {
            // if all amounts are negative, reverse sort order so p90 is the largest expense outlier
            final List<Float> sorted = amounts.stream().allMatch(a -> a <= 0)
                    ? amounts.stream().sorted((x, y) -> x.compareTo(y) * -1).toList()
                    : amounts.stream().sorted().toList();
            final float p50 = sorted.get((int) Math.floor((amounts.size() - 1) * 0.5));
            final float p90 = sorted.get((int) Math.floor((amounts.size() - 1) * 0.9));
            final float total = amounts.stream().reduce(0f, Float::sum);
            lines.add(CSV_DELIMITER + "p50" + CSV_DELIMITER + CURRENCY_FORMATTER.format(p50));
            lines.add(CSV_DELIMITER + "p90" + CSV_DELIMITER + CURRENCY_FORMATTER.format(p90));
            lines.add(CSV_DELIMITER + "avg" + CSV_DELIMITER + CURRENCY_FORMATTER.format(total / amounts.size()));
            lines.add(CSV_DELIMITER + "total" + CSV_DELIMITER + CURRENCY_FORMATTER.format(total));
        }

        cli.println(lines);
    }

    private Stats computeStats(List<Float> monthlyAmounts) {
        if (monthlyAmounts.isEmpty()) {
            return new Stats(0f, 0f, 0f, 0f);
        }
        final float total = monthlyAmounts.stream().reduce(0f, Float::sum);
        final float avg = total / monthlyAmounts.size();
        final float trailing3m = trailingAverage(monthlyAmounts, 3);
        final float trailing6m = trailingAverage(monthlyAmounts, 6);
        return new Stats(trailing3m, trailing6m, total, avg);
    }

    // Returns the average of the most recent 'window' months. Zero-spend months are included in the
    // denominator, so a month with no transactions counts as $0.00 rather than being skipped.
    // The divisor is always 'window', even when fewer months are available; rows are suppressed at
    // the call site when the report spans fewer months than the window (see reportTransactionsMonthly).
    private float trailingAverage(List<Float> amounts, int window) {
        final int fromIndex = Math.max(0, amounts.size() - window);
        return amounts.subList(fromIndex, amounts.size()).stream().reduce(0f, Float::sum) / window;
    }

    private record Stats(float trailing3m, float trailing6m, float total, float avg) {}

    private record LocalDateRange(LocalDate start, LocalDate end) {}

    /**
     * Prints out a CSV list of accounts in the database
     * TODO: add a table-formatted option
     */
    public void reportAccounts() {
        final List<Account> accounts = accountDao.select();
        cli.println(Streams.concat(
                        Stream.of("Account Name,Account Number,Bank Id,Account Type"),
                        accounts.stream()
                                .map(a -> String.format(
                                        "%s,%s,%s,%s",
                                        a.getName(), a.getAccountNumber(), a.getBankId(), a.getAccountType())))
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
                .forEach(category -> lines.add(category.getId() + CSV_DELIMITER + category.getName()));
        cli.println(lines);
    }
}
