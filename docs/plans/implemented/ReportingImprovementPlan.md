# Reporting Improvement Plan

**Status: Implemented** — All phases (1a, 1b, 2, 3) and the TRANSFER exclusion fix are complete and merged.

## Current State

`ReportingService.reportTransactionsMonthly()` generates a CSV table printed to the terminal. Categories are columns, months are rows, with p50/p90/avg/total summary rows appended at the bottom. The output is designed to be copy-pasted into a spreadsheet.

---

## Problems Identified

### 1. Month rows are in random order (Bug)

`dateSpend` is a `HashMap<LocalDate, Map<Category, Float>>` (line 57). The iteration at line 86 uses `dateSpend.entrySet()`, which has no guaranteed ordering. In practice, months appear shuffled — the output might show March before January.

**Fix:** Use a `LinkedHashMap` merge function in the `Collectors.toMap()` at line 72. Since the `months` list is already built in chronological order (lines 49-54), insertion order is all that needs to be preserved.

> **Note:** This is a prerequisite for the trailing average work in Phase 1b. Trailing averages require knowing which months are most recent, which requires the months to be in chronological order. Do not implement trailing averages until this fix is in place.

### 2. Off-by-one in month loop (Bug)

The loop condition at line 54 is `while (startMonth.isBefore(endDate))`, where `startMonth` advances to the first day of the next month after each iteration. Because `isBefore` is strict, if `endDate` is the first day of a month (e.g. `2022-12-01`), the December bucket is never added even though the user explicitly asked for data up to December 1st.

**Fix:** Change the condition to `!startMonth.isAfter(endDate.withDayOfMonth(1))` so that any month whose first day falls within the requested range is included.

### 3. Missing input validation in `reportTransactionsMonthly` (Bug)

`reportTransactionsInCategory` validates that `startDate` is non-null and that `startDate` precedes `endDate`, throwing clear `IllegalArgumentException`s. `reportTransactionsMonthly` has no equivalent guards — a null `startDate` causes an NPE at line 49, and an inverted date range produces a silently empty report.

**Fix:** Add the same guard clauses to `reportTransactionsMonthly` for consistency.

### 4. `selectGroupByCategory` silently swallows exceptions (Bug)

`CategorizedTransactionDao.selectGroupByCategory()` catches `SQLException` and returns an empty `HashMap` (line 133). If the database query fails mid-report, that month silently shows $0.00 for every category. The user has no idea data is missing.

**Fix:** Let the exception propagate and have the CLI display a clear error message rather than silently returning incomplete data.

### 5. `reportTransactionsInCategory` stats are not formatted (Minor Bug)

Lines 177-180 output raw float values for the per-category stats:
```java
lines.add(CSV_DELIMITER + "p50" + CSV_DELIMITER + stats.p50);
```
This produces output like `, p50, 42.599998` instead of `, p50, 42.60`, and will produce scientific notation (e.g. `4.2E-4`) for very small values. The monthly report uses `CURRENCY_FORMATTER` correctly but this method doesn't.

### 6. Month format uses full month name — wastes horizontal space

`DateTimeFormatter.ofPattern("MMMM yyyy")` produces "September 2022" (14 chars). The README example shows "Nov-21" format. For a wide table with many categories, shorter month labels keep things readable.

**Fix:** Use `MMM-yy` format (e.g., "Jan-22", "Sep-22"). Matches the existing README example and is unambiguous.

### 7. Category columns include UNKNOWN and TRANSFER even when empty

Every report includes the system-default categories (UNKNOWN, TRANSFER) as columns, even when they have no transactions in the date range. This adds clutter. Most users care about their spending categories, not the bookkeeping ones.

**Fix:** Filter `sortedCategories` at line 75 to only include categories that have at least one transaction in the queried date range. This is a single `filter()` call and produces cleaner output for all subsequent work.

### 8. Summary statistics (p50/p90/avg) aren't useful

The current stats have several issues:

