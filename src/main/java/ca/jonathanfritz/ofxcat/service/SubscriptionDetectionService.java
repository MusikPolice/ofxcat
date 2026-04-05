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
 *   <li>It contains at least {@code minOccurrences} transactions (or {@code annualMinOccurrences}
 *       for ANNUAL billing periods).
 *   <li>Every transaction amount is within {@code amountTolerance} (fraction) of the group's median
 *       amount.
 *   <li>All inter-transaction intervals (sorted by date) are within {@code intervalToleranceDays}
 *       of the same canonical billing period (weekly/biweekly/monthly/quarterly/annual).
 * </ol>
 */
public class SubscriptionDetectionService {

    private final VendorGroupingService vendorGroupingService;
    private final int minOccurrences;
    private final int annualMinOccurrences;
    private final double amountTolerance;
    private final int intervalToleranceDays;

    @Inject
    public SubscriptionDetectionService(VendorGroupingService vendorGroupingService, AppConfig appConfig) {
        this.vendorGroupingService = vendorGroupingService;
        this.minOccurrences = appConfig.getSubscriptionDetection().getMinOccurrences();
        this.annualMinOccurrences = appConfig.getSubscriptionDetection().getAnnualMinOccurrences();
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

    /**
     * Returns explain results for every vendor group in the date range: detected subscriptions and
     * rejected groups with the reason for rejection.
     *
     * @param startDate start of the date range, inclusive
     * @param endDate end of the date range, inclusive
     * @return explain results sorted by vendor display name
     */
    public List<ExplainResult> explainSubscriptions(LocalDate startDate, LocalDate endDate) {
        return vendorGroupingService.groupByVendor(startDate, endDate).stream()
                .map(this::toExplainResult)
                .sorted(Comparator.comparing(r -> r.vendorName().toLowerCase()))
                .toList();
    }

    /**
     * Returns true if {@code days} is within {@code intervalToleranceDays} of any integer multiple
     * of {@code periodDays} between 1× and {@code maxSkipMultiplier}×. This allows subscriptions
     * that occasionally skip a billing cycle to still be detected.
     */
    private boolean intervalMatchesPeriod(long days, int periodDays, int maxSkipMultiplier) {
        if (days <= 0) {
            return false;
        }
        long n = Math.round((double) days / periodDays);
        return n >= 1 && n <= maxSkipMultiplier && Math.abs(days - n * periodDays) <= intervalToleranceDays;
    }

    private ExplainResult toExplainResult(VendorGroup group) {
        List<CategorizedTransaction> sorted = group.transactions().stream()
                .sorted(Comparator.comparing(CategorizedTransaction::getDate))
                .toList();

        int count = sorted.size();

        // Check min_occurrences (use a provisional threshold; we don't know the period yet)
        if (count < Math.min(minOccurrences, annualMinOccurrences)) {
            return ExplainResult.rejected(
                    group.displayName(),
                    count,
                    List.of(),
                    RejectionReason.TOO_FEW_TRANSACTIONS,
                    String.format("%d < %d", count, Math.min(minOccurrences, annualMinOccurrences)));
        }

        List<Float> sortedAmounts =
                sorted.stream().map(CategorizedTransaction::getAmount).sorted().toList();
        float median = sortedAmounts.get(sortedAmounts.size() / 2);

        if (median == 0f) {
            return ExplainResult.rejected(
                    group.displayName(), count, List.of(), RejectionReason.AMOUNT_VARIANCE, "median is zero");
        }

        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(
                    sorted.get(i - 1).getDate(), sorted.get(i).getDate()));
        }

        // Check amount consistency
        boolean amountsConsistent =
                sorted.stream().allMatch(tx -> Math.abs(tx.getAmount() - median) <= Math.abs(median) * amountTolerance);
        if (!amountsConsistent) {
            float maxDeviation = sorted.stream()
                    .map(tx -> Math.abs(tx.getAmount() - median) / Math.abs(median))
                    .max(Float::compareTo)
                    .orElse(0f);
            return ExplainResult.rejected(
                    group.displayName(),
                    count,
                    intervals,
                    RejectionReason.AMOUNT_VARIANCE,
                    String.format("%.1f%% > %.1f%% tolerance", maxDeviation * 100, amountTolerance * 100));
        }

        // Check interval consistency against billing periods
        for (BillingPeriod period : BillingPeriod.values()) {
            int threshold = period == BillingPeriod.ANNUAL ? annualMinOccurrences : minOccurrences;
            if (count < threshold) {
                continue;
            }
            boolean allMatch =
                    intervals.stream().allMatch(d -> intervalMatchesPeriod(d, period.days, period.maxSkipMultiplier));
            if (allMatch) {
                LocalDate lastCharge = sorted.get(sorted.size() - 1).getDate();
                return ExplainResult.detected(new Subscription(
                        group.displayName(), period.name(), median, lastCharge, lastCharge.plusDays(period.days)));
            }
        }

