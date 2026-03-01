# Gap Detection Plan

**Status:** Ready for implementation — all decisions made.

---

## Problem Statement

OFX files contain the transactions visible in your bank's online history at the time of export, plus
the account balance as of the export date. The bank only makes a rolling window of history available
(typically 60–90 days), so if you forget to export before old transactions scroll out of the window,
those transactions are gone and can't be recovered.

The result is a database that looks complete — chronologically ordered transactions with balances —
but has invisible holes. A monthly spending report for a month with missing transactions is silently
inaccurate: it sums only what we know about, not what we should know about.

The goal of this feature is to detect those holes and flag affected months so the user knows when a
report can be trusted and when it can't.

---

## Key Invariant

`TransactionImportService` already computes and stores a running account balance on each transaction.
The logic is:

```
initialBalance = ofxExport.balance.amount − sum(all transaction amounts in this import)
balance[0]     = initialBalance + amount[0]
balance[1]     = balance[0]     + amount[1]
...
balance[n]     = balance[n−1]   + amount[n]
```

The `balance` field on each `CategorizedTransaction` therefore represents the account balance
immediately after that transaction was applied.

For any two **consecutive** transactions for the same account (sorted chronologically), this must hold:

```
balance[n+1]  ==  balance[n] + amount[n+1]
```

If it doesn't, one or more transactions are missing between position n and n+1 in the record. The
magnitude of the discrepancy tells us how much money is unaccounted for:

```
missingAmount  =  balance[n+1] − (balance[n] + amount[n+1])
```

