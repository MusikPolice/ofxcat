# Transaction Matching Refactor Plan

**Created:** January 17, 2026
**Status:** Draft - Awaiting Review

---

## Executive Summary

This document proposes a refactoring of the transaction categorization system to use Elasticsearch-style tokenization and normalization instead of the current fuzzy string matching approach. The goal is more durable, predictable matching that handles real-world transaction description variations like franchise store numbers, location suffixes, and truncated merchant names.

Additionally, this plan introduces a keyword rules configuration file that allows users to define automatic categorization rules for known merchants, pre-populated with common chains.

---

## Problem Statement

### Current Behaviour

The existing `TransactionCategoryService` uses a three-tier matching strategy:

1. **Exact match**: Searches for transactions with identical descriptions
2. **Partial match**: Tokenizes by splitting on spaces, filters out numeric tokens and phone numbers, then uses FuzzyWuzzy library to score matches with a 60% threshold
3. **Manual selection**: User chooses from all categories or creates a new one

### Observed Problems

Based on analysis of real OFX transaction data, the current approach struggles with:

**1. Franchise/Chain Store Numbers**
- `WAL-MART #1155` and `WAL-MART #3045` should match but may not score highly in fuzzy matching
- `STARBUCKS #4756` vs `STARBUCKS 800-782-7282` - the phone number variant won't match well
- `LCBO/RAO #0417` vs `LCBO/RAO #7777` - store numbers differ

**2. Location Suffixes**
- `SEPHORA - KITCH` (Kitchener) vs `SEPHORA - TORONTO` - location doesn't indicate category
- `ZEHRS MARKET ST` vs `ZEHRS MARKET STANLEY P` - partial address information
- `EUROPRO (KITCHE` - truncated location in parentheses

**3. Truncated Merchant Names**
- `SHOPPERS DRUG M` should match `SHOPPERS DRUG MART`
- `REN'S PETS DEPO` should match `REN'S PETS DEPOT`
- `PINO'S SALON AN` - truncated at character limit

**4. Online/Credit Card Transaction Variants**
- `Amazon.ca*T23YP3F33` vs `Amazon.ca*X56OF5GV3` - random order IDs appended
- `PAYPAL *FACERECORDS` vs `PAYPAL *UNIVERSALMU` - PayPal with different merchants
- `SP * ONCE UPON A CHILD` vs `SP * VILLAGE CRAFT` - Square/Stripe prefix patterns

**5. Punctuation and Case Variations**
- `MCDONALD'S #290` - apostrophes
- `A&W #4330` - ampersands (stored as `&amp;` in XML)
- `BED BATH & BEYOND` - various punctuation

**6. Fuzzy Matching is Unpredictable**
- The 60% threshold is arbitrary and not configurable
- FuzzyWuzzy scores can be counterintuitive for short strings
- Results depend heavily on what's already in the database

---

## Proposed Solution

### Relationship to Existing TransactionCleaner System

The codebase already has a `ca.jonathanfritz.ofxcat.cleaner` package that normalizes raw OFX data at import time. It's important to understand how the proposed `TokenNormalizer` relates to this existing system.

**TransactionCleaner (existing)** and **TokenNormalizer (proposed)** serve different purposes at different stages:

| Aspect | TransactionCleaner | TokenNormalizer |
|--------|-------------------|-----------------|
| **When** | Import time (OFX parsing) | Matching time (categorization) |
| **Input** | Raw `OfxTransaction` (name, memo, type, amount) | Cleaned description string |
| **Output** | `Transaction` with human-readable description | `Set<String>` of tokens for comparison |
| **Context** | Full transaction (can use type, amount, regex) | Description string only |
| **Bank-specific** | Yes (factory selects by bank ID) | No (generic rules) |
| **Purpose** | *"What did I actually buy?"* | *"Is this the same merchant I've seen before?"* |

**Data Flow:**

```
OFX File
    ↓
[TransactionCleaner] ← Bank-specific rules (e.g., RBC strips "WWW PAYMENT - 123")
    ↓
Cleaned Description (human-readable, stored in DB)
    ↓
[TokenNormalizer] ← Generic rules (store numbers, phone numbers, etc.)
    ↓
Token Set (for matching only, stored in TransactionToken table)
```

**Division of Responsibilities:**

| Pattern Type | Handled By | Examples |
|--------------|-----------|----------|
| Bank-specific prefixes | TransactionCleaner | `WWW PAYMENT - 123`, `WWW TRF DDA - 456`, `C-IDP PURCHASE-` |
| Transaction type detection | TransactionCleaner | Detecting transfers, ATM withdrawals |
| Generic store numbers | TokenNormalizer | `#1234`, `#4756` |
| Phone numbers | TokenNormalizer | `800-782-7282` |
| Payment processor prefixes | TokenNormalizer | `SP *`, `PAYPAL *`, `SQ *` |
| Case/punctuation | TokenNormalizer | `MCDONALD'S` → `mcdonalds` |

**Contract:** TransactionCleaners produce a "cleaned description suitable for display and tokenization." TokenNormalizer assumes the description is already bank-normalized and focuses on generic noise removal for matching purposes.

**Implication for Implementation:** If a bank-specific pattern is discovered during TokenNormalizer development (e.g., a prefix that only appears for one bank), it should be added to the appropriate `TransactionCleaner` implementation rather than the TokenNormalizer.

### Core Concept: Token Normalization

Replace fuzzy string matching with deterministic token-based matching inspired by Elasticsearch's text analysis pipeline:

