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
import java.util.*;
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

        // figure out how much money was spent in each category over the entirety of the specified date range
        final Map<Category, Float> categorySums = categorizedTransactions.entrySet().stream()
                .map(entry -> Pair.of(
                        entry.getKey(),
                        entry.getValue().stream()
                                .reduce(0f, (sum, t) -> sum + t.getAmount(), Float::sum))
                )
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, Float::sum));

        // print categories sorted by total amount spent descending
        cli.println(String.format("Categorized spending from %s to %s", minDate, maxDate));
        cli.println(Streams.concat(
                Stream.of("Category, Spend"),
                categorySums.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue((sum1, sum2) ->
                                // sort by absolute value descending
                                Float.compare(Math.abs(sum1), Math.abs(sum2)) * -1)
                        )
                        .map(entry -> {
                            if (entry.getValue() > 0) {
                                return String.format("%s,$%.2f", entry.getKey().getName(), entry.getValue());
                            } else {
                                return String.format("%s,-$%.2f", entry.getKey().getName(), entry.getValue()*-1);
                            }
                        })
            ).collect(Collectors.toList())
        );
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
