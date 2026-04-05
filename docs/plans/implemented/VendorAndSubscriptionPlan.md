# Vendor Spending Report and Subscription Detection Plan

**Status:** In planning — open questions remain before implementation begins.

---

## Overview

Two related features that share a common foundation:

1. **Vendor spending report** — groups transactions by vendor and reports total spend per vendor for
   a given date range. Allows users to see where most of their money is going.

2. **Subscription detection** — identifies recurring charges from the same vendor at a consistent
   interval and similar amount. Useful for auditing active subscriptions.

The dependency is clear: vendor grouping is the prerequisite for both features. Subscription
detection is vendor spending with a temporal and amount-consistency dimension added on top. Build
the vendor spending report first, then extend the shared infrastructure for subscription detection.

---

## The Vendor Identity Problem

OFX transaction descriptions are noisy. The same merchant can appear in many forms:

```
NETFLIX.COM
NETFLIX *MONTHLY CA
NETFLIX INC CA
```

Fuzzy string matching on raw name/memo has historically proven unreliable — online vendors like
Spotify and Amazon produce enough variation to cause over-merging and under-merging. The existing
token normalization infrastructure (`TokenNormalizer`, `TransactionToken` table) already handles
this: it strips noise (dates, amounts, location codes, numeric suffixes) and reduces descriptions
to a stable set of semantic tokens.

**Decision**: Use token overlap — the same mechanism that drives `TokenMatchingService` — to cluster
transactions into vendor groups. No new schema objects or normalization passes are needed.

---

## Shared Infrastructure: Vendor Grouping

### Concept

A **vendor group** is a set of transactions whose normalized token sets have pairwise overlap ≥ a
configurable threshold. Each group has a representative display name reconstructed from the
transactions' original descriptions, guided by their shared tokens.

Transactions in the TRANSFER and UNKNOWN categories are excluded from vendor grouping. TRANSFER
transactions are inter-account movements rather than vendor spend. UNKNOWN transactions have not
been categorized and should not influence vendor grouping results.

### Algorithm

Vendor grouping is a connected-components clustering problem over the token overlap graph:

1. Load all `CategorizedTransaction` rows in the date range, with their token sets, in a single
   join query against `CategorizedTransaction` and `TransactionToken`. Exclude TRANSFER and UNKNOWN.

2. Build an inverted index: `token → Set<transactionId>`. Transactions sharing a token are
   candidates for the same vendor group.

3. For each pair of candidate transactions (connected by at least one shared token), compute the
   overlap ratio using the same formula as `TokenMatchingService`:
   ```
   overlapRatio = matchingTokens / min(searchTokenCount, storedTokenCount)
   ```

4. If `overlapRatio ≥ vendor_grouping.overlap_threshold`, the two transactions belong to the same
   vendor group. Use **union-find** to cluster transitively connected transactions.

5. Derive the **display name** for each cluster using the ordered reconstruction algorithm
   described below.

### Display Name: Ordered Token Reconstruction

Picking the single most-common token as the display name produces nonsensical results for
multi-word vendors — "Shoppers Drug Mart" could become just "drug", and "The Home Depot" could
become just "depot". Instead, the display name is reconstructed using the original transaction
descriptions to recover the natural ordering of the shared tokens.

**Algorithm:**

1. Compute the **core token set** = the set of tokens that appear in at least a majority (≥ 60%)
   of transactions in the group. Using a majority threshold rather than strict intersection
   tolerates occasional noise tokens in individual descriptions without losing core words.

2. Add a `normalizeOrdered(String description) → List<String>` method to `TokenNormalizer` that
   runs the identical normalization pipeline as the existing `normalize()` but returns a `List`
   preserving left-to-right positional order instead of a `Set`.

3. For each transaction in the group, call `normalizeOrdered(description)` and walk the result
   left-to-right, collecting only the elements that appear in the core token set — in the order
   they appear. This yields an ordered subsequence of the core tokens as they occur in that
   description.

4. The most common such ordered subsequence across all transactions in the group, joined by spaces
   and title-cased, is the display name. Ties are broken alphabetically.

**Worked examples:**