- **They use all available months equally.** A p50 over 12 months of data gives equal weight to January and December. For tracking family expenses, you care about recent trends — what you're spending *now*, not what you spent 8 months ago.
- **Percentiles on small samples are misleading.** With 6 months of data, p90 is just "the second-highest month." With 4 months, p50 and p90 collapse to the same values. The label "p90" implies statistical rigor that doesn't exist with N<20.
- **The reverse-sort hack for negative amounts is confusing.** The `computeStats` method reverses sort order when all amounts are negative (lines 191-200) so that p90 represents "the largest outlier expense" rather than "the smallest expense." This is clever but non-obvious.
- **avg and total are fine.** These are straightforward and useful.

**Recommendation:** Replace p50/p90 with a **trailing average** that captures recent trend:

- **Trailing 3-month average**: Average of the most recent 3 months relative to the report's end date. Shows what you're spending right now. Responsive to changes.
- **Trailing 6-month average**: Average of the most recent 6 months relative to the report's end date. Smooths out one-off spikes. Good for budgeting.
- **Overall average and total**: Keep as-is.

Why trailing averages instead of percentiles:
- Intuitive: "your average grocery spend over the last 3 months was $850" is immediately actionable
- Recency-weighted: old months don't dilute the signal
- Robust: a single outlier month (holiday shopping, emergency vet bill) gets averaged out in the 6-month window but shows up in the 3-month window, so you can see both
- Honest: doesn't pretend to be statistical analysis on a sample of 6

**Semantics for zero-spend months:** Months where a category had no transactions count as $0.00 in the trailing average — they are included in the denominator. This is intentional: if you didn't spend anything on restaurants in February, that should lower your trailing average. The implementation must pad the amounts list with zeros for months where a category had no data, rather than excluding those months.

**Trailing averages are computed relative to the report's end date**, not relative to today. A report run for 2022-01-01 to 2022-06-30 will show trailing averages using Apr/May/Jun 2022 as the most recent months, not months relative to the current date.

**Behaviour when fewer months than window size are available:** Suppress the row entirely. The trailing 3-month average row is only emitted if the report spans at least 3 months; the trailing 6-month average row is only emitted if the report spans at least 6 months. A report with 4 months of data will show a t3m row but no t6m row. This avoids the need for any fallback label and keeps the output unambiguous — a missing row is clearer than a partial average that looks like a full one.

**Cascading change:** The private `Stats` record (`record Stats(float p50, float p90, float total, float avg)`) will need its fields renamed (e.g. `trailing3m`, `trailing6m`, `total`, `avg`). The `generateStatsString` helper and all test assertions that reference `stats.p50` / `stats.p90` will need updating. This is a broader refactor than it appears.

### 9. Output only goes to terminal — can't easily import into Excel

The report is printed via `cli.println(lines)` to the TextIO terminal. To get data into a spreadsheet, you'd need to select all the terminal text, copy it, paste it into a file, and then import it. This is fragile (trailing whitespace, encoding issues, missed lines).

**Recommendation:** Add `--format` and `--output-file` flags to `get transactions` (see Phase 2). The terminal CSV output continues to work as the default, keeping backward compatibility.