        // No period matched — find best candidate for the explanation message
        Long medianInterval = intervals.isEmpty() ? null : intervals.get(intervals.size() / 2);
        String detail = medianInterval == null
                ? "no intervals"
                : String.format(
                        "median interval %d days does not match any billing period (±%d)",
                        medianInterval, intervalToleranceDays);

        // Re-check if rejection is due to min_occurrences for all non-matching periods
        boolean allPeriodsFailThreshold = true;
        for (BillingPeriod period : BillingPeriod.values()) {
            int threshold = period == BillingPeriod.ANNUAL ? annualMinOccurrences : minOccurrences;
            if (count >= threshold) {
                allPeriodsFailThreshold = false;
                break;
            }
        }
        if (allPeriodsFailThreshold) {
            return ExplainResult.rejected(
                    group.displayName(),
                    count,
                    intervals,
                    RejectionReason.TOO_FEW_TRANSACTIONS,
                    String.format("%d < %d", count, minOccurrences));
        }

        return ExplainResult.rejected(group.displayName(), count, intervals, RejectionReason.INTERVAL_MISMATCH, detail);
    }

    private Optional<Subscription> toSubscription(VendorGroup group) {
        if (group.transactionCount() < minOccurrences) {
            // Check if it could still qualify as annual (lower threshold)
            if (group.transactionCount() < annualMinOccurrences) {
                return Optional.empty();
            }
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
            int threshold = period == BillingPeriod.ANNUAL ? annualMinOccurrences : minOccurrences;
            if (group.transactionCount() < threshold) {
                continue;
            }
            boolean allMatch =
                    intervals.stream().allMatch(d -> intervalMatchesPeriod(d, period.days, period.maxSkipMultiplier));
            if (allMatch) {
                LocalDate lastCharge = sorted.get(sorted.size() - 1).getDate();
                return Optional.of(new Subscription(
                        group.displayName(), period.name(), median, lastCharge, lastCharge.plusDays(period.days)));
            }
        }

        return Optional.empty();
    }

    private enum BillingPeriod {
        // Weekly and biweekly subscriptions rarely skip cycles; no multiplier allowed.
        WEEKLY(7, 1),
        BIWEEKLY(14, 1),
        // Monthly subscriptions may occasionally skip 1-2 cycles (paused, payment failure, etc.).
        MONTHLY(30, 3),
        QUARTERLY(91, 1),
        ANNUAL(365, 1);

        final int days;
        final int maxSkipMultiplier;

        BillingPeriod(int days, int maxSkipMultiplier) {
            this.days = days;
            this.maxSkipMultiplier = maxSkipMultiplier;
        }
    }

    /** The reason a vendor group was not detected as a subscription. */
    public enum RejectionReason {
        TOO_FEW_TRANSACTIONS,
        AMOUNT_VARIANCE,
        INTERVAL_MISMATCH
    }

    /**
     * The result of evaluating a single vendor group for subscription detection, used by the
     * {@code --explain} output mode.
     */
    public static final class ExplainResult {
        private final String vendorName;
        private final boolean detected;
        private final Subscription subscription;
        private final int transactionCount;
        private final List<Long> intervals;
        private final RejectionReason rejectionReason;
        private final String rejectionDetail;

        private ExplainResult(
                String vendorName,
                boolean detected,
                Subscription subscription,
                int transactionCount,
                List<Long> intervals,
                RejectionReason rejectionReason,
                String rejectionDetail) {
            this.vendorName = vendorName;
            this.detected = detected;
            this.subscription = subscription;
            this.transactionCount = transactionCount;
            this.intervals = intervals;
            this.rejectionReason = rejectionReason;
            this.rejectionDetail = rejectionDetail;
        }

        static ExplainResult detected(Subscription subscription) {
            return new ExplainResult(subscription.vendorName(), true, subscription, 0, List.of(), null, null);
        }

        static ExplainResult rejected(
                String vendorName, int transactionCount, List<Long> intervals, RejectionReason reason, String detail) {
            return new ExplainResult(vendorName, false, null, transactionCount, intervals, reason, detail);
        }

        public String vendorName() {
            return vendorName;
        }

        public boolean isDetected() {
            return detected;
        }

        public Subscription subscription() {
            return subscription;
        }

        public int transactionCount() {
            return transactionCount;
        }

        public List<Long> intervals() {
            return intervals;
        }

        public RejectionReason rejectionReason() {
            return rejectionReason;
        }

        public String rejectionDetail() {
            return rejectionDetail;
        }
    }
}
