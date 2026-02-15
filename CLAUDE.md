# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Collaboration Rules

See `docs/GenAIGuide.md` for all collaboration rules, coding standards, TDD process, debugging framework, and testing priorities. Read it fully before starting work.

## Build Commands

```bash
# Verify everything before committing (tests + Error Prone + Spotless + checkstyle + PMD + SpotBugs + coverage)
./gradlew verify

# Build everything including fat JAR
./gradlew clean build shadowJar

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "ca.jonathanfritz.ofxcat.datastore.AccountDaoTest"

# Run a specific test method
./gradlew test --tests "ca.jonathanfritz.ofxcat.datastore.AccountDaoTest.testSelectById"

# Auto-fix formatting (Spotless + Palantir Java Format)
./gradlew spotlessApply

# Check formatting without modifying files
./gradlew spotlessCheck

# Run checkstyle only
./gradlew checkstyleMain checkstyleTest

# Run PMD only
./gradlew pmdMain pmdTest

# Run SpotBugs only
./gradlew spotbugsMain spotbugsTest

# Run coverage report (opens build/reports/jacoco/test/html/index.html)
./gradlew jacocoTestReport

# Run the application
java -jar build/libs/ofxcat-1.0-SNAPSHOT-jar-with-dependencies.jar <command>
```

**Before committing, always run `./gradlew verify`** to confirm tests pass and code style is clean. A pre-commit hook enforces this automatically (see below).

## Pre-commit Hook

A git pre-commit hook runs `./gradlew verify` before every commit. If verification fails, the commit is rejected.

The hook lives in `.githooks/pre-commit` and is activated by:
```bash
git config core.hooksPath .githooks
```

This is a per-clone setting. After a fresh clone, run the command above to enable the hook.

## Architecture Overview

Java 21 CLI application for importing and categorizing OFX bank transactions using SQLite storage.

### Layer Structure and Dependency Rules

The architecture is enforced by ArchUnit tests in `LayeredArchitectureTest`. New packages must be added to the test before the build will pass.

```
OfxCat.java (entry point) — can access all layers
    ↓
service / cli — business logic and user interaction
    ↓             cli uses DTOs and IO types for display/prompts
matching    — token matching, keyword rules (can access datastore, config)
cleaner     — bank-specific transaction cleaning (can access datastore, io)
    ↓
datastore   — DAOs, DTOs, database utilities (no upward dependencies)
io          — OFX parsing (self-contained, no upward dependencies)
    ↓
config / utils / exception — leaf packages, no upward dependencies
```

**Key constraints (violations fail the build):**
- DAOs must never import from service, cli, matching, cleaner, or io
- DTOs must be pure data classes — no imports from other layers
- IO classes must not depend on any other layer
- No circular dependencies between packages
- Services are only accessed by the entry point
- New packages must be registered in `LayeredArchitectureTest` before use

### Key Design Patterns
- **Google Guice** for dependency injection (`CLIModule`, `DatastoreModule`, `MatchingModule`)
- **Factory + Classpath scanning**: `TransactionCleanerFactory` auto-discovers bank-specific cleaners
- **Builder pattern**: DTOs like `Transaction`, `OfxAccount` are immutable with builders
- **DAO pattern**: All database access through DAO classes
- **Token matching**: `TokenNormalizer` and `TokenMatchingService` for intelligent categorization

### Transaction Categorization Algorithm (TransactionCategoryService)

Four-tier matching strategy:
1. **Keyword rules**: Match normalized tokens against `keyword-rules.yaml` → auto-categorize if rule matches
2. **Exact match**: Find transactions with identical description → auto-categorize if single category
3. **Token match**: Normalize description into tokens, find transactions with token overlap ≥ threshold (default 60%) → auto-categorize if single strong match, else prompt user
4. **Manual**: User chooses from all categories or creates new one

### Configuration (`~/.ofxcat/config.yaml`)
- `keyword_rules_path`: Path to keyword rules file (default: `keyword-rules.yaml`)
- `token_matching.overlap_threshold`: Minimum token overlap for matching (default: 0.6)

### Transfer Detection (TransferMatchingService)
Matches XFER-type transactions across accounts by: same date + opposite amounts + different accounts

### Bank-Specific Cleaning
Implement `TransactionCleaner` interface with rule-based pattern matching. Only RBC supported out of box. Factory auto-discovers implementations by scanning classpath for classes implementing the interface.

## Testing

- Tests extend `AbstractDatabaseTest` for Flyway setup with in-memory SQLite
- Test OFX files in `src/test/resources/` (creditcard.ofx, oneaccount.ofx, etc.)
- `TestUtils` provides helper methods for creating test data

## Database

Schema managed by Flyway migrations in `src/main/resources/db/migration/`. Key tables:
- `Category` - transaction categories (includes UNKNOWN and TRANSFER defaults)
- `Account` - bank accounts with user-assigned names
- `CategorizedTransaction` - all imported transactions with categories and `fit_id` for deduplication
- `Transfer` - links source/sink transaction pairs for inter-account transfers
- `DescriptionCategory` - maps descriptions to categories for auto-categorization
- `TransactionToken` - normalized tokens for each transaction (for token-based matching)

## Key Documentation

- `docs/GenAIGuide.md` - Collaboration rules and coding standards (required reading)
- `docs/CodebaseOverview.md` - Comprehensive technical documentation
- `docs/StaticAnalysis.md` - Spotless, Error Prone, Checkstyle, PMD, SpotBugs, and JaCoCo configuration, static analysis rules, and how to fix violations