| Descriptions | Core tokens | Ordered subsequence | Display name |
|---|---|---|---|
| `SHOPPERS DRUG MART #123` / `SHOPPERS DRUG MART TORONTO` | `{shoppers, drug, mart}` | shoppers, drug, mart | **Shoppers Drug Mart** |
| `THE HOME DEPOT #4201` / `HOME DEPOT ONLINE` | `{home, depot}` | home, depot | **Home Depot** |
| `NETFLIX.COM` / `NETFLIX *MONTHLY CA` | `{netflix}` | netflix | **Netflix** |

Note that `THE` is stripped by the stop-word list, so "The Home Depot" correctly yields `{home,
depot}` as core tokens and "Home Depot" as the display name rather than "The Home Depot".

### Why not the existing `findTransactionsWithMatchingTokens`?

That method is designed for *search*: given a new transaction, find which existing transactions
(and therefore categories) it resembles. Vendor grouping is *clustering*: group all existing
transactions with no external query. Different access pattern, different query shape — but the
same mathematical formula for overlap ratio.

### New DAO method

`CategorizedTransactionDao`:
```java
/**
 * Returns all transactions in [startDate, endDate] with their token sets.
 * Excludes TRANSFER and UNKNOWN categories.
 * Used for vendor grouping.
 */
public Map<CategorizedTransaction, Set<String>> selectWithTokensByDateRange(
        LocalDate startDate, LocalDate endDate)
```

The join against `TransactionToken` is done in SQL to avoid N+1 queries. The result is a map from
transaction to its token set, loaded in one round-trip.

### New `VendorGroup` record

In the `service` package:
```java
public record VendorGroup(
    String displayName,           // most-common token across transactions in the group
    List<CategorizedTransaction> transactions,
    float totalAmount,            // sum of transaction amounts (negative = net spend)
    int transactionCount
) {}
```

### New `VendorGroupingService`

```java
public class VendorGroupingService {
    public List<VendorGroup> groupByVendor(LocalDate startDate, LocalDate endDate)
}
```

Sorted by `totalAmount` ascending (largest spends first, since spend amounts are negative).

### Configuration additions

Add a new nested class to `AppConfig`:

```java
public static class VendorGroupingSettings {
    private double overlapThreshold = 0.6;   // default: same as token matching
    // getters/setters
}
```

And in `AppConfig`:
```java
private VendorGroupingSettings vendorGrouping = new VendorGroupingSettings();
```

This is kept separate from `token_matching.overlap_threshold` so the two can be tuned
independently — categorization and vendor clustering may need different sensitivity.

In `config.yaml` this becomes:
```yaml
vendor_grouping:
  overlap_threshold: 0.6
```

---

## Feature 1: Vendor Spending Report

### CLI

Follows the existing `get transactions` pattern:

```
ofxcat get vendors --start-date=START [--end-date=END] [--format=terminal|xlsx]
```

- `--start-date`: Required. Inclusive, `yyyy-mm-dd`.
- `--end-date`: Optional. Inclusive, `yyyy-mm-dd`. Defaults to today.
- `--format`: Optional. `terminal` (default) or `xlsx`.

A new `VENDORS` value is added to the `Concern` enum in `OfxCat`.

### Terminal output

CSV to stdout, sorted by total spend descending (largest absolute spend first):

```
VENDOR, TRANSACTIONS, TOTAL
netflix, 12, -143.88
amazon, 47, -892.14
shoppers drug mart, 8, -201.55
```

The `TOTAL` column is the sum of all transaction amounts for that vendor group (negative = money
out). Vendor name is the group's display name (most-common token, lowercase).

### XLSX output

A two-column table: `VENDOR` and `TOTAL`, sorted by total spend descending. Each cell in the
`TOTAL` column is formatted as currency (`$#,##0.00`).

**Pie chart**: investigate whether fastexcel supports chart generation. If yes, add a pie chart on
the same sheet (or a second sheet) with vendors on the legend and total spend as the values. If
not, document the manual steps to add a pie chart in Excel after export. Either way this is a
nice-to-have and does not block the feature.

### New `VendorSpendingService`

```java
public class VendorSpendingService {
    public List<VendorGroup> getVendorSpend(LocalDate startDate, LocalDate endDate)
    public Path writeToFile(List<VendorGroup> groups, LocalDate startDate, LocalDate endDate, Path outputFile)
            throws IOException
}
```

