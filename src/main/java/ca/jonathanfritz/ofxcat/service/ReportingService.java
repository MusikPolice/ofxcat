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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReportingService {

    private final CategorizedTransactionDao categorizedTransactionDao;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CLI cli;

    @Inject
    public ReportingService(CategorizedTransactionDao categorizedTransactionDao, AccountDao accountDao, CategoryDao categoryDao, CLI cli) {
        this.categorizedTransactionDao = categorizedTransactionDao;
        this.accountDao = accountDao;
        this.categoryDao = categoryDao;
        this.cli = cli;
    }

    /**
     * Prints out a CSV list of categories and the total amount spent in each between the specified dates
     * TODO: add a table-formatted option
     * TODO: print a matrix with categories on x, months on y, so that we can graph expenses in each category over time
     */
    public void reportTransactions(LocalDate startDate, LocalDate endDate) {
        // get all transactions in the specified date range, grouped by category
        final Map<Category, List<CategorizedTransaction>> categorizedTransactions =
                categorizedTransactionDao.selectGroupByCategory(startDate, endDate);

        // determine the effective start and end dates of the returned transactions
        final LocalDate minDate = categorizedTransactions.values().stream()
                .flatMap((Function<List<CategorizedTransaction>, Stream<CategorizedTransaction>>) Collection::stream)
                .map(Transaction::getDate)
                .min(Comparator.naturalOrder())
                .orElse(startDate);
        final LocalDate maxDate = categorizedTransactions.values().stream()
                .flatMap((Function<List<CategorizedTransaction>, Stream<CategorizedTransaction>>) Collection::stream)
                .map(Transaction::getDate)
                .max(Comparator.naturalOrder())
                .orElse(endDate);

        final long days = Math.abs(ChronoUnit.DAYS.between(minDate, maxDate));
        final float months = days / (365/12F);

        // figure out how much money was spent in each category over the entirety of the specified date range
        final Map<Category, Float> categorySums = categorizedTransactions.entrySet().stream()
                .map(entry -> Pair.of(
                        entry.getKey(),
                        entry.getValue().stream()
                                .reduce(0f, (sum, t) -> sum + t.getAmount(), Float::sum))
                )
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, Float::sum));

        // print categories sorted by total amount spent descending
        cli.println(String.format("Total spending from %s to %s", minDate, maxDate));
        cli.println(Streams.concat(
                Stream.of("Category, Spend"),
                categorySums.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(absoluteValueDescendingComparator()))
                        .map(this::printCategorySpend)
            ).collect(Collectors.toList())
        );

        // print categories sorted by total amount spent descending
        /*cli.println("\n\n");
        cli.println(String.format("Average Monthly spending from %s to %s", minDate, maxDate));
        cli.println(Streams.concat(
                        Stream.of("Category, Spend"),
                        categorySums.entrySet().stream()
                                .map(categoryFloatEntry ->
                                        // divide each spend amount by the number of months spanned by our date range
                                        Map.entry(categoryFloatEntry.getKey(), categoryFloatEntry.getValue() / months)
                                )
                                .sorted(Map.Entry.comparingByValue(absoluteValueDescendingComparator()))
                                .map(this::printCategorySpend)
                ).collect(Collectors.toList())
        );*/
    }

    private Comparator<Float> absoluteValueDescendingComparator() {
        return (sum1, sum2) -> {
                // sort by absolute value descending
                return Float.compare(Math.abs(sum1), Math.abs(sum2)) * -1;
            };
    }

    private String printCategorySpend(Map.Entry<Category, Float> entry) {
        final String category = entry.getKey().getName();
        final float spend = entry.getValue();
        if (spend > 0) {
            return String.format("%s,$%.2f", category, spend);
        } else {
            return String.format("%s,-$%.2f", category, spend * -1);
        }
    }

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
        final List<Category> categories = categoryDao.select();
        cli.println(Streams.concat(
                Stream.of("Category Name"),
                categories.stream().map(Category::getName)
            )
            .collect(Collectors.toList()));
    }
}
