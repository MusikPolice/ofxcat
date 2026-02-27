# ofxcat Codebase Overview

**Last Updated:** January 21, 2026  
**Purpose:** Comprehensive technical documentation for developers maintaining and extending this application.

---

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Command Line Interface](#command-line-interface)
4. [Dependencies](#dependencies)
5. [Code Structure](#code-structure)
6. [Database Schema](#database-schema)
7. [Key Algorithms](#key-algorithms)
8. [Configuration & Storage](#configuration--storage)
9. [Testing](#testing)
10. [Known Issues](#known-issues)
11. [Areas for Improvement](#areas-for-improvement)

---

## Overview

ofxcat is a Java 21 command-line application that imports and categorizes financial transactions from OFX (Open Financial Exchange) files. The application uses intelligent token-based matching with configurable keyword rules to automatically categorize transactions based on historical patterns and prompts users interactively when needed.

**Core Functionality:**
- Parse OFX files exported from banking institutions
- Store transactions in a local SQLite database
- Automatically detect inter-account transfers
- Categorize transactions using keyword rules, token-based matching, and exact matching
- Generate CSV reports showing spending by category over time
- Learn from user input to improve categorization accuracy
- Apply keyword rules for automatic categorization of common merchants

---

## Architecture

### Design Pattern
The application follows a **layered architecture** with dependency injection:

```
┌─────────────────────────────────────┐
│     CLI Layer (OfxCat, CLI)         │  ← Entry point, argument parsing
├─────────────────────────────────────┤
│  Service Layer (Import, Category,   │  ← Business logic
│   Reporting, TransferMatching,      │
│   CategoryCombine)                  │
├─────────────────────────────────────┤
│    DAO Layer (AccountDao, etc)      │  ← Data access
├─────────────────────────────────────┤
│  Database (SQLite + Flyway)         │  ← Persistence
└─────────────────────────────────────┘
```

### Dependency Injection
Uses **Google Guice** for dependency injection with two modules:
- `CLIModule`: Provides TextIO for interactive terminal UI
- `DatastoreModule`: Configures database connection, can be on-disk or in-memory (for testing)

### Key Patterns
- **Factory Pattern**: `TransactionCleanerFactory` uses classpath scanning to discover bank-specific cleaners
- **Builder Pattern**: DTOs like `Transaction`, `OfxAccount` use builders
- **DAO Pattern**: All database access goes through DAO classes
- **Strategy Pattern**: `TransactionCleaner` interface with bank-specific implementations

---

## Command Line Interface

### Entry Point
**Class:** `ca.jonathanfritz.ofxcat.OfxCat`  
**Build Output:** `build/libs/ofxcat-<hash>.jar`

### Commands

#### Import Transactions
```bash
java -jar ofxcat-<hash>.jar import <filename.ofx>
```
- Parses OFX file
- Prompts for account names on first encounter
- Automatically categorizes transactions (with user prompts when needed)
- Detects inter-account transfers
- Backs up imported file to `~/.ofxcat/imported/`
- Optionally deletes the original file

#### Get Accounts
```bash
java -jar ofxcat-<hash>.jar get accounts
```
Outputs all known accounts in CSV format.

#### Get Categories
```bash
java -jar ofxcat-<hash>.jar get categories
```
Outputs all known categories in CSV format.

#### Get Transactions Report
```bash
java -jar ofxcat-<hash>.jar get transactions \
  --start-date=2022-01-01 \
  --end-date=2022-12-31 \
  [--category-id=<id>]
```
- `--start-date`: Required, format `yyyy-MM-dd`
- `--end-date`: Optional, defaults to today
- `--category-id`: Optional, filters to specific category

Outputs a matrix with months as rows and categories as columns, showing total spending per category per month. Includes p50, p90, average, and total rows.

#### Combine Categories
```bash
java -jar ofxcat-<hash>.jar combine categories \
  --source=DAYCARE --target="CHILD CARE"
```
- `--source`: Required. Name of the category to move transactions from.
- `--target`: Required. Name of the category to move transactions to. Created if it doesn't exist.

Moves all transactions from the source category to the target, then deletes the source. After a successful combine, if any keyword rules in `keyword-rules.yaml` reference the deleted category, the user is warned and offered to update the rules automatically.

#### Rename Category
```bash
java -jar ofxcat-<hash>.jar rename category \
  --source=DAYCARE --target="CHILD CARE"
```
Alias for `combine categories`. Same behavior and options.

#### Help
```bash
java -jar ofxcat-<hash>.jar help
```

### Interactive Prompts
Uses **TextIO** library for rich terminal interaction:
- Yes/No prompts for decisions
- List selection for categories
- Text input for account/category names
- Colored/formatted output for readability

---

## Dependencies

### Core Libraries

| Dependency | Version | Purpose |
|------------|---------|---------|
| **JDK** | 21 | Language platform |
| **Gradle** | 9.3.1 | Build system (wrapper included) |
| **Google Guice** | 7.0.0 | Dependency injection |
| **SQLite JDBC** | 3.51.2.0 | Database driver |
| **Flyway** | 12.0.1 | Database migrations |
| **ofx4j** | 1.39 | OFX file parsing |
| **TextIO** | 3.4.1 | Interactive CLI |
| **Commons CLI** | 1.10.0 | Command-line parsing |
| **Commons Lang3** | 3.19.0 | Utility functions |
| **Log4j2** | 2.25.3 | Logging |
| **Jackson** | 2.21.0 | YAML parsing (for log config) |
| **ClassGraph** | 4.8.184 | Classpath scanning |

### Test Dependencies
- **JUnit Jupiter** 6.0.0
- **Hamcrest** 3.0

### Build Plugins
- **Shadow** 9.3.1 (`com.gradleup.shadow`) - Creates fat JAR with dependencies

---

## Code Structure

### Package Organization
```
ca.jonathanfritz.ofxcat/
├── cleaner/               # Transaction cleaning/normalization
│   ├── rules/             # Matching rules for transaction patterns
│   ├── TransactionCleaner.java
│   ├── TransactionCleanerFactory.java
│   ├── DefaultTransactionCleaner.java
│   └── RbcTransactionCleaner.java
├── cli/                   # Command-line interface
│   ├── CLI.java
│   ├── CLIModule.java
│   └── TextIOWrapper.java
├── datastore/             # Database access layer
│   ├── dto/               # Data transfer objects
│   │   ├── Account.java
│   │   ├── Category.java
│   │   ├── Transaction.java
│   │   ├── CategorizedTransaction.java
│   │   ├── DescriptionCategory.java
│   │   └── Transfer.java
│   ├── utils/             # Database utilities
│   │   ├── DatabaseTransaction.java
│   │   ├── DatastoreModule.java
│   │   ├── Entity.java
│   │   ├── ResultSetDeserializer.java
│   │   ├── SqlConsumer.java
│   │   └── SqlFunction.java
│   ├── AccountDao.java
│   ├── CategoryDao.java
│   ├── CategorizedTransactionDao.java
│   ├── DescriptionCategoryDao.java
│   └── TransferDao.java
├── exception/             # Custom exceptions
│   ├── OfxCatException.java
│   └── CliException.java
├── io/                    # OFX parsing
│   ├── OfxParser.java
│   ├── OfxAccount.java
│   ├── OfxBalance.java
│   ├── OfxExport.java
│   └── OfxTransaction.java
├── matching/              # Token-based matching and keyword rules
│   ├── KeywordRule.java
│   ├── KeywordRulesConfig.java
│   ├── KeywordRulesLoader.java
│   ├── MatchingModule.java
│   ├── TokenMatchingConfig.java
│   ├── TokenMatchingService.java
│   └── TokenNormalizer.java
├── config/                # Configuration management
│   ├── AppConfig.java
│   └── AppConfigLoader.java
├── service/               # Business logic
│   ├── CategoryCombineService.java
│   ├── TransactionImportService.java
│   ├── TransactionCategoryService.java
│   ├── TransferMatchingService.java
│   └── ReportingService.java
├── utils/                 # Utilities
│   ├── Accumulator.java
│   ├── Log4jLogger.java
│   ├── PathUtils.java
│   └── StringUtils.java
└── OfxCat.java           # Application entry point
```

### Custom SLF4J Shim
**Notable:** The codebase includes custom implementations of `org.slf4j.Logger` and `org.slf4j.LoggerFactory` that delegate to Log4j2. This avoids dependency conflicts between libraries expecting SLF4J.

---

## Database Schema

### Storage Location
`~/.ofxcat/ofxcat.db` (SQLite3)

### Schema Evolution
Managed by **Flyway** migrations in `src/main/resources/db/migration/`

### Tables

#### Category
```sql
CREATE TABLE Category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);
```
Stores transaction categories (e.g., GROCERIES, MORTGAGE). Two default categories are created:
- UNKNOWN (V8 migration)
- TRANSFER (V9 migration)

#### DescriptionCategory
```sql
CREATE TABLE DescriptionCategory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    description TEXT NOT NULL,
    category_id INTEGER REFERENCES Category (id) ON DELETE CASCADE
);
```
Maps transaction descriptions to categories. Used for automatic categorization based on learned patterns.

#### Account
```sql
CREATE TABLE Account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bank_number TEXT,
    account_number TEXT NOT NULL,
    account_type TEXT,
    name TEXT NOT NULL
);
```
Stores bank accounts. User-assigned friendly names map to OFX account IDs.

#### CategorizedTransaction
```sql
CREATE TABLE CategorizedTransaction (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT,
    date DATE NOT NULL,
    amount FLOAT NOT NULL,
    description TEXT NOT NULL,
    account_id INTEGER REFERENCES Account (id) ON DELETE CASCADE,
    category_id INTEGER REFERENCES Category (id) ON DELETE CASCADE,
    balance FLOAT,              -- Added in V5
    fit_id TEXT                 -- Added in V7
);
```
Core table storing all imported transactions with their categories. `fit_id` is the unique transaction identifier from the OFX file.

#### Transfer
```sql
CREATE TABLE Transfer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER REFERENCES CategorizedTransaction (id) ON DELETE CASCADE,
    sink_id INTEGER REFERENCES CategorizedTransaction (id) ON DELETE CASCADE
);
```
Links pairs of transactions that represent inter-account transfers.

#### TransactionToken
```sql
CREATE TABLE TransactionToken (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL REFERENCES CategorizedTransaction (id) ON DELETE CASCADE,
    token TEXT NOT NULL
);
```
Stores normalized tokens for each transaction, enabling token-based matching during categorization.

### Migration History
- V1: Category table
- V2: DescriptionCategory table
- V3: Account table
- V4: CategorizedTransaction table
- V5: Added balance column
- V6: Fixed rounding issues
- V7: Added fit_id column
- V8: Created UNKNOWN default category
- V9: Created TRANSFER default category
- V10: Transfer table
- V11: TransactionToken table for token-based matching

---

## Key Algorithms

### 1. Transaction Import Flow

**Class:** `TransactionImportService`

```
1. Parse OFX file → List<OfxExport>
2. For each account in exports:
   a. Find or create Account (prompts user for name if new)
   b. Calculate initial balance from final balance - sum(transactions)
   c. Sort transactions by date
   d. Apply bank-specific cleaning via TransactionCleaner
   e. Calculate running balance for each transaction
3. Identify inter-account transfers
4. For remaining transactions:
   a. Check if duplicate (skip if yes)
   b. Attempt auto-categorization
   c. Insert into database
```

### 2. Transaction Categorization Algorithm

**Class:** `TransactionCategoryService`

The categorization process uses a **four-tier matching strategy**:

#### Tier 1: Keyword Rules
```java
categorizeTransactionKeywordRules(transaction)
```
1. Normalize description into tokens using `TokenNormalizer`
2. Match tokens against configured keyword rules
3. Rules are loaded from `~/.ofxcat/keyword-rules.yaml` or bundled defaults
4. If a rule matches → auto-categorize to specified category
5. If no match → proceed to Tier 2

#### Tier 2: Exact Match
```java
categorizeTransactionExactMatch(transaction)
```
1. Search for transactions with identical description
2. Extract distinct categories from matches (excluding UNKNOWN)
3. If **exactly one** category found → auto-categorize
4. If **multiple** categories → prompt user to choose
5. If **zero** matches → proceed to Tier 3

#### Tier 3: Token-Based Match
```java
categorizeTransactionTokenMatch(transaction)
```
1. Normalize description into tokens using `TokenNormalizer`
   - Converts to lowercase
   - Splits on whitespace and special characters
   - Removes common stop words (THE, A, AN, etc.)
   - Filters out pure numbers, store numbers, phone numbers
2. Search `TransactionToken` table for transactions with matching tokens
3. Calculate token overlap percentage: `matching_tokens / total_tokens`
4. Filter to candidates meeting `overlap_threshold` (default 0.6)
5. Group by category and rank by match quality
6. If **exactly one** category with strong match → auto-categorize
7. If **multiple** categories → prompt user to choose from ranked list
8. If **zero** matches → proceed to Tier 4

#### Tier 4: Manual Selection
```java
chooseExistingCategoryOrAddNew(transaction)
```
1. Prompt user to choose from all existing categories
2. Allow creation of new category

### Token Normalization

**Class:** `TokenNormalizer`

Converts transaction descriptions into normalized tokens:
- Converts to lowercase
- Splits on whitespace and special characters
- Removes stop words: THE, A, AN, AND, OR, OF, TO, IN, FOR, AT, BY
- Filters out pure numbers and common patterns (store IDs, phone numbers)

### Keyword Rules Configuration

**File:** `~/.ofxcat/keyword-rules.yaml`

```yaml
version: 1
settings:
  auto_categorize: true
rules:
  - keywords: [amazon, amzn]
    category: Shopping
  - keywords: [uber, lyft]
    category: Transportation
```

Rules are processed in order; first match wins.

### 3. Transfer Detection

**Class:** `TransferMatchingService`

Identifies inter-account transfers:
```
1. Extract all XFER-type transactions
2. Separate into source (negative amount) and sink (positive amount)
3. For each source:
   a. Find sinks with:
      - Same date
      - Amount = source.amount * -1
      - Different account
   b. If exactly one match → create Transfer
4. Remove matched transactions from import queue
5. Categorize as TRANSFER
```

**Limitation:** Only matches transfers that occur on the same day with exact amounts. Does not handle delayed transfers or transactions with fees.

### 4. Transaction Cleaning (Bank-Specific)

**Class:** `RbcTransactionCleaner` (example)

Uses **rule-based pattern matching** to normalize transaction descriptions:

```java
TransactionMatcherRule.newBuilder()
    .withName(Pattern.compile("^WWW TRF DDA - \\d+.*$"))
    .build(ofxTransaction -> 
        Transaction.newBuilder()
            .setType(XFER)
            .setDescription("TRANSFER OUT OF ACCOUNT")
    )
```

Rules match on:
- Transaction name pattern
- Memo pattern
- Type (DEBIT/CREDIT)
- Amount range

**Factory Discovery:** `TransactionCleanerFactory` uses ClassGraph to scan the classpath for all `TransactionCleaner` implementations, caching them by `bankId`.

---

## Configuration & Storage

### File Locations

| File | Location | Purpose |
|------|----------|---------|
| Database | `~/.ofxcat/ofxcat.db` | SQLite database |
| Configuration | `~/.ofxcat/config.yaml` | Application configuration |
| Keyword Rules | `~/.ofxcat/keyword-rules.yaml` | Automatic categorization rules |
| Logs | `~/.ofxcat/ofxcat.log` | Application logs |
| Imported Files | `~/.ofxcat/imported/` | Backup copies of OFX files |

**Security Note:** All files may contain sensitive financial information. The README warns users to protect these files appropriately.

### Application Configuration

**File:** `~/.ofxcat/config.yaml`

```yaml
# Keyword rules file location (relative to config directory or absolute path)
keyword_rules_path: keyword-rules.yaml

# Token matching settings
token_matching:
  # Minimum percentage of tokens that must match (0.0-1.0)
  overlap_threshold: 0.6
```

A default configuration file is created on first run if one doesn't exist.

### Logging Configuration

**File:** `src/main/resources/log4j2.yaml`

- Main application: ALL level
- Flyway: INFO level
- Output: `~/.ofxcat/ofxcat.log`
- Format: `HH:mm:ss.SSS [thread] LEVEL logger - message`

### TextIO Configuration

**File:** `src/main/resources/textio.properties`

Controls terminal styling and formatting (colors, prompts, etc.).

---

## Testing

### Test Structure
```
src/test/java/ca/jonathanfritz/ofxcat/
├── cleaner/
│   ├── rules/
│   ├── DefaultTransactionCleanerTest.java
│   ├── RbcTransactionCleanerTest.java
│   └── TransactionCleanerFactoryTest.java
├── datastore/
│   ├── AccountDaoTest.java
│   ├── CategoryDaoTest.java
│   ├── CategorizedTransactionDaoTest.java
│   ├── DescriptionCategoryDaoTest.java
│   └── TransferDaoTest.java
├── io/
│   └── OfxParserTest.java
├── service/
│   ├── CategoryCombineServiceTest.java
│   ├── ReportingServiceTest.java
│   ├── TransactionCategoryServiceTest.java
│   ├── TransactionImportServiceTest.java
│   └── TransferMatchingServiceTest.java
├── utils/
│   └── PathUtilsTest.java
├── AbstractDatabaseTest.java
├── OfxCatImportValidationTest.java
├── OfxCatParameterParsingTest.java
├── OfxCatTest.java
└── TestUtils.java
```

### Test Resources
Sample OFX files in `src/test/resources/`:
- `creditcard.ofx`
- `oneaccount.ofx`
- `twoaccounts.ofx`
- `twoaccountsonecreditcard.ofx`

### Testing Approach
- **DAO Tests:** Use in-memory SQLite (`DatastoreModule.inMemory()`)
- **Service Tests:** Mix of unit and integration tests
- **Database Tests:** Extend `AbstractDatabaseTest` for Flyway setup
- **Test Utilities:** `TestUtils` provides helper methods

### Current Test Coverage Gaps (see TODOs)
- CLI not tested (marked "TODO: test me?")
- `TransactionCategoryService` marked "TODO: Test me!"
- `TransactionImportService` marked "TODO: Improve test coverage"

---

## Known Issues

### 1. Unsafe Method Warnings (Documented)
```
WARNING: sun.misc.Unsafe::objectFieldOffset has been called
```
**Source:** Transitive dependency `com.google.guava:guava:31.0.1-jre`  
**Impact:** Cosmetic only, functionality unaffected  
**Status:** Waiting for library updates  
**Workaround:** Use `--enable-native-access=ALL-UNNAMED` JVM flag (configured in build.gradle)

### 2. Restricted Method Warnings
```
WARNING: java.lang.System::loadLibrary has been called
```
**Source:** HawtJNI (native library loading)  
**Impact:** Cosmetic only  
**Status:** Warning can be suppressed with JVM flags

### 3. Transfer Detection Limitations
**Issue:** Only matches same-day transfers with exact amounts  
**Impact:** Multi-day transfers or transfers with fees are not detected  
**Potential Fix:** Implement fuzzy date/amount matching within configurable thresholds

### 4. Token Match Threshold
**File:** `~/.ofxcat/config.yaml`
**Status:** Now configurable via `token_matching.overlap_threshold` setting
**Default:** 0.6 (60%)

### 5. LCBO/RAO Categorization Issue
**File:** `TransactionCategoryService.java:75`  
**TODO:** "why does LCBO/RAO not auto-categorize?"  
**Status:** Specific vendor not being matched, needs investigation

---

## Areas for Improvement

### High Priority

#### 1. Missing Test Coverage
**Impact:** High risk of regressions

- **CLI class** (`cli/CLI.java`) - No tests
- **CLI parameter parsing** (`OfxCatTest.java`) - Empty test class
- **TransactionCategoryService** - Critical business logic untested
- **TransactionImportService** - Incomplete coverage

**Recommendation:** Achieve >80% coverage on service layer before adding features.

#### 2. Missing Features (TODOs)

From `OfxCat.java`:
- **Line 128:** "add a mode that allows reprocessing of transactions from some category"
  - Use case: User miscategorized transactions and wants to fix them
- **Line 147:** "add a way to export actual transactions, not just category sums"
  - Current reports only show aggregates
- **Line 151:** "need a way to edit categories and category descriptions"
  - Combine/rename is now implemented; category descriptions still not editable
- **Line 209:** "add group-by arg, values are category, day, week, month, year, type"
  - Reporting flexibility

From `ReportingService.java`:
- **Lines 198, 212:** "add a table-formatted option"
  - Alternative to CSV output for terminal viewing

#### 3. Performance Concerns

**CategorizedTransactionDao.java:40**
> "some kind of cache for Account and Category objects would be a good idea..."

**Issue:** Repeated database lookups for the same reference data  
**Impact:** Import performance degrades with large OFX files  
**Recommendation:** Implement simple in-memory cache with weak references

#### 4. Code Quality Issues

**DescriptionCategoryDao.java:38**
> "it isn't clear that this is the best approach - on one hand, we reduce code duplication..."

**Issue:** Design uncertainty noted in comment  
**Recommendation:** Review and refactor if needed, remove uncertain comments

#### 5. UI/UX Improvements

From `OfxCat.java`:
- **Line 63:** "show a progress bar?" - Large imports have no progress indication
- **Line 64:** "retain scrolling list of categorizations on screen" - User loses context

### Medium Priority

#### 6. Better CSV Handling
**Issue:** Category names can't contain commas (checked in `CLI.java`)  
**Recommendation:** Use proper CSV library (Apache Commons CSV or OpenCSV)

#### 7. Bank Support Expansion
**Current:** Only RBC officially supported  
**Opportunity:** Community contributions for other banks  
**Documentation:** Contributing guide exists but could be more prominent

#### 8. Database Migrations Need Documentation
**Issue:** Migration files lack comments explaining business context  
**Recommendation:** Add comments to each migration explaining WHY changes were made

### Low Priority

#### 9. Logging Verbosity
**Issue:** Main application logs at ALL level  
**Impact:** Large log files  
**Recommendation:** Use DEBUG for development, INFO for production

#### 10. Error Handling
**Issue:** Generic `OfxCatException` used throughout  
**Recommendation:** Create specific exception types for different error conditions

#### 11. Configuration Management
**Status:** Implemented via `~/.ofxcat/config.yaml`
- Token matching threshold is configurable
- Keyword rules can be customized
- Default configuration is auto-created on first run

---

## Code Quality Observations

### Strengths
1. **Clean separation of concerns** - Layered architecture is well-executed
2. **Dependency injection** - Properly used, makes testing easier
3. **Builder pattern** - DTOs are immutable and safely constructed
4. **Database migrations** - Schema changes are tracked and versioned
5. **Logging** - Comprehensive logging at all levels
6. **Token-based matching** - Smart categorization algorithm with configurable keyword rules
7. **Factory pattern** - Extensible bank support via classpath scanning

### Weaknesses
1. **Test coverage** - Critical gaps in service and CLI layers
2. **TODO comments** - Many unresolved design questions
3. **Error messages** - Generic exceptions lose context
4. **Documentation** - Internal code comments sparse in places
5. **Hardcoded values** - Magic numbers and thresholds throughout
6. **CSV handling** - Naive string concatenation instead of proper library

### Code Smells
1. **Long methods** - Some service methods exceed 50 lines
2. **God objects** - `TransactionImportService` does too much
3. **Primitive obsession** - Float for currency (should use BigDecimal)
4. **Feature envy** - Some methods in services primarily manipulate DTO internals

---

## Development Workflow

### Building
```bash
./gradlew clean build shadowJar
```

### Running
```bash
java -jar build/libs/ofxcat-$(git rev-parse --short=7 HEAD).jar <command>
```

### Testing
```bash
./gradlew test
```

### Debugging Database
```bash
sqlite3 ~/.ofxcat/ofxcat.db
```

---

## Security Considerations

1. **Sensitive Data:** Database and logs contain account numbers, transaction details, balances
2. **Storage:** Files stored in user home directory (~/.ofxcat/)
3. **Permissions:** No explicit file permission setting - relies on OS defaults
4. **Encryption:** Data stored in plaintext
5. **Recommendation:** Document security best practices for users (file encryption, restrictive permissions)

---

## Extension Points

### Adding Support for a New Bank

1. Determine the bank's `BANKID` from an OFX export
2. Create new class implementing `TransactionCleaner` in `ca.jonathanfritz.ofxcat.cleaner`
3. Implement `getBankId()` to return the bank's ID
4. Implement `clean()` using `TransactionMatcherRule` builders
5. Add unit tests extending pattern from `RbcTransactionCleanerTest`
6. No registration needed - `TransactionCleanerFactory` auto-discovers via classpath scan

### Adding New Report Types

1. Add method to `ReportingService`
2. Add new `Concern` enum value in `OfxCat`
3. Add DAO method if new query is needed
4. Wire up in `OfxCat.main()` switch statement
5. Update help text

### Adding New Transaction Matchers

1. Create new `Rule` class in `ca.jonathanfritz.ofxcat.cleaner.rules`
2. Use existing `TransactionMatcherRule` and `AmountMatcherRule` as patterns
3. Compose rules in bank-specific cleaner

---

## Technical Debt Summary

| Issue | Severity | Effort | Priority |
|-------|----------|--------|----------|
| Missing test coverage | High | High | 1 |
| Float for currency (should be BigDecimal) | High | Medium | 2 |
| Some hardcoded values | Medium | Low | 3 |
| Generic exceptions | Medium | Medium | 4 |
| Missing cache for reference data | Medium | Low | 5 |
| No progress indication | Low | Low | 6 |
| CSV handling | Low | Low | 7 |

---

## Conclusion

This is a well-architected, functional application with a solid foundation. The use of dependency injection, database migrations, and token-based matching with keyword rules demonstrates mature engineering. The main areas needing attention are:

1. **Test coverage** - Critical for maintainability
2. **BigDecimal for currency** - Current float usage risks precision errors
3. **Completing TODO items** - Many features partially implemented

The codebase is clean and readable, making it a good candidate for ongoing maintenance and feature additions. The extension points (bank cleaners, reports) are well-designed for community contributions.