`getVendorSpend` delegates to `VendorGroupingService` and returns the groups sorted by total
amount. `writeToFile` writes the XLSX output.

---

## Feature 2: Subscription Detection

### What is a subscription?

A subscription is a vendor group where:

1. There are at least `min_occurrences` transactions (configurable, default 3).
2. The amounts are consistent: each transaction's amount is within `amount_tolerance` (configurable,
   default 5%) of the group's median amount.
3. The intervals between consecutive transactions (sorted by date) are consistent: each interval
   falls within `interval_tolerance_days` (configurable, default 3 days) of one of the known
   billing periods:

   | Period name | Days |
   |-------------|------|
   | Weekly      | 7    |
   | Biweekly    | 14   |
   | Monthly     | 30   |
   | Quarterly   | 91   |
   | Annual      | 365  |

   A vendor group qualifies as a subscription only if **all** inter-transaction intervals are
   consistent with the **same** billing period.

### CLI

```
ofxcat get subscriptions [--start-date=START] [--end-date=END]
```

Both date arguments are optional. If omitted, defaults to the last 13 months (enough to catch
annual subscriptions). Start and end date are provided for tuning and for cross-period comparison.

A new `SUBSCRIPTIONS` value is added to the `Concern` enum.

### Terminal output

```
VENDOR, FREQUENCY, TYPICAL AMOUNT, LAST CHARGE, NEXT EXPECTED
netflix, MONTHLY, -11.99, 2026-03-01, 2026-04-01
spotify, MONTHLY, -9.99, 2026-02-28, 2026-03-28
dropbox, ANNUAL, -119.99, 2025-11-15, 2026-11-15
```

`TYPICAL AMOUNT` is the median transaction amount. `NEXT EXPECTED` is `LAST CHARGE` + one billing
period.

### Configuration additions

Add to `AppConfig`:

```java
public static class SubscriptionDetectionSettings {
    private int minOccurrences = 3;
    private double amountTolerance = 0.05;     // 5% variance allowed
    private int intervalToleranceDays = 3;     // ± days from canonical period
    // getters/setters
}
```

In `config.yaml`:
```yaml
subscription_detection:
  min_occurrences: 3
  amount_tolerance: 0.05
  interval_tolerance_days: 3
```

### New `SubscriptionDetectionService`

```java
public record Subscription(
    String vendorName,
    String frequency,           // "WEEKLY", "MONTHLY", etc.
    float typicalAmount,        // median amount
    LocalDate lastCharge,
    LocalDate nextExpected
) {}

public class SubscriptionDetectionService {
    public List<Subscription> detectSubscriptions(LocalDate startDate, LocalDate endDate)
}
```

The service delegates vendor clustering to `VendorGroupingService`, then evaluates each group
against the subscription criteria.

---

## Implementation Phases

### Phase 1 — Shared infrastructure

- Add `VendorGroupingSettings` and `SubscriptionDetectionSettings` to `AppConfig`. Update
  `AppConfigLoader` and `config.yaml` defaults.
- Add `selectWithTokensByDateRange(startDate, endDate)` to `CategorizedTransactionDao`.
- Implement `VendorGroupingService` with union-find clustering.
- Tests:
  - Transactions with identical token sets → single group
  - Transactions with overlapping token sets above threshold → single group
  - Transactions with overlap below threshold → separate groups
  - Transitive clustering: A↔B and B↔C but A and C share no tokens → one group
  - TRANSFER and UNKNOWN transactions excluded
  - Display name for single-token vendor (e.g. "Netflix") → title-cased token
  - Display name for multi-token vendor (e.g. "Shoppers Drug Mart") → tokens reconstructed in description order
  - Stop words excluded from core tokens (e.g. "The Home Depot" → "Home Depot")
  - Noise tokens (numbers, short tokens) excluded from core tokens
  - Majority threshold: token appearing in 60%+ of transactions included in core set
  - Ties in most-common ordered subsequence broken alphabetically
  - Empty date range → empty result

### Phase 2 — Vendor spending report

- Implement `VendorSpendingService`.
- Add `VENDORS` to `Concern` enum in `OfxCat`; wire up `get vendors` command.
- Investigate fastexcel chart support; implement pie chart or document manual steps.
- Tests:
  - Single vendor group → correct total
  - Multiple groups sorted by spend descending
  - Date range filtering (transactions outside range excluded)
  - XLSX output format

