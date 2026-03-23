package ca.jonathanfritz.ofxcat.service;

import ca.jonathanfritz.ofxcat.config.AppConfig;
import ca.jonathanfritz.ofxcat.datastore.dto.CategorizedTransaction;
import ca.jonathanfritz.ofxcat.model.Subscription;
import ca.jonathanfritz.ofxcat.model.VendorGroup;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Detects recurring subscriptions by inspecting vendor groups for consistent charge intervals and
 * amounts.
 *
 * <p>A vendor group qualifies as a subscription when:
 *
 * <ol>
 *   <li>It contains at least {@code minOccurrences} transactions.
 *   <li>Every transaction amount is within {@code amountTolerance} (fraction) of the group's median
 *       amount.
 *   <li>All inter-transaction intervals (sorted by date) are within {@code intervalToleranceDays}
 *       of the same canonical billing period (weekly/biweekly/monthly/quarterly/annual).
 * </ol>
 */
public class SubscriptionDetectionService {

    private final VendorGroupingService vendorGroupingService;
    private final int minOccurrences;
    private final double amountTolerance;
    private final int intervalToleranceDays;

    @Inject
    public SubscriptionDetectionService(VendorGroupingService vendorGroupingService, AppConfig appConfig) {
        this.vendorGroupingService = vendorGroupingService;
        this.minOccurrences = appConfig.getSubscriptionDetection().getMinOccurrences();
        this.amountTolerance = appConfig.getSubscriptionDetection().getAmountTolerance();
        this.intervalToleranceDays = appConfig.getSubscriptionDetection().getIntervalToleranceDays();
    }

    /**
     * Returns detected subscriptions for the given date range, sorted by typical amount ascending
     * (largest absolute spends first, since spend amounts are negative).
     *
     * @param startDate start of the date range, inclusive
     * @param endDate end of the date range, inclusive
     * @return detected subscriptions sorted by typical amount ascending
     */
    public List<Subscription> detectSubscriptions(LocalDate startDate, LocalDate endDate) {
        return vendorGroupingService.groupByVendor(startDate, endDate).stream()
                .map(this::toSubscription)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingDouble(Subscription::typicalAmount))
                .toList();
    }

    private Optional<Subscription> toSubscription(VendorGroup group) {
        if (group.transactionCount() < minOccurrences) {
            return Optional.empty();
        }

        // Sort transactions by date to compute intervals in chronological order.
        List<CategorizedTransaction> sorted = group.transactions().stream()
                .sorted(Comparator.comparing(CategorizedTransaction::getDate))
                .toList();

        // Compute the median amount (sorted ascending; negative amounts sort most-negative first).
        List<Float> sortedAmounts =
                sorted.stream().map(CategorizedTransaction::getAmount).sorted().toList();
        float median = sortedAmounts.get(sortedAmounts.size() / 2);

        // Reject groups where any transaction deviates from the median beyond the tolerance.
        if (median == 0f) {
            return Optional.empty();
        }
        boolean amountsConsistent =
                sorted.stream().allMatch(tx -> Math.abs(tx.getAmount() - median) <= Math.abs(median) * amountTolerance);
        if (!amountsConsistent) {
            return Optional.empty();
        }

        // Compute day-gaps between consecutive transactions.
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(
                    sorted.get(i - 1).getDate(), sorted.get(i).getDate()));
        }

        // Find the first billing period whose canonical length is within intervalToleranceDays of
        // every observed interval. Periods are ordered shortest-first so biweekly is preferred over
        // monthly when both would technically match.
        for (BillingPeriod period : BillingPeriod.values()) {
            boolean allMatch = intervals.stream().allMatch(d -> Math.abs(d - period.days) <= intervalToleranceDays);
            if (allMatch) {
                LocalDate lastCharge = sorted.get(sorted.size() - 1).getDate();
                return Optional.of(new Subscription(
                        group.displayName(), period.name(), median, lastCharge, lastCharge.plusDays(period.days)));
            }
        }

        return Optional.empty();
    }

    private enum BillingPeriod {
        WEEKLY(7),
        BIWEEKLY(14),
        MONTHLY(30),
        QUARTERLY(91),
        ANNUAL(365);

        final int days;

        BillingPeriod(int days) {
            this.days = days;
        }
    }
}