A positive `missingAmount` means net income was missing (credits that weren't captured). Negative
means net spending was missed.

---

## Why the Invariant Holds Across Multiple Imports

When a second OFX file overlaps with a previous one (e.g. the user exports Jan 1–31 and then
exports Jan 15–Feb 28):

- Duplicate transactions (Jan 15–31 appearing in both files) are detected by fitId and skipped.
- The new transactions (Feb 1–28) have their balance computed from the Feb 28 OFX ending balance,
  walking backward through all transactions in the file including the Jan 15–31 duplicates.
- The balance assigned to Feb 1 is therefore consistent with the balance that was assigned to Jan 31
  in the first import, provided there are no missing transactions between the two export windows.

This means overlapping imports with deduplication do **not** create false positives: the invariant
check gives a clean result at every import boundary as long as the two OFX files together cover the
full date range with no gaps.

---

## Sort Order for Consecutive Pairs

The OFX format carries only a date, not a time. Multiple transactions can share the same date.

During import, `TransactionImportService` sorts by date using a stable comparator. Within a single
day, the order is the OFX file's natural order. Transactions for an account are then inserted into
the database in this order, so their auto-increment IDs increase monotonically within a session and
within a day.

For gap detection, consecutive pairs should be identified using:

```sql
SELECT * FROM CategorizedTransaction
WHERE account_id = ?
ORDER BY date ASC, id ASC
```

Sorting by `(date, id)` gives the same relative ordering that was used during import. The invariant
holds between every consecutive pair in this ordering.

---

## Floating-Point Arithmetic

`balance` and `amount` are stored as `ROUND(value, 2)` — rounded to the nearest cent. Comparing
stored float values with `==` is unreliable because `balance[n] + amount[n+1]` computed in float
may not reproduce the stored `balance[n+1]` exactly even when both are correctly rounded.

The safest approach is to convert to integer cents before comparing:

```java
long balanceCents   = Math.round(balance * 100);
long amountCents    = Math.round(amount  * 100);
long expectedCents  = previousBalanceCents + amountCents;
// gap if: expectedCents != actualBalanceCents
```

`Math.round(float * 100)` gives the correct result for values stored to 2dp, because float has
sufficient precision (~7 significant digits) for any realistic account balance.

---

## What a Gap Record Contains

```
account           — the Account where the gap was detected
lastGoodDate      — the date of the last transaction before the gap
firstDateAfterGap — the date of the first transaction after the gap
missingAmount     — balance[n+1] − (balance[n] + amount[n+1]), in dollars
```

`missingAmount > 0` indicates net missing credits (e.g. income not captured).
`missingAmount < 0` indicates net missing debits (e.g. spending not captured).
Either way, some transactions are unknown.

---

## GAP Column in the Monthly Report

Rather than flagging months with asterisks or warning banners, the monthly report will include a
`GAP` column (rightmost, after `TOTAL`) that shows the net missing amount for each month where a
gap exists, and is blank otherwise. This keeps the `TOTAL` column accurate — money left the account
whether or not we know the details — and makes the t3m/t6m/avg statistics meaningful rather than
silently understated.

The `GAP` column is intentionally distinct from `UNKNOWN` (which holds imported-but-uncategorized
transactions). Mixing the two would make it impossible to distinguish "lazy categorization" from
"missing data."

### Net vs. Gross Limitation

`missingAmount` is the **net** change across all missing transactions, not the sum of their absolute
values. If a gap contains $2,000 in expenses and a $1,800 paycheck, `missingAmount` is −$200. The
report shows a $200 gap when the actual untracked spending was $2,000. This is an unavoidable
consequence of working from balance differences rather than individual transactions. A footnote in
the README and XLSX output should document this so users understand what the column represents.

### Attribution for Multi-Month Gaps

A single `Gap` record has one `missingAmount` that covers all missing transactions from
`lastGoodDate` to `firstDateAfterGap`, potentially spanning several months. Since the amount cannot
be split accurately across months without knowing the actual transactions, it is attributed in full
to the month containing `lastGoodDate` — the month where the gap begins. This is the last month
with a solid anchor before data disappears.

For months that fall entirely within a gap (no transactions from that account at all), there is no
gap record to pull a value from, because the sequential scan finds no transaction pair to compare.
These months show a special marker in the GAP column (e.g. the string `"GAP"`) to signal that the
entire month's data is absent rather than partially missing. Users can run `get gaps` for the full
date-range and magnitude details.

### GAP Column in Stats Rows

The `GAP` column participates in t3m, t6m, avg, and total stats the same way category columns do.
Months with the `"GAP"` string marker (entirely missing) are treated as 0.00 in the statistical
calculations, consistent with how zero-spend months are handled elsewhere.

---

## Gap Closure: Importing Missing Transactions

Gaps are not stored in the database — they are computed dynamically each time `detectGaps()` runs
by walking the transaction record and checking the balance invariant. This means **gap closure
requires no special logic**: importing the OFX file that contains the missing transactions is
sufficient. On the next call to `detectGaps()`, the invariant is satisfied and the gap does not
appear.

### Why the Math Works

If the missing OFX file accurately represents the account during the gap period, its ending balance
is consistent with the state of the surrounding imports. The balance invariant holds at every import
boundary (see *Why the Invariant Holds Across Multiple Imports*), so the gap disappears naturally.

### Out-of-Order Imports

Importing a fill-in file after later files have already been imported also works correctly. The gap
detection query sorts by `(date, id)` with date as the primary key, so fill-in transactions slot
into their correct date position regardless of their database IDs or the order in which files were
imported.

### Known Limitation: Same-Day Transactions Across Import Boundaries

If a single calendar day contains transactions from both an original import and a fill-in import,
sorting by `(date, id)` may not reproduce the correct intra-day sequence. Fill-in transactions
receive higher IDs and sort after the originals for the same date, even if the fill-in OFX file
placed them earlier in the day. Because the two sets of same-day transactions were anchored to
different OFX ending balances, the invariant check may report a spurious small gap within that day
after the fill-in import.

This does not affect month-level spending totals and will not manifest as a false gap at the month
boundary. It is a cosmetic artefact of the limitation that OFX files carry only a date, not a time
of day. It should not be mistaken for a real gap.

---

## Edge Cases

| Case | Behaviour |
|------|-----------|
| Account has zero transactions | No consecutive pairs → no gaps → considered complete |
| Account has exactly one transaction | Same — can't form a pair, no gap possible |
| Account has no transactions in a date range | No gap flagged; absence of data is not a detectable gap |
| Same-day transactions (multiple per day) | Sort by `(date, id)` gives deterministic order; invariant checked between each consecutive pair |
| Transfer transactions (TRANSFER category) | These have balances like all other transactions and participate in the invariant check. Excluding them would create false positives. |
| Two imports covering exactly contiguous ranges | No gap at the boundary (see *Why the Invariant Holds* above) |

---

## Implementation Plan

### Phase 1 — Gap detection in the DAO and a new service

**Rename `MigrationProgressCallback` → `ProgressCallback`**

`MigrationProgressCallback` is already the right shape (`@FunctionalInterface`, `onProgress(int
current, int total)`, `NOOP` constant) but the name ties it to migration. Rename the interface to
`ProgressCallback` in the `service` package and update the existing usages in
`TokenMigrationService` and `OfxCat`. This rename is part of Phase 1 since gap detection is the
first second consumer of the interface.

**`CategorizedTransactionDao`**

Add:
```java
/**
 * Returns all transactions for the given account, sorted by date then insertion order.
 * Used for gap detection.
 */
public List<CategorizedTransaction> selectByAccount(Account account)
```

Query: `SELECT * FROM CategorizedTransaction WHERE account_id = ? ORDER BY date ASC, id ASC`

**New `GapDetectionService`** in the `service` package

```java
public record Gap(Account account, LocalDate lastGoodDate, LocalDate firstDateAfterGap, float missingAmount) {}

public List<Gap> detectGaps(Account account)
public List<Gap> detectGaps()                              // across all accounts; calls detectGaps(account) per account
public List<Gap> detectGaps(ProgressCallback callback)    // with progress reporting
public Map<LocalDate, Float> gapAmountsByMonth(LocalDate start, LocalDate end)
    // returns month-start → net missing amount for months where a gap starts in that month
    // used by ReportingService to populate the GAP column
public Set<LocalDate> fullyMissingMonths(LocalDate start, LocalDate end)
    // returns month-starts for months that fall entirely within a gap (no transactions at all)
    // used by ReportingService to place the "GAP" string marker
```

Progress is reported per account (one `onProgress` call after each account's scan completes),
not per transaction. The outer loop is over accounts; each inner scan loads one account's
transactions at a time, which provides natural memory partitioning.

Core algorithm for a single account:
1. Load all transactions for the account sorted by `(date, id)`
2. Walk consecutive pairs; for each pair convert balance and amount to integer cents
3. If `expectedCents != actualCents`, emit a `Gap` record

**Why no paging is needed**

The algorithm only ever needs the previous transaction in memory to check the invariant — it is
inherently a streaming one-pair-at-a-time operation. However, SQLite's JDBC driver materialises
the entire `ResultSet` in memory regardless of fetch size, so true cursor streaming is not
available. Loading one account at a time via `selectByAccount()` is the practical equivalent
and provides sufficient partitioning.

Explicit `LIMIT`/`OFFSET` pagination would add complexity at page boundaries (the last transaction
on page N must be carried forward as "previous" for the first pair check on page N+1) with no
meaningful benefit. At realistic data sizes — tens of thousands of transactions across several
accounts — the per-account in-memory footprint is a few megabytes at most and is not a concern.

**`AccountDao`** — verify or add `selectAll()` if not present (needed by `detectGaps()` to enumerate
accounts).

**Tests** (`GapDetectionServiceTest`, `CategorizedTransactionDaoTest`):
- No transactions → no gaps
- One transaction → no gaps
- Two consecutive transactions, invariant holds → no gaps
- Two consecutive transactions, invariant violated → one gap with correct `missingAmount`
- Gap at month boundary (last day of month M to first day of month N+1)
- Multiple gaps in one account
- Two accounts, gap in one but not the other
- Same-day transactions, all consistent → no gaps
- Progress callback receives one call per account

### Phase 2 — `get gaps` CLI command

Add a `get gaps` subcommand to `OfxCat`. Output format (CSV to terminal):

```
ACCOUNT, GAP FROM, GAP TO, MISSING AMOUNT
Chequing, 2022-01-15, 2022-02-01, -342.50
Visa, 2022-03-08, 2022-03-22, -198.00
Savings, INDETERMINATE, INDETERMINATE, INDETERMINATE
```

Accounts with fewer than two transactions cannot have gaps detected. They are listed with
`INDETERMINATE` in all value columns and a trailing note explaining that gap detection requires at
least two transactions. They are never silently omitted.

If no gaps and no indeterminate accounts: print `"No gaps detected."` and exit cleanly.

This fits the existing `get accounts` / `get categories` / `get transactions` pattern.

### Phase 3 — Integration with `get transactions`

Add a `GAP` column as the rightmost column in the monthly report (after `TOTAL`), in both the
terminal CSV and XLSX output paths.

For each month row:
- Call `GapDetectionService.gapAmountsByMonth(start, end)` to get the net missing amount for months
  where a gap starts. If the month has a gap amount, display it formatted as currency.
- Call `GapDetectionService.fullyMissingMonths(start, end)` to identify months entirely within a
  gap. For those months, display the string `"GAP"` in the GAP column instead of a number.
- Months with no gap show a blank GAP cell.

The `GAP` column header is `GAP`. The stats rows (t3m, t6m, avg, total) include the GAP column,
treating `"GAP"` string months as 0.00.

Add a note to the terminal output footer and an XLSX cell comment or footnote row explaining:
> GAP values are net balance differences and may understate actual missing activity if the gap
> includes both credits and debits.

---

## Files to Modify

| File | Phase | Change |
|------|-------|--------|
| `MigrationProgressCallback.java` | 1 | Rename to `ProgressCallback` |
| `TokenMigrationService.java` | 1 | Update references to renamed interface |
| `OfxCat.java` | 1 | Update references to renamed interface |
| `CategorizedTransactionDao.java` | 1 | Add `selectByAccount(Account)` |
| `AccountDao.java` | 1 | Verify `selectAll()` exists; add if not |
| `GapDetectionService.java` (new) | 1 | Core gap detection logic with `ProgressCallback` support |
| `GapDetectionServiceTest.java` (new) | 1 | Unit tests for all gap scenarios |
| `CategorizedTransactionDaoTest.java` | 1 | Tests for `selectByAccount` |
| `OfxCat.java` | 2 | Add `get gaps` command, wire `GapDetectionService` |
| `CLIModule.java` | 2 | Bind `GapDetectionService` in Guice |
| `ReportingService.java` | 3 | Add GAP column to terminal and XLSX output; call gap service |
| `ReportingServiceTest.java` | 3 | Tests for GAP column values, "GAP" string marker, stats rows |
| `README.md` | 3 | Document `get gaps` command |
| `docs/CodebaseOverview.md` | 3 | Update reporting section |

---

## Decisions Made

| Question | Decision |
|----------|----------|
| How to surface gaps in `get transactions` | Dedicated `GAP` column (rightmost, after `TOTAL`), not asterisks or warning banners |
| What goes in the GAP column | Net missing amount (currency) for months where a gap starts; `"GAP"` string for months entirely within a gap; blank otherwise |
| GAP column in stats rows | Included; `"GAP"` string months count as 0.00 |
| Attribution for multi-month gaps | Full `missingAmount` attributed to the month of `lastGoodDate` (where the gap begins) |
| Net vs. gross limitation | Document in README and as a report footnote; no attempt to estimate gross |
| GAP vs. UNKNOWN column | Separate — UNKNOWN is for imported-but-uncategorized transactions; GAP is inferred data only |
| `get gaps` date filtering | No filtering — always show all gaps. Add later if needed (YAGNI). |
| Accounts with a single transaction | Show as "indeterminate" in `get gaps` output with a note that gap detection requires at least two transactions. Do not silently omit. |

## Open Questions

None — all decisions have been made. Implementation may proceed.