### Phase 3 — Subscription detection

- Implement `SubscriptionDetectionService`.
- Add `SUBSCRIPTIONS` to `Concern` enum; wire up `get subscriptions` command.
- Tests:
  - Monthly subscription (all intervals ≈ 30 days, all amounts within tolerance) → detected
  - Annual subscription → detected
  - Irregular intervals → not detected
  - Amount variance exceeds tolerance → not detected
  - Fewer than `min_occurrences` transactions → not detected
  - `NEXT EXPECTED` computed correctly per billing period
  - Configurable thresholds are respected

### Phase 4 — Threshold tuning (deferred)

Once the user has access to the production database, run `get vendors` and `get subscriptions`
against real data and adjust default thresholds in `config.yaml` based on observed results.
Document the tuning process and any threshold changes.

---

## Files to Create or Modify

| File | Phase | Change |
|------|-------|--------|
| `AppConfig.java` | 1 | Add `VendorGroupingSettings`, `SubscriptionDetectionSettings` |
| `AppConfigLoader.java` | 1 | Update deserialization defaults |
| `TokenNormalizer.java` | 1 | Add `normalizeOrdered(String) → List<String>` for display name reconstruction |
| `TokenNormalizerTest.java` | 1 | Tests for `normalizeOrdered` (order preserved, same filtering as `normalize`) |
| `CategorizedTransactionDao.java` | 1 | Add `selectWithTokensByDateRange` |
| `CategorizedTransactionDaoTest.java` | 1 | Tests for new DAO method |
| `VendorGroupingService.java` (new) | 1 | Token overlap clustering with union-find and ordered display name reconstruction |
| `VendorGroupingServiceTest.java` (new) | 1 | Clustering unit tests including display name reconstruction |
| `VendorGroup.java` (new) | 1 | Record in `service` package |
| `VendorSpendingService.java` (new) | 2 | Aggregation and XLSX output |
| `VendorSpendingServiceTest.java` (new) | 2 | Report unit tests |
| `OfxCat.java` | 2, 3 | Add `VENDORS`, `SUBSCRIPTIONS` to `Concern` enum; wire commands |
| `CLIModule.java` | 2, 3 | Bind new services in Guice |
| `SubscriptionDetectionService.java` (new) | 3 | Subscription detection logic |
| `SubscriptionDetectionServiceTest.java` (new) | 3 | Detection unit tests |

---

## Decisions Made

| Question | Decision |
|----------|----------|
| Vendor identity approach | Token overlap clustering (reuse existing `TransactionToken` infrastructure) |
| New schema objects for vendor | No — derive vendor groups dynamically from existing tokens |
| Vendor display name | Ordered token reconstruction: find core tokens (≥60% of transactions), recover their natural order from original descriptions via `normalizeOrdered()`, title-case the most common ordered subsequence |
| Categories excluded from vendor grouping | TRANSFER and UNKNOWN |
| Separate overlap threshold for vendor grouping | Yes — `vendor_grouping.overlap_threshold` independent of `token_matching.overlap_threshold` |
| Config location | Extend existing `AppConfig` / `config.yaml` — no new config file |
| CLI pattern for vendor report | `get vendors --start-date/--end-date`, consistent with `get transactions` |
| CLI pattern for subscriptions | `get subscriptions` with optional date range; defaults to last 13 months |
| Feature order | Vendor spending report first (Phase 2), subscriptions second (Phase 3) |
| Subscription amount comparison | Against median of the group, not mean (more robust to outliers) |
| Threshold tuning | Deferred to Phase 4 after production database access |

---

## Open Questions

| Question | Notes |
|----------|-------|
| Does fastexcel support pie charts? | Investigate during Phase 2. Nice-to-have; not a blocker. |
| Should `get subscriptions` produce XLSX output? | Not planned initially; terminal output is sufficient. Add later if needed. |
| How should credits (positive amounts) be handled in vendor grouping? | E.g. refunds from Amazon. Include them in the group total but flag them? Exclude from subscription detection? Decide during Phase 3 implementation. |
| What is the right default `overlap_threshold` for vendor grouping? | Starting at 0.6 (same as token matching). May need tuning in Phase 4 against real data. |