**Library decision: fastexcel** (`org.dhatim:fastexcel`). An in-depth comparison of fastexcel vs Apache POI was conducted. fastexcel wins on every axis that matters for this use case:
- **Dependency footprint:** fastexcel adds ~400 KB (one transitive dependency: `opczip`). Apache POI adds ~23 MB (poi, poi-ooxml, poi-ooxml-lite, xmlbeans, commons-compress, and others). The fat JAR stays ~28 MB with fastexcel vs ~50 MB with POI.
- **Formatting:** fastexcel supports everything we need — bold headers (`ws.style(r, c).bold().set()`), frozen rows (`ws.freezePane(0, 1)`), Excel number format strings (`ws.style(r, c).format("#,##0.00").set()`), and manual column widths (`ws.width(col, value)`). Column auto-sizing has a known bug ([Issue #35](https://github.com/dhatim/fastexcel/issues/35)) where it doesn't account for non-default font sizes; since we use only default font sizes, this is unlikely to affect us, and manual widths are an acceptable fallback.
- **API:** fastexcel is a streaming write-only library with a fluent API — a natural fit for a report generator that never needs to modify previously written rows.
- **Apache POI's extra capabilities** (charts, borders, conditional formatting) are not needed for this use case.

---

## Implementation Plan

### Phase 1a — Pure bug fixes (no user-visible behavior change)

Changes to `ReportingService.java`, `CategorizedTransactionDao.java`, and their tests. No new dependencies. Each item is an independent fix and can be committed separately.

1. **Fix off-by-one in month loop** — change `isBefore(endDate)` to `!isAfter(endDate.withDayOfMonth(1))`
2. **Fix month ordering** — use `LinkedHashMap` merge function in `Collectors.toMap()` at line 72. **Must land before Phase 1b.**
3. **Fix date format** — change `MMMM yyyy` to `MMM-yy`
4. **Fix unformatted stats in `reportTransactionsInCategory`** — wrap with `CURRENCY_FORMATTER`
5. **Fix missing input validation in `reportTransactionsMonthly`** — add null/range guards matching `reportTransactionsInCategory`
6. **Fix DAO exception swallowing** — propagate `SQLException` from `selectGroupByCategory` with a clear error message at the CLI layer
7. **Filter empty categories from columns** — exclude categories with no transactions in the date range from `sortedCategories`
8. **Update tests** — `ReportingServiceTest` and `ReportingWorkflowIntegrationTest` assertions for date format, column filtering, and validation behaviour

### Phase 1b — Stats replacement (depends on Phase 1a item 2)

Changes to `ReportingService.java` and tests only. No new dependencies.

1. **Implement trailing average computation**
   - "Trailing N months" means the N most recent months within the report's date range
   - Zero-spend months count as $0.00 (pad amounts list with zeros for months where a category had no data)
   - Suppress the trailing average row entirely when the report spans fewer months than the window size — no fallback label, no partial average
   - t3m row only appears when the report has ≥ 3 months; t6m row only appears when the report has ≥ 6 months
2. **Replace p50/p90 with trailing 3-month avg and trailing 6-month avg**
3. **Rename `Stats` record fields** from `p50`/`p90` to `trailing3m`/`trailing6m`
4. **Update `generateStatsString` helper** and all call sites
5. **Update tests** — all assertions referencing p50/p90 values need rewriting; add dedicated tests for trailing average edge cases (exactly 3 months, exactly 6 months, fewer than 3 months, zero-spend months, mixed positive/negative categories)

### Phase 2 — File output (pending library investigation)

> **Before starting:** Complete an in-depth comparison of Apache POI vs. fastexcel across: fat JAR size impact, write-only API ergonomics, formatting capabilities (bold headers, frozen rows, column auto-sizing, native number types), and maintenance status. Document the decision and rationale before writing any implementation code.

1. **Add `org.dhatim:fastexcel` dependency** to `build.gradle`
2. **Implement `writeReportToFile` method** in `ReportingService`
   - Creates an XLSX workbook with one sheet
   - Header row: bold, frozen
   - Month rows: date-formatted first column, currency-formatted data columns
   - Summary rows: trailing 3-month avg, trailing 6-month avg, overall avg, total
   - Auto-sizes columns
   - Creates `~/.ofxcat/reports/` directory if it doesn't exist
3. **Add `--format` flag** to `get transactions` — accepted values: `terminal` (default), `xlsx`
   - `terminal`: existing behaviour, backward compatible
   - `xlsx`: writes file and prints path to terminal
4. **Add `--output-file` flag** to `get transactions`
   - Optional; only meaningful when `--format xlsx` is specified
   - Default: `~/.ofxcat/reports/transactions-<start>-to-<end>.xlsx`
   - Overrides the default path when provided
   - Overwrites an existing file at the same path (no timestamp suffix by default)
5. **Update `OfxCatOptions` record** to include `format` and `outputFile` fields
6. **Update tests** — unit tests for XLSX generation, CLI flag parsing; update README example output
7. **Update `README.md` and `docs/CodebaseOverview.md`** with new flags and example usage

### Phase 3 — Polish

#### 3a — Documentation (deferred from Phase 2)

Update `README.md` and `docs/CodebaseOverview.md` to reflect all changes made in Phases 1 and 2:

1. **Update example output in `README.md`** — the example currently shows `p50`/`p90` summary rows, which no longer exist. Replace with `t3m`, `t6m`, `avg`, `total` rows matching the current output format.
2. **Document new CLI flags in `README.md`** — add `--format terminal|xlsx` and `--output-file <path>` to the `get transactions` section, with descriptions and example invocations.
3. **Update `docs/CodebaseOverview.md`** — the `get transactions` entry still describes p50/p90 stats and omits the new flags. Bring it in sync with the current behaviour.

#### 3b — Monthly TOTAL column

Add a `TOTAL` column as the rightmost column in the monthly report (both terminal and XLSX paths). The `TOTAL` value for each month row is the sum of all category columns for that month. The trailing stats rows (`t3m`, `t6m`, `avg`, `total`) carry the same aggregation through the `TOTAL` column.

**Scope:**
1. Compute the per-month total in `collectMonthlyReportData` (or at render time — either is acceptable).
2. Append the `TOTAL` column header and per-month values in `reportTransactionsMonthly` (terminal) and `reportTransactionsMonthlyToFile` (XLSX).
3. Append the `TOTAL` stats values in `generateStatsString` (terminal) and `writeXlsxStatsRow` (XLSX).
4. Update tests — all existing assertions that check exact line content or cell values will need a `TOTAL` column appended; add dedicated tests for the `TOTAL` column values.

**Column ordering note:** Category columns remain sorted alphabetically. `TOTAL` is always the last column, after all category columns, regardless of its value.

---

## Files to Modify

| File | Phase | Changes |
|------|-------|---------|
| `ReportingService.java` | 1a, 1b, 2, 3b | Fix bugs, replace stats, add file output, add TOTAL column |
| `CategorizedTransactionDao.java` | 1a | Propagate exceptions instead of swallowing |
| `ReportingServiceTest.java` | 1a, 1b, 2, 3b | Update assertions for all changes |
| `ReportingWorkflowIntegrationTest.java` | 1a | Update assertions for date format, column filtering |
| `build.gradle` | 2 | Add XLSX library dependency |
| `OfxCat.java` | 2 | Add `--format` and `--output-file` CLI options |
| `README.md` | 3a | Update example output and document new CLI flags |
| `docs/CodebaseOverview.md` | 3a | Document new reporting features and CLI options |

---

## Decisions Made

| Question | Decision |
|----------|----------|
| Zero-spend months in trailing averages | Count as $0.00; included in the denominator |
| "Trailing" relative to what? | The report's end date, not today's date |
| Trailing average window sizes | 3-month and 6-month |
| Insufficient data for trailing average | Suppress the row entirely — no fallback label, no partial average |
| CLI output flag | `--format terminal\|xlsx` (default: `terminal`) |
| Output path control | `--output-file <path>` with default `~/.ofxcat/reports/transactions-<start>-to-<end>.xlsx` |
| Overwrite vs. timestamp | Overwrite by default |
| XLSX library | fastexcel (`org.dhatim:fastexcel`) — small footprint, streaming write-only API, covers all required formatting |
| Category filtering | Phase 1a (not deferred) |
| Off-by-one month loop | Phase 1a |
| Category column order | Alphabetical always; deterministic regardless of spend amounts |
| TOTAL column position | Always last, after all category columns |

---

## Open Questions

None — all decisions have been made. Implementation may proceed.
