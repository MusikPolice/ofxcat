package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.datastore.AccountDao;
import ca.jonathanfritz.ofxcat.datastore.CategorizedTransactionDao;
import ca.jonathanfritz.ofxcat.datastore.dto.Account;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Detects gaps in the transaction record by checking the balance invariant between consecutive
 * transactions. A gap exists when {@code balance[n+1] != balance[n] + amount[n+1]}, meaning one
 * or more transactions are missing from the record.
 */
public class GapDetectionService {

    private static final Logger logger = LogManager.getLogger(GapDetectionService.class);

    private final AccountDao accountDao;
    private final CategorizedTransactionDao categorizedTransactionDao;

    @Inject
    public GapDetectionService(AccountDao accountDao, CategorizedTransactionDao categorizedTransactionDao) {
        this.accountDao = accountDao;
        this.categorizedTransactionDao = categorizedTransactionDao;
    }

    /**
     * Represents a detected gap in the transaction record.
     *
     * @param account the account where the gap was detected
     * @param lastGoodDate the date of the last transaction before the gap
     * @param firstDateAfterGap the date of the first transaction after the gap
     * @param missingAmount the net missing amount ({@code balance[n+1] - (balance[n] + amount[n+1])});
     *     positive means net missing credits, negative means net missing debits
     */
    public record Gap(Account account, LocalDate lastGoodDate, LocalDate firstDateAfterGap, float missingAmount) {}

    /**
     * Detects gaps in the transaction record for a single account.
     *
     * <p>Same-day transaction pairs are excluded from the invariant check. When two OFX exports
     * share a boundary date, transactions on that date may have been imported in different orders,
     * producing balance discrepancies that are import artifacts rather than genuine data gaps. Real
     * gaps always span at least one calendar day boundary.
     *
     * @param account the account to check
     * @return list of gaps detected; empty if no gaps found or fewer than two transactions exist
     */
    public List<Gap> detectGaps(Account account) {
        List<CategorizedTransaction> transactions = categorizedTransactionDao.selectByAccount(account);
        List<Gap> gaps = new ArrayList<>();

        for (int i = 1; i < transactions.size(); i++) {
            CategorizedTransaction prev = transactions.get(i - 1);
            CategorizedTransaction curr = transactions.get(i);

            // Same-day pairs are skipped: balance discrepancies between transactions on the same
            // date are always import-ordering artifacts, not genuine gaps in the data.
            if (prev.getDate().equals(curr.getDate())) {
                continue;
            }

            long prevBalanceCents = Math.round(prev.getBalance() * 100);
            long currAmountCents = Math.round(curr.getAmount() * 100);
            long expectedCents = prevBalanceCents + currAmountCents;
            long actualCents = Math.round(curr.getBalance() * 100);

            if (expectedCents != actualCents) {
                float missingAmount = (actualCents - expectedCents) / 100f;
                gaps.add(new Gap(account, prev.getDate(), curr.getDate(), missingAmount));
                logger.debug(
                        "Gap detected for account {} between {} and {}: missing amount {}",
                        account.getName(),
                        prev.getDate(),
                        curr.getDate(),
                        missingAmount);
            }
        }

        return gaps;
    }

    /**
     * Detects gaps across all accounts.
     *
     * @return list of all gaps detected
     */
    public List<Gap> detectGaps() {
        return detectGaps(ProgressCallback.NOOP);
    }

    /**
     * Detects gaps across all accounts, reporting progress per account via the callback.
     *
     * @param progressCallback called once after each account's scan completes
     * @return list of all gaps detected
     */
    public List<Gap> detectGaps(ProgressCallback progressCallback) {
        List<Account> accounts = accountDao.select();
        List<Gap> allGaps = new ArrayList<>();
        int total = accounts.size();

        for (int i = 0; i < accounts.size(); i++) {
            allGaps.addAll(detectGaps(accounts.get(i)));
            progressCallback.onProgress(i + 1, total);
        }

        return allGaps;
    }

    /**
     * Returns accounts for which gap detection cannot be performed because they have fewer than two
     * transactions. These accounts should be listed as INDETERMINATE in the gaps report.
     *
     * @return list of accounts with fewer than two transactions
     */
    public List<Account> indeterminateAccounts() {
        return accountDao.select().stream()
                .filter(account ->
                        categorizedTransactionDao.selectByAccount(account).size() < 2)
                .collect(Collectors.toList());
    }

    /**
     * Returns the net missing amount for months in [{@code start}, {@code end}] where a gap begins.
     * The gap is attributed to the month containing {@code lastGoodDate}.
     *
     * @param start the beginning of the date range (any day of the starting month)
     * @param end the end of the date range (any day of the ending month)
     * @return map from month-start date to net missing amount for that month
     */
    public Map<LocalDate, Float> gapAmountsByMonth(LocalDate start, LocalDate end) {
        List<Gap> gaps = detectGaps();
        Map<LocalDate, Float> result = new HashMap<>();

        LocalDate startMonth = start.withDayOfMonth(1);
        LocalDate endMonth = end.withDayOfMonth(1);

        for (Gap gap : gaps) {
            LocalDate gapMonth = gap.lastGoodDate().withDayOfMonth(1);
            if (!gapMonth.isBefore(startMonth) && !gapMonth.isAfter(endMonth)) {
                result.merge(gapMonth, gap.missingAmount(), Float::sum);
            }
        }

        return result;
    }

    /**
     * Returns month-start dates for months that fall entirely within a gap — i.e., the gap spans
     * the full month with no transactions recorded for that month.
     *
     * @param start the beginning of the date range to check
     * @param end the end of the date range to check
     * @return set of month-start dates where the entire month is within a gap
     */
    public Set<LocalDate> fullyMissingMonths(LocalDate start, LocalDate end) {
        List<Gap> gaps = detectGaps();
        Set<LocalDate> result = new HashSet<>();

        LocalDate month = start.withDayOfMonth(1);
        LocalDate endMonth = end.withDayOfMonth(1);

        while (!month.isAfter(endMonth)) {
            final LocalDate monthStart = month;
            final LocalDate nextMonth = month.plusMonths(1);

            for (Gap gap : gaps) {
                // The month is fully within the gap if the last good transaction was before this
                // month AND the first post-gap transaction is in the next month or later.
                if (gap.lastGoodDate().isBefore(monthStart)
                        && !gap.firstDateAfterGap().isBefore(nextMonth)) {
                    result.add(monthStart);
                    break;
                }
            }

            month = nextMonth;
        }

        return result;
    }
}