1. **Tokenize**: Split description into words
2. **Normalize**: Apply a series of transformations to each token
3. **Filter**: Remove noise tokens (store numbers, phone numbers, short tokens, stop words)
4. **Match**: Compare normalized token sets

A transaction matches if its normalized tokens are a **superset** of the tokens from a previously categorized transaction (or if there's significant overlap above a configurable threshold).

### Normalization Pipeline

Each token passes through these transformations in order. Note: Bank-specific patterns (like `C-IDP PURCHASE-`) should be handled by `TransactionCleaner` implementations, not here.

1. **Lowercase**: `STARBUCKS` → `starbucks`
2. **Remove punctuation**: `MCDONALD'S` → `mcdonalds`, `A&W` → `aw`, `PAYPAL *` → `paypal`
3. **Decode XML entities**: `&amp;` → `&` (then remove)
4. **Remove numeric suffixes**: `#1234` → removed, `800-782-7282` → removed

**Note:** Payment processor names (PayPal, Square, Shopify, etc.) are retained as tokens since they provide useful context about transaction type. The `*` separator is removed as punctuation.

**Note:** Location suffixes (city names like `KITCH`, `TORONTO`) are intentionally kept. The token matching algorithm handles these naturally—partial overlap still matches. Maintaining a list of city abbreviations would add complexity, and many "abbreviations" are actually truncated data.

### Keyword Rules Configuration

A YAML configuration file (`~/.ofxcat/keyword-rules.yaml`) allows users to define automatic categorization rules:

```yaml
# Keywords that trigger automatic categorization
# Matching is performed on normalized tokens
rules:
  - keywords: [starbucks]
    category: RESTAURANTS

  - keywords: [walmart, wal-mart]
    category: GROCERIES

  - keywords: [mcdonalds, mcdonald]
    category: RESTAURANTS

  - keywords: [netflix]
    category: ENTERTAINMENT

  - keywords: [amazon]
    category: SHOPPING
    # Note: Can be overridden by user during import
```

The file ships with sensible defaults for common international and Canadian chains.

---

## Detailed Design

### Phase 1: Token Normalizer

**Goal**: Create a reusable `TokenNormalizer` class that converts transaction descriptions into normalized token sets.

**New Classes**:
- `ca.jonathanfritz.ofxcat.matching.TokenNormalizer`
- `ca.jonathanfritz.ofxcat.matching.NormalizationConfig`

**TokenNormalizer Responsibilities**:
- Accept a cleaned transaction description string (already processed by `TransactionCleaner`)
- Return a `Set<String>` of normalized tokens
- Be configurable via `NormalizationConfig`
- Handle only generic noise patterns (not bank-specific prefixes)

**Normalization Steps** (in order):

| Step | Input | Output | Rationale |
|------|-------|--------|-----------|
| 1. Decode XML | `A&amp;W` | `A&W` | OFX files encode special chars |
| 2. Lowercase | `STARBUCKS` | `starbucks` | Case-insensitive matching |
| 3. Split tokens | `starbucks #4756` | `[starbucks, #4756]` | Separate words |
| 4. Remove punctuation | `mcdonald's`, `*` | `mcdonalds`, removed | Apostrophes, asterisks vary |
| 5. Filter numerics | `#4756`, `800-782-7282` | removed | Store numbers, phones |
| 6. Filter short tokens | `a`, `&` | removed | Single chars are noise |
| 7. Filter stop words | `the`, `of`, `and` | removed | Common words don't help |

**Notes:**
- Payment processor names (PayPal, Square/SP, Shopify, etc.) are retained—they provide useful context about transaction type
- Location suffixes (city names) are intentionally kept—token matching handles partial overlap naturally

**Expected Behaviour**:

| Input Description | Normalized Tokens |
|-------------------|-------------------|
| `STARBUCKS #4756` | `{starbucks}` |
| `STARBUCKS 800-782-7282` | `{starbucks}` |
| `WAL-MART #1155` | `{walmart}` |
| `WAL-MART #3045` | `{walmart}` |
| `MCDONALD'S #290` | `{mcdonalds}` |
| `SP * ONCE UPON A CHILD` | `{sp, once, upon, child}` |
| `PAYPAL *FACERECORDS` | `{paypal, facerecords}` |
| `Amazon.ca*T23YP3F33` | `{amazon, ca}` |
| `SHOPPERS DRUG M` | `{shoppers, drug}` |
| `SHOPPERS DRUG MART` | `{shoppers, drug, mart}` |
| `ZEHRS MARKET ST` | `{zehrs, market, st}` |
| `ZEHRS MARKET STANLEY P` | `{zehrs, market, stanley}` |

**Tests for Phase 1**:
- `tokenNormalizer_lowercasesAllTokens`
- `tokenNormalizer_removesPunctuation`
- `tokenNormalizer_removesStoreNumbers`
- `tokenNormalizer_removesPhoneNumbers`
- `tokenNormalizer_removesShortTokens`
- `tokenNormalizer_removesStopWords`
- `tokenNormalizer_retainsPaymentProcessorNames`
- `tokenNormalizer_decodesXmlEntities`
- `tokenNormalizer_handlesEmptyString`
- `tokenNormalizer_handlesNullInput`
- `tokenNormalizer_starbucksVariantsNormalizeIdentically`
- `tokenNormalizer_walmartVariantsNormalizeIdentically`
- `tokenNormalizer_mcdonaldsVariantsNormalizeIdentically`
- `tokenNormalizer_amazonVariantsNormalizeIdentically`

### Phase 2: Token Storage and SQL-Based Matching

**Goal**: Store normalized tokens in the database and use efficient SQL queries to find matching transactions, avoiding O(n) table scans with repeated normalization.

**New Classes**:
- `ca.jonathanfritz.ofxcat.matching.TokenMatchingService`
- `ca.jonathanfritz.ofxcat.datastore.TransactionTokenDao`

**Database Schema**:

A new table stores the normalized tokens for each categorized transaction:

```sql
-- Flyway migration: V12__Add_Transaction_Tokens.sql
CREATE TABLE TransactionToken (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL REFERENCES CategorizedTransaction(id) ON DELETE CASCADE,
    token TEXT NOT NULL
);

-- Index for efficient token lookups
CREATE INDEX idx_transaction_token_token ON TransactionToken(token);

-- Index for efficient deletion when transaction is removed
CREATE INDEX idx_transaction_token_transaction_id ON TransactionToken(transaction_id);
```

**TransactionTokenDao Responsibilities**:
- `insertTokens(DatabaseTransaction t, long transactionId, Set<String> tokens)` - Store tokens for a new transaction
- `deleteTokens(DatabaseTransaction t, long transactionId)` - Remove tokens when transaction is deleted
- `findTransactionsWithMatchingTokens(DatabaseTransaction t, Set<String> tokens)` - SQL-based lookup

All methods accept a `DatabaseTransaction` parameter to participate in the caller's transaction scope. This follows the existing DAO pattern in the codebase (see `DescriptionCategoryDao`, `CategorizedTransactionDao`, etc.).

**Matching Algorithm**:

```
function findMatchingCategories(newTransaction):
    newTokens = normalize(newTransaction.description)

    if newTokens is empty:
        return empty (prompt user)

    // Step 1: SQL query to find transactions sharing at least one token
    // Returns: transaction_id, category_id, matching_token_count
    candidateRows = SELECT
        ct.id as transaction_id,
        ct.category_id,
        COUNT(DISTINCT tt.token) as matching_tokens
    FROM TransactionToken tt
    JOIN CategorizedTransaction ct ON tt.transaction_id = ct.id
    WHERE tt.token IN (:newTokens)
      AND ct.category_id != :unknownCategoryId
    GROUP BY ct.id, ct.category_id

    // Step 2: For each candidate, get total token count and compute overlap
    candidates = []
    for each row in candidateRows:
        totalTokens = SELECT COUNT(*) FROM TransactionToken
                      WHERE transaction_id = row.transaction_id

        overlapRatio = row.matching_tokens / min(|newTokens|, totalTokens)

        if overlapRatio >= threshold (default 0.8):
            candidates.add(row.category_id, overlapRatio)

    // Step 3: Group by category, rank by best match score
    return candidates.groupByCategory().sortByScore()
```

**Optimized Single-Query Variant**:

For better performance, the two-step lookup can be combined:

```sql
WITH TokenCounts AS (
    SELECT transaction_id, COUNT(*) as total_tokens
    FROM TransactionToken
    GROUP BY transaction_id
)
SELECT
    ct.id as transaction_id,
    ct.category_id,
    COUNT(DISTINCT tt.token) as matching_tokens,
    tc.total_tokens
FROM TransactionToken tt
JOIN CategorizedTransaction ct ON tt.transaction_id = ct.id
JOIN TokenCounts tc ON tc.transaction_id = ct.id
WHERE tt.token IN (?, ?, ?)  -- newTokens
  AND ct.category_id != ?    -- exclude UNKNOWN
GROUP BY ct.id, ct.category_id, tc.total_tokens
HAVING CAST(COUNT(DISTINCT tt.token) AS REAL) /
       MIN(?, tc.total_tokens) >= ?  -- threshold check
ORDER BY CAST(COUNT(DISTINCT tt.token) AS REAL) /
         MIN(?, tc.total_tokens) DESC
```

**Token Insertion on Import**:

When a transaction is categorized and saved, tokens are also stored. Both operations must be wrapped in a single `DatabaseTransaction` to ensure atomicity—if either operation fails, both are rolled back.

**Atomicity Requirement:** Any code that modifies two or more database records as part of a single logical operation must use a `DatabaseTransaction`. For OFX imports, there should be one `DatabaseTransaction` per OFX transaction being imported.

```java
// In TransactionCategoryService - accepts DatabaseTransaction from caller
public CategorizedTransaction saveWithCategory(DatabaseTransaction t, Transaction txn, Category category) throws SQLException {
    // 1. Save the categorized transaction (within caller's transaction)
    CategorizedTransaction saved = categorizedTransactionDao.insert(t, txn, category);

    // 2. Normalize and store tokens (same transaction - atomic with step 1)
    Set<String> tokens = tokenNormalizer.normalize(txn.getDescription());
    transactionTokenDao.insertTokens(t, saved.getId(), tokens);

    return saved;
}

// Convenience overload that creates its own transaction
public CategorizedTransaction saveWithCategory(Transaction txn, Category category) {
    try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
        return saveWithCategory(t, txn, category);
    } catch (SQLException ex) {
        logger.error("Failed to save categorized transaction with tokens", ex);
        return null;
    }
}
```

This pattern matches the existing `put(DatabaseTransaction t, ...)` / `put(...)` overload pattern used throughout the codebase.

**Note:** Migration of existing data is handled in Phase 4, after keyword rules are available. This allows the migration to also apply keyword rules to fix potentially miscategorized historical transactions.

**Key Differences from Current Approach**:
1. **Deterministic**: Same input always produces same token set
2. **Subset matching**: `{shoppers, drug}` matches `{shoppers, drug, mart}`
3. **No fuzzy scoring**: Either tokens match or they don't
4. **Configurable threshold**: Overlap ratio is explicit, not a fuzzy score
5. **Efficient lookups**: SQL index on tokens avoids O(n) table scan
6. **One-time normalization**: Tokens computed once at import, not on every match

**Threshold Behaviour**:
- 1.0 = All tokens must match (strict)
- 0.8 = 80% of tokens must match (default)
- 0.5 = Half of tokens must match (loose)

**Tests for Phase 2**:
- `transactionTokenDao_insertAndRetrieveTokens`
- `transactionTokenDao_deleteTokensCascadesWithTransaction`
- `transactionTokenDao_findTransactionsWithMatchingTokens`
- `transactionTokenDao_hasTokensReturnsTrueWhenTokensExist`
- `transactionTokenDao_hasTokensReturnsFalseWhenNoTokens`
- `tokenMatchingService_exactTokenMatchReturnsCategory`
- `tokenMatchingService_subsetTokenMatchReturnsCategory`
- `tokenMatchingService_noMatchReturnsEmpty`
- `tokenMatchingService_multipleMatchesRankedByOverlap`
- `tokenMatchingService_respectsThresholdConfiguration`
- `tokenMatchingService_emptyTokensReturnsEmpty`
- `tokenMatchingService_ignoresUnknownCategory`
- `tokenMatchingService_starbucksLocationsMatchSameCategory`
- `tokenMatchingService_walmartLocationsMatchSameCategory`
- `saveWithCategory_insertsTransactionAndTokensAtomically`
- `saveWithCategory_rollsBackBothOnTokenInsertFailure`

### Phase 3: Keyword Rules System

**Goal**: Allow automatic categorization based on keyword patterns, with a pre-populated configuration file.

#### Keyword Rules Acquisition Process

Before implementing the keyword rules system, we need to build a comprehensive list of retailers and their categories. This is a research task that should be completed before coding begins.

**Data Sources to Investigate:**

1. **Wikipedia Lists**
   - [List of supermarket chains in Canada](https://en.wikipedia.org/wiki/List_of_supermarket_chains_in_Canada)
   - [List of restaurants in Canada](https://en.wikipedia.org/wiki/List_of_restaurant_chains_in_Canada)
   - [List of Canadian retail chains](https://en.wikipedia.org/wiki/Category:Retail_companies_of_Canada)
   - Similar lists for US retailers (for broader applicability)

2. **Open Data Sources**
   - Statistics Canada business registries
   - OpenStreetMap retailer data (via Overpass API)
   - Wikidata queries for retail companies by category

3. **Industry Reports**
   - Retail Council of Canada member lists
   - Restaurant Brands International portfolio
   - Loblaw Companies Ltd. banner list
   - Empire Company (Sobeys) banner list

4. **Crowdsourced/Community Data**
   - Review existing open-source personal finance tools for their merchant lists
   - Consider allowing users to contribute rules back (future enhancement)

**Acquisition Tasks:**

| Task | Output | Priority |
|------|--------|----------|
| Scrape/compile Canadian grocery chains | YAML rules for GROCERIES | High |
| Scrape/compile Canadian restaurant chains | YAML rules for RESTAURANTS | High |
| Scrape/compile major gas station brands | YAML rules for VEHICLES | High |
| Compile pharmacy chains (Shoppers, Rexall, etc.) | YAML rules for PHARMACY | Medium |
| Compile streaming services (Netflix, Spotify, etc.) | YAML rules for ENTERTAINMENT | Medium |
| Compile major e-commerce (Amazon, eBay, etc.) | YAML rules for SHOPPING | Medium |
| Compile telecom providers (Rogers, Bell, Telus) | YAML rules for UTILITIES | Low |
| Compile insurance companies | YAML rules for INSURANCE | Low |

**Acceptance Criteria for Default Rules File:**
- Minimum 50 distinct retailers covered
- All major Canadian grocery chains (Loblaws banners, Sobeys banners, Metro, Walmart, Costco)
- All major Canadian banks (for transfer detection enhancement, future)
- Top 20 restaurant chains by Canadian locations
- Common subscription services (streaming, software)
- Rules should use normalized keywords (lowercase, no punctuation)

**Output:** A well-commented `keyword-rules.yaml` file that ships with the application as a sensible default.

**New Classes**:
- `ca.jonathanfritz.ofxcat.matching.KeywordRule`
- `ca.jonathanfritz.ofxcat.matching.KeywordRulesConfig`
- `ca.jonathanfritz.ofxcat.matching.KeywordRulesLoader`

**Configuration File Location**: `~/.ofxcat/keyword-rules.yaml`

**Configuration Schema**:

```yaml
# Version for future schema migrations
version: 1

# Global settings
settings:
  # If true, keyword matches are applied before token matching
  # If false, keyword matches are suggestions only
  auto_categorize: true

# Keyword rules - processed in order, first match wins
rules:
  # Restaurant chains
  - keywords: [starbucks]
    category: RESTAURANTS

  - keywords: [mcdonalds, mcdonald]
    category: RESTAURANTS

  - keywords: [tim, hortons]
    match_all: true  # Both keywords must be present
    category: RESTAURANTS

  - keywords: [subway]
    category: RESTAURANTS

  - keywords: [wendys, wendy]
    category: RESTAURANTS

  - keywords: [burger, king]
    match_all: true
    category: RESTAURANTS

  - keywords: [swiss, chalet]
    match_all: true
    category: RESTAURANTS

  - keywords: [chipotle]
    category: RESTAURANTS

  - keywords: [pizza]
    category: RESTAURANTS

  # Grocery stores
  - keywords: [walmart, wal-mart]
    category: GROCERIES

  - keywords: [costco]
    category: GROCERIES

  - keywords: [loblaws, loblaw]
    category: GROCERIES

  - keywords: [zehrs]
    category: GROCERIES

  - keywords: [no, frills]
    match_all: true
    category: GROCERIES

  - keywords: [real, canadian, superstore]
    match_all: true
    category: GROCERIES

  - keywords: [farm, boy]
    match_all: true
    category: GROCERIES

  - keywords: [sobeys]
    category: GROCERIES

  - keywords: [safeway]
    category: GROCERIES

  - keywords: [whole, foods]
    match_all: true
    category: GROCERIES

  - keywords: [metro]
    category: GROCERIES

  # Retail
  - keywords: [amazon]
    category: SHOPPING

  - keywords: [canadian, tire]
    match_all: true
    category: SHOPPING

  - keywords: [home, depot]
    match_all: true
    category: SHOPPING

  - keywords: [dollarama]
    category: SHOPPING

  - keywords: [winners]
    category: SHOPPING

  - keywords: [marshalls, homesense]
    category: SHOPPING

  # Pharmacies
  - keywords: [shoppers, drug]
    match_all: true
    category: PHARMACY

  - keywords: [rexall]
    category: PHARMACY

  # Entertainment
  - keywords: [netflix]
    category: ENTERTAINMENT

  - keywords: [spotify]
    category: ENTERTAINMENT

  - keywords: [apple, bill]
    match_all: true
    category: ENTERTAINMENT

  - keywords: [steam, purchase]
    match_all: true
    category: ENTERTAINMENT

  # Gas stations
  - keywords: [shell]
    category: VEHICLES

  - keywords: [esso]
    category: VEHICLES

  - keywords: [petro, canada]
    match_all: true
    category: VEHICLES

  # Alcohol (Canada)
  - keywords: [lcbo]
    category: ALCOHOL

  - keywords: [beer, store]
    match_all: true
    category: ALCOHOL
```

**Matching Logic**:
1. Normalize the transaction description to tokens
2. For each rule in order:
   - If `match_all: true`: All keywords must be present in tokens
   - If `match_all: false` (default): Any keyword present triggers match
3. First matching rule wins
4. If no rule matches, fall through to token-based matching

**User Customization**:
- Users can edit the file to add their own rules
- Users can delete rules they don't want
- Rules are processed in order, so user rules at the top take precedence
- Invalid YAML is logged and ignored (defaults to empty rules)

**Tests for Phase 3**:
- `keywordRulesLoader_loadsValidYamlFile`
- `keywordRulesLoader_handlesEmptyFile`
- `keywordRulesLoader_handlesMissingFile`
- `keywordRulesLoader_handlesInvalidYaml`
- `keywordRule_matchesAnyKeywordByDefault`
- `keywordRule_matchesAllKeywordsWhenRequired`
- `keywordRule_caseInsensitiveMatching`
- `keywordRule_matchesNormalizedTokens`
- `keywordRulesConfig_firstMatchWins`
- `keywordRulesConfig_noMatchReturnsEmpty`
- `keywordRulesConfig_starbucksMatchesRestaurants`
- `keywordRulesConfig_walmartMatchesGroceries`
- `keywordRulesConfig_timHortonsRequiresBothKeywords`

### Phase 4: Migration and Integration

**Goal**: Migrate existing transactions (with keyword rule sweep to fix miscategorizations) and integrate the new matching system into the existing categorization flow.

**New Classes**:
- `ca.jonathanfritz.ofxcat.service.TokenMigrationService`

#### Migration with Keyword Rule Sweep

Existing transactions need tokens computed and stored. Additionally, we apply keyword rules to fix transactions that may have been miscategorized by the old fuzzy matching system.

**Migration Algorithm:**

```
For each existing CategorizedTransaction:
    1. Compute tokens using TokenNormalizer
    2. Store tokens in TransactionToken table
    3. Check if any keyword rule matches the tokens
    4. If keyword rule matches AND current category differs from rule's category:
       → Update transaction's category to keyword rule's category
       → Update DescriptionCategory mapping
       → Log the change for user visibility
    5. If no keyword rule matches OR category already matches:
       → Keep existing category (preserves user's manual work)
```

**Implementation:**

```java
// TokenMigrationService.java
@Inject
public TokenMigrationService(
    Connection connection,
    CategorizedTransactionDao categorizedTransactionDao,
    TransactionTokenDao transactionTokenDao,
    DescriptionCategoryDao descriptionCategoryDao,
    TokenNormalizer tokenNormalizer,
    KeywordRulesConfig keywordRulesConfig
) { ... }

public MigrationReport migrateExistingTransactions() {
    List<CategorizedTransaction> all = categorizedTransactionDao.selectAll();

    MigrationReport report = new MigrationReport();

    // Filter to transactions that don't already have tokens
    List<CategorizedTransaction> needsMigration = all.stream()
        .filter(txn -> !transactionTokenDao.hasTokens(txn.getId()))
        .toList();

    if (needsMigration.isEmpty()) {
        logger.info("Token migration: all transactions already have tokens");
        return report;
    }

    logger.info("Token migration: processing {} transactions", needsMigration.size());

    // Process in batches for efficiency
    final int BATCH_SIZE = 100;
    for (int i = 0; i < needsMigration.size(); i += BATCH_SIZE) {
        List<CategorizedTransaction> batch = needsMigration.subList(
            i, Math.min(i + BATCH_SIZE, needsMigration.size()));

        try (DatabaseTransaction t = new DatabaseTransaction(connection)) {
            for (CategorizedTransaction txn : batch) {
                migrateTransaction(t, txn, report);
            }
        } catch (SQLException ex) {
            logger.error("Token migration failed at batch starting index {}", i, ex);
            throw new RuntimeException("Token migration failed", ex);
        }
    }

    // Log summary
    logger.info("Token migration completed: {} processed, {} recategorized",
        report.getProcessedCount(), report.getRecategorizedCount());

    return report;
}

private void migrateTransaction(DatabaseTransaction t, CategorizedTransaction txn, MigrationReport report) throws SQLException {
    Set<String> tokens = tokenNormalizer.normalize(txn.getDescription());

    // 1. Store tokens
    transactionTokenDao.insertTokens(t, txn.getId(), tokens);
    report.incrementProcessed();

    // 2. Check keyword rules
    Optional<Category> keywordCategory = keywordRulesConfig.findMatchingCategory(tokens);

    if (keywordCategory.isPresent() && !keywordCategory.get().equals(txn.getCategory())) {
        // 3. Recategorize
        Category oldCategory = txn.getCategory();
        Category newCategory = keywordCategory.get();

        categorizedTransactionDao.updateCategory(t, txn.getId(), newCategory);
        descriptionCategoryDao.updateOrInsert(t, txn.getDescription(), newCategory);

        report.addRecategorization(txn.getDescription(), oldCategory, newCategory);
        logger.debug("Recategorized '{}': {} -> {}",
            txn.getDescription(), oldCategory.getName(), newCategory.getName());
    }
}
```

**Migration Report:**

The migration produces a report that the user can review:

```java
public class MigrationReport {
    private int processedCount;
    private int recategorizedCount;
    private List<RecategorizationEntry> recategorizations;

    public record RecategorizationEntry(
        String description,
        String oldCategory,
        String newCategory
    ) {}
}
```

The report can be displayed to the user or written to a log file, showing which transactions were recategorized and why.

**User Control:**

- Users can edit `keyword-rules.yaml` before running migration to control what gets recategorized
- Users can remove rules for categories they want to preserve
- Migration is idempotent (safe to run multiple times)
- A `--dry-run` flag could show what would be changed without making changes

#### Integration with TransactionCategoryService

**Modified Flow**:

```
1. Check keyword rules (Phase 3)
   → If match found and auto_categorize enabled: return category
   → If match found and auto_categorize disabled: add to suggestions

2. Try exact description match (existing)
   → If single category found: return category
   → If multiple categories: prompt user with choices

3. Try token-based matching (Phase 2, replaces fuzzy matching)
   → If matches found: prompt user with ranked choices

4. Prompt user for new category (existing)
```

**Backward Compatibility**:
- Existing `DescriptionCategory` mappings remain valid
- Exact match still works as before
- Only the partial/fuzzy matching is replaced
- Database schema unchanged

**Configuration**:
Add to `~/.ofxcat/config.yaml` (create if doesn't exist):

```yaml
matching:
  # Minimum token overlap ratio for a match (0.0 to 1.0)
  token_overlap_threshold: 0.8

  # Whether keyword rules auto-categorize or just suggest
  keyword_auto_categorize: true

  # Enable/disable the legacy fuzzy matching as fallback
  # Set to true during transition period
  fuzzy_fallback_enabled: false
```

**Tests for Phase 4**:

*Migration Tests:*
- `tokenMigrationService_migratesExistingTransactions`
- `tokenMigrationService_skipsAlreadyMigratedTransactions`
- `tokenMigrationService_isIdempotent`
- `tokenMigrationService_recategorizesWhenKeywordRuleMatches`
- `tokenMigrationService_preservesCategoryWhenNoKeywordRuleMatches`
- `tokenMigrationService_preservesCategoryWhenAlreadyMatchesRule`
- `tokenMigrationService_updatesDescriptionCategoryOnRecategorization`
- `tokenMigrationService_producesAccurateMigrationReport`
- `tokenMigrationService_handlesLargeBatchesEfficiently`

*Integration Tests:*
- `categorizeTransaction_keywordRuleMatchAutoCategorizesWhenEnabled`
- `categorizeTransaction_keywordRuleMatchSuggestsWhenDisabled`
- `categorizeTransaction_exactMatchTakesPrecedenceOverKeyword`
- `categorizeTransaction_tokenMatchUsedWhenNoExactMatch`
- `categorizeTransaction_tokenMatchRankedByOverlap`
- `categorizeTransaction_fallsBackToManualWhenNoMatch`
- `categorizeTransaction_legacyFuzzyFallbackWhenEnabled`

### Phase 5: Cleanup

**Goal**: Remove deprecated fuzzy matching code and update documentation.

**Tasks**:
1. Remove FuzzyWuzzy dependency from `build.gradle`
2. Remove `FuzzySearch` imports from `TransactionCategoryService`
3. Update `CodebaseOverview.md` with new matching algorithm description
4. Update `README.md` with:
   - Keyword rules configuration section (location, format, examples)
   - How to override default rules (`~/.ofxcat/keyword-rules.yaml`)
   - How to contribute new rules back to the project
   - How to add new bank-specific cleaners
5. Update `CLAUDE.md` with:
   - Updated Architecture Overview explaining the two-stage normalization pipeline
   - TransactionCleaner vs TokenNormalizer responsibilities
   - Guidance on where to add new patterns (cleaner for bank-specific, normalizer for generic)
6. Add `keyword-rules.yaml` to default resources for distribution
7. Create migration guide for users with existing databases

**Documentation Content for CLAUDE.md**:

The Architecture Overview section should be updated to include:

```markdown
### Transaction Processing Pipeline

1. **OFX Parsing** → Raw `OfxTransaction` objects
2. **TransactionCleaner** → Bank-specific normalization (human-readable descriptions)
3. **TokenNormalizer** → Generic tokenization for matching (machine-comparable tokens)
4. **TokenMatchingService** → SQL-based lookup of similar transactions

**When adding new patterns:**
- Bank-specific prefixes/formats → Add to appropriate `TransactionCleaner` implementation
- Generic noise (store numbers, phone numbers) → Already handled by `TokenNormalizer`
- New payment processor prefixes → Add to `TokenNormalizer` if cross-bank, otherwise cleaner
```

**Tests**:
- Ensure all existing integration tests still pass
- Verify keyword rules file is created on first run if missing
- Verify graceful handling when keyword rules file is corrupted

---

## Database Considerations

### Schema Changes

**One new table required** for token storage (see Phase 2 for full details):

```sql
-- Flyway migration: V12__Add_Transaction_Tokens.sql
CREATE TABLE TransactionToken (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL REFERENCES CategorizedTransaction(id) ON DELETE CASCADE,
    token TEXT NOT NULL
);

CREATE INDEX idx_transaction_token_token ON TransactionToken(token);
CREATE INDEX idx_transaction_token_transaction_id ON TransactionToken(transaction_id);
```

**Existing tables remain unchanged**:
- `CategorizedTransaction.description` stores the original (cleaned) description
- `DescriptionCategory` maps descriptions to categories

### Migration Strategy

Existing transactions need their tokens populated when upgrading. Additionally, keyword rules are applied to fix transactions that may have been miscategorized by the old fuzzy matching system.

1. Flyway creates the empty `TransactionToken` table
2. On first application startup after upgrade, `TokenMigrationService` detects empty token table
3. For each `CategorizedTransaction` record:
   a. Normalize description and insert tokens into `TransactionToken`
   b. Check if any keyword rule matches the tokens
   c. If keyword rule matches and current category differs → recategorize transaction
4. Migration produces a report showing which transactions were recategorized
5. Migration is idempotent (checks if tokens already exist for each transaction)

**Note:** Migration requires keyword rules to be available (see Phase 3). Users can customize `keyword-rules.yaml` before migration to control which recategorizations occur.

This approach was chosen over SQLite FTS5 (full-text search) because:
- FTS5 is designed for natural language search with fuzzy matching and relevance ranking
- Our use case is simpler: deterministic set intersection on pre-normalized tokens
- Token table with index is more straightforward to implement, debug, and maintain
- No concerns about FTS5 availability across different SQLite builds/platforms

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Token normalization loses important information | Medium | Medium | Configurable normalization, keep original description |
| Keyword rules too aggressive (false positives) | Medium | Low | `auto_categorize: false` option, user can edit rules |
| Token migration slow for large databases | Low | Low | One-time cost, runs at startup, progress logged |
| Breaking change for existing users | Low | High | Exact match unchanged, gradual rollout |
| YAML parsing errors crash application | Medium | Medium | Graceful fallback to empty rules |
| Normalization rules change after tokens stored | Low | Medium | Provide re-tokenize command, or auto-detect rule version |

---

## Success Criteria

1. **All Starbucks variants categorize identically**: `STARBUCKS #4756`, `STARBUCKS 800-782-7282`, etc.
2. **All Walmart variants categorize identically**: `WAL-MART #1155`, `WAL-MART #3045`, etc.
3. **Truncated names match**: `SHOPPERS DRUG M` matches previous `SHOPPERS DRUG MART` transactions
4. **Keyword rules work**: New `STARBUCKS` transaction auto-categorizes as RESTAURANTS
5. **No regression**: Existing exact matches continue to work
6. **User control**: Users can edit keyword rules and thresholds

---

## Implementation Order

1. **Phase 1**: TokenNormalizer (foundation, no integration yet)
2. **Phase 2**: TokenMatchingService + TransactionTokenDao (can be tested in isolation)
3. **Phase 3**: KeywordRulesSystem (includes data acquisition task, can be tested in isolation)
4. **Phase 4**: Migration + Integration (requires Phase 2 and 3 to be complete)
5. **Phase 5**: Cleanup (remove old code, update docs)

```
Phase 1 ──┐
          ├──► Phase 4 ──► Phase 5
Phase 2 ──┤
          │
Phase 3 ──┘
```

Phases 1, 2, and 3 can be developed in parallel. Phase 3 includes a data acquisition task (building the keyword rules file) that can happen concurrently with coding.

**Critical dependency:** Migration in Phase 4 requires keyword rules from Phase 3 to perform the recategorization sweep.

---

## Estimated Scope

| Phase | New Classes | New Tests | New Files | Modified Files |
|-------|-------------|-----------|-----------|------------------|
| 1 | 2 | 14 | 0 | 0 |
| 2 | 2 | 16 | 1 (V12 migration) | 0 |
| 3 | 3 | 13 | 1 (keyword-rules.yaml) | 0 |
| 4 | 2 | 16 | 0 | 1 (TransactionCategoryService) |
| 5 | 0 | 3 | 0 | 4 (build.gradle, CLAUDE.md, README.md, CodebaseOverview.md) |
| **Total** | **9** | **62** | **2** | **5** |

Phase 2 new classes: `TokenMatchingService`, `TransactionTokenDao`
Phase 4 new classes: `TokenMigrationService`, `MigrationReport`

---

## Open Questions

*None remaining - all questions resolved.*

## Resolved Questions

### Implementation Decisions (January 2026)

**Phase 4 Scope Refinement:**
- **Config system**: Create `~/.ofxcat/config.yaml` as the central configuration file. It should include the path to keyword rules and other matching settings. If the config doesn't exist, auto-create it with sensible defaults and print a CLI message showing the location so users can customize it.
- **Category lookup**: `KeywordRulesConfig.findMatchingCategory()` returns `Optional<String>` (category name). The service layer looks up the Category via CategoryDao. If a keyword rule references a category that doesn't exist in the database, implicitly create it.
- **TransactionCategoryService integration**: Full integration of the new matching flow (keyword rules → exact match → token matching → manual) happens in Phase 4.
- **Migration service**: `TokenMigrationService` and `MigrationReport` move to Phase 5, not Phase 4.
- **DAO methods**: Add `updateCategory` and `updateOrInsert` methods as needed, using DatabaseTransaction pattern.

### Original Resolved Questions

1. **~~Should stemming be enabled by default?~~** **No.** Retailer names are proper nouns and remain consistent across transactions. Stemming adds complexity and false positive risk without meaningful benefit.

2. **~~Should we support regex in keyword rules?~~** **No, not initially.** Tokenization already normalizes away variations (punctuation, case, spacing) that regex would typically handle. This keeps the rules file simple and user-friendly. Regex support can be added later if a clear need emerges.

3. **~~Should the keyword rules file be version-controlled?~~** **Yes.** The default `keyword-rules.yaml` should be version-controlled and ship with the application, pre-populated with common retailers. This makes the utility immediately useful and helps others who discover the project on GitHub. The README must clearly document:
   - The existence and location of the default rules file
   - How users can override rules (edit `~/.ofxcat/keyword-rules.yaml`)
   - How to contribute new rules back to the project

4. **~~What's the right default threshold?~~** **0.8 (80%) is acceptable as the default.** The threshold should be configurable via `~/.ofxcat/config.yaml` so users can tune it without recompiling. This is already specified in Phase 4's configuration section (`token_overlap_threshold: 0.8`).

5. **~~Should PayPal transactions extract the merchant name?~~** **No, keep the payment processor in tokens.** `PAYPAL *FACERECORDS` should normalize to `{paypal, facerecords}` rather than just `{facerecords}`. The payment processor name (PayPal, Square, Shopify, etc.) provides useful context about the nature of the transaction (likely an online/retail purchase) even when the merchant name isn't recognized.

6. **~~Should location codes/city abbreviations be filtered?~~** **No.** Location suffixes like `KITCH` or `TORONTO` are intentionally kept. The token matching algorithm handles these naturally—partial overlap still matches. Maintaining a list of city abbreviations would add complexity, and many apparent "abbreviations" are actually truncated data that would be impossible to enumerate.

---

## Appendix: Sample Transaction Descriptions from OFX Files

These are anonymized examples from real OFX data that informed this design:

**Franchise Store Numbers**:
- `WAL-MART #1155`, `WAL-MART #1156`, `WAL-MART #3045`
- `STARBUCKS #4756`, `STARBUCKS 800-782-7282`
- `TIM HORTONS #01`, `TIM HORTONS #06`
- `DOLLARAMA # 257`
- `LCBO/RAO #0417`, `LCBO/RAO #7777`
- `MICHAELS #3988`
- `SWISS CHALET #1`

**Location Suffixes**:
- `SEPHORA - KITCH`
- `ZEHRS MARKET ST`, `ZEHRS MARKET STANLEY P`
- `EUROPRO (KITCHE`
- `GALAXY CINEMAS`

**Truncated Names**:
- `SHOPPERS DRUG M` (Shoppers Drug Mart)
- `REN'S PETS DEPO` (Ren's Pets Depot)
- `PINO'S SALON AN`
- `BLISS COUNSELLI`
- `A AND M WOOD A`

**Online/Credit Card**:
- `Amazon.ca*T23YP3F33`, `Amazon.ca*X56OF5GV3`
- `PAYPAL *FACERECORDS`, `PAYPAL *UNIVERSALMU`, `PAYPAL *BIG B BRICK`
- `SP * ONCE UPON A CHILD`, `SP * VILLAGE CRAFT & C`, `SP * ARTURO DENIM CO.`
- `Spotify P12CE9DA9B`
- `APPLE.COM/BILL`

**Special Characters**:
- `MCDONALD'S #290`
- `A&W #4330`
- `BED BATH & BEYOND #2291` (stored as `BED BATH &amp; BEYOND`)
- `SWANSON'S HOME HARDWARE`
- `CARTERS/OSHKOSH ECOMM`
