package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.cli.CLI;
import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.CategoryDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.Category;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReportingService {

    private final CategorizedTransactionDao categorizedTransactionDao;
    private final AccountDao accountDao;
    private final CategoryDao categoryDao;
    private final CLI cli;

    private static final Logger logger = LoggerFactory.getLogger(ReportingService.class);

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
        final Map<Category, Float> transactions = categorizedTransactionDao.selectGroupByCategory(startDate, endDate).entrySet().stream()
                .map(entry -> Pair.of(
                        entry.getKey(),
                        entry.getValue().stream()
                                .reduce(0f, (sum, t) -> sum + t.getAmount(), Float::sum))
                )
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue, Float::sum));

        // print categories sorted by total amount spent descending
        cli.println(Streams.concat(
                Stream.of("Category,Amount Spent"),
                transactions.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue((sum1, sum2) -> sum1.compareTo(sum2) * -1))
                        .map(entry -> String.format("%s,$%.2f", entry.getKey().getName(), entry.getValue()))
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
                .map(a -> String.format("%s,%s,%s,%s", a.getName(), a.getAccountId(), a.getBankId(), a.getAccountType()))
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
