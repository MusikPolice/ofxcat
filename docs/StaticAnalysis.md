# Static Analysis

This document describes the static analysis tools configured for this project, what they enforce, and how to work with them.

## Spotless — Code Formatting

Spotless provides deterministic, automatic code formatting using [Palantir Java Format](https://github.com/palantir/palantir-java-format). It runs as part of `./gradlew verify` and will fail the build if any file is not formatted correctly.

### Configuration

- **Gradle plugin**: `com.diffplug.spotless` version 7.0.2
- **Formatter**: Palantir Java Format (4-space indentation, 120-char line width)
- **Additional steps**: `removeUnusedImports()`, `trimTrailingWhitespace()`, `endWithNewline()`

### Running Spotless

```bash
# Auto-fix all formatting issues
./gradlew spotlessApply

# Check formatting without modifying files (used by verify)
./gradlew spotlessCheck
```

### What Spotless Enforces

- **Consistent indentation** (4 spaces, no tabs)
- **Line wrapping** at 120 characters
- **Import ordering** (deterministic, normalized)
- **Trailing whitespace** removal
- **Newline at end of file**
- **Consistent spacing** around operators, keywords, braces, parentheses, etc.

### Relationship to Checkstyle

Spotless handles all **layout and whitespace** formatting deterministically — the formatter produces the one correct output, so there's nothing to check. Checkstyle handles **semantic style rules** that a formatter can't enforce: naming conventions, import restrictions (no star imports, no unused imports), code structure (braces required, one statement per line), and correctness checks (equals/hashCode pairing, fall-through documentation).

The whitespace-related Checkstyle modules (`FileTabCharacter`, `LineLength`, `NewlineAtEndOfFile`, `EmptyForIteratorPad`, `GenericWhitespace`, `MethodParamPad`, `NoWhitespaceAfter`, `NoWhitespaceBefore`, `ParenPad`, `TypecastParenPad`, `WhitespaceAfter`, `WhitespaceAround`, `CommentsIndentation`) were removed when Spotless was added since the formatter now enforces these deterministically.

## Checkstyle

Checkstyle enforces consistent code style across the project. It runs as part of `./gradlew verify` and will fail the build on any violation.

### Configuration

- **Config file**: `config/checkstyle/checkstyle.xml`
- **Suppressions**: `config/checkstyle/suppressions.xml`
- **Checkstyle version**: 10.21.4
- **Severity**: All violations are errors (zero tolerance)

### Running Checkstyle

```bash
# Run on main sources only
./gradlew checkstyleMain

# Run on test sources only
./gradlew checkstyleTest

# Run everything (tests + checkstyle)
./gradlew verify
```

Reports are generated at:
- `build/reports/checkstyle/main.html`
- `build/reports/checkstyle/test.html`

### What Checkstyle Enforces

#### Imports
- **No wildcard imports** (`import java.util.*`). Use explicit imports instead.
- **Static wildcard imports are allowed** (e.g., `import static org.junit.jupiter.api.Assertions.*`) since they're standard practice for test assertions and matchers.
- **No unused imports**. Remove imports that aren't referenced.
- **No redundant imports**. Don't import classes from the same package.

#### Naming
- **Constants** (`static final` fields): Either `UPPER_SNAKE_CASE` or `camelCase` are permitted. Logger fields (`private static final Logger logger`) use camelCase by convention.
- **Test methods**: Underscore-separated names are allowed in test files (e.g., `methodName_scenario_expectedResult`).
- Standard Java naming conventions for everything else: `PascalCase` for types, `camelCase` for methods/fields/parameters, `lower.dot.case` for packages.

#### Code Structure
- **Braces required** on all control structures (`if`, `else`, `for`, `while`, etc.), with an exception for single-line statements like `if (x == null) return;`.
- **One statement per line**.
- **Opening brace on same line** as the declaration (K&R style).
- **No empty statements** (stray semicolons).
- **No finalizer methods**.
- **Overloaded methods must be adjacent**.

#### Correctness
- **`equals` and `hashCode` must both be defined** if either is.
- **Switch fall-through must be documented** with a comment.
- **String comparison via `.equals()`**, not `==`.
- **Boolean expressions and returns must not be needlessly complex**.
- **No redundant modifiers** (e.g., `public` on JUnit 5 test methods, `final` on try-with-resources variables).

#### Formatting
Whitespace and layout rules are now enforced by Spotless (Palantir Java Format). See the [Spotless section](#spotless--code-formatting) above.

### Suppressions

Some checks are suppressed where the existing code style is intentionally different:

| File Pattern | Suppressed Check | Reason |
|---|---|---|
| `Log4jLogger.java` | `LeftCurly` | One-liner adapter methods are more readable on a single line |
| `*Test.java` | `MethodName` | Test methods use underscores for readability |

### Fixing Violations

When checkstyle reports a violation, the error message includes the rule name in brackets (e.g., `[AvoidStarImport]`). Common fixes:

| Rule | Fix |
|---|---|
| `AvoidStarImport` | Replace `import java.util.*` with explicit imports for each type used |
| `UnusedImports` | Delete the unused import line |
| `RedundantModifier` | Remove `public` from JUnit 5 test methods/classes, or `final` from try-with-resources variables |
| `NeedBraces` | Add `{ }` around the body of `if`/`else`/`for`/`while` |
| `ConstantName` | Use `UPPER_SNAKE_CASE` or `camelCase` for `static final` fields |

For formatting issues (whitespace, line length, indentation), run `./gradlew spotlessApply` instead — the formatter fixes them automatically.

## PMD

PMD detects common code smells, potential bugs, and dead code. It runs as part of `./gradlew verify` and will fail the build on any violation.

### Configuration

- **Ruleset file**: `config/pmd/ruleset.xml`
- **PMD version**: 7.14.0
- **Severity**: All violations are errors (zero tolerance)
- **Console output**: Enabled (violations shown directly in build output)

### Running PMD

```bash
# Run on main sources only
./gradlew pmdMain

# Run on test sources only
./gradlew pmdTest

# Run everything (tests + checkstyle + PMD)
./gradlew verify
```

Reports are generated at:
- `build/reports/pmd/main.html`
- `build/reports/pmd/test.html`

### What PMD Enforces

#### Best Practices
- **No unused local variables, private fields, or private methods**. Remove dead code.
- **No unused assignments**. Don't assign a value that is never read.
- **No parameter reassignment**. Use a local variable instead of overwriting a parameter.
- **Use foreach loops** where possible instead of indexed for-loops.
- **Use try-with-resources** for `AutoCloseable` resources.

#### Code Style
- **No empty control statements** (empty `if`, `while`, `for`, etc.).
- **No unnecessary returns** at the end of void methods.
- **No unnecessary semicolons**.
- **No unnecessary boxing/unboxing** (e.g., `Integer.valueOf(42)` where autoboxing suffices).

#### Design
- **Simplify ternary expressions** (e.g., `x ? true : false` should be just `x`).
- **No useless overriding methods** that only call `super`.

#### Error Prone
- **No empty catch blocks** (unless the exception variable is named `ignored` or `expected`).
- **No decimal literals in BigDecimal constructors** (use string constructors).
- **Close resources** after use (ResultSet, Connection, etc.).
- **No comparison with NaN** (use `Double.isNaN()` instead).
- **No `.equals(null)`** (use `== null` instead).
- **Override both `equals` and `hashCode`** if either is defined.
- **Return empty collections instead of null**.
- **No unconditional if statements** (always-true or always-false conditions).

#### Performance
- **No `new String("literal")`** — use the string literal directly.
- **No `.toString()` on String objects**.

### Suppressions

For false positives where PMD cannot understand the resource lifecycle, use the `@SuppressWarnings` annotation:

```java
@SuppressWarnings("PMD.CloseResource") // lifecycle managed by caller
public void myMethod() { ... }
```

Current suppressions:

| File | Suppressed Rule | Reason |
|---|---|---|
| `ResultSetDeserializer.java` | `CloseResource` | ResultSet lifecycle is managed by TransactionState |

### Fixing Violations

PMD output includes the rule name (e.g., `UnusedLocalVariable`). Common fixes:

| Rule | Fix |
|---|---|
| `UnusedLocalVariable` | Remove the variable. If the method call has a needed side-effect, call it without assigning the result |
| `UnusedPrivateMethod` | Delete the method |
| `AvoidReassigningParameters` | Introduce a local variable (e.g., `final LocalDate effectiveDate = date != null ? date : LocalDate.now()`) |
| `ForLoopCanBeForeach` | Convert `for (int i = 0; ...)` to `for (Type item : collection)`. If the loop variable isn't needed, consider `String.join` or `Collections.nCopies` |
| `CloseResource` | Wrap in try-with-resources. If the resource lifecycle is managed elsewhere, suppress with `@SuppressWarnings("PMD.CloseResource")` |
| `EmptyCatchBlock` | Add handling, or rename the exception variable to `ignored` or `expected` |
| `ReturnEmptyCollectionRatherThanNull` | Return `Collections.emptyList()`, `Collections.emptySet()`, or `Collections.emptyMap()` instead of `null` |

## SpotBugs

SpotBugs analyzes compiled bytecode to detect bug patterns including null pointer dereferences, resource leaks, incorrect API usage, and performance issues. It runs as part of `./gradlew verify` and will fail the build on any violation.

### Configuration

- **Exclusion filter**: `config/spotbugs/exclusion-filter.xml`
- **SpotBugs version**: 4.9.8 (via Gradle plugin 6.4.8)
- **Effort**: MAX (deepest analysis)
- **Report level**: MEDIUM (reports medium and high confidence findings)
- **Severity**: All violations are errors (zero tolerance)

### Running SpotBugs

```bash
# Run on main sources only
./gradlew spotbugsMain

# Run on test sources only
./gradlew spotbugsTest

# Run everything (tests + checkstyle + PMD + SpotBugs)
./gradlew verify
```

Reports are generated at:
- `build/reports/spotbugs/main.html`
- `build/reports/spotbugs/test.html`

### What SpotBugs Detects

SpotBugs operates on compiled bytecode, so it catches issues that source-level tools miss.

#### Correctness
- **Null pointer dereferences** from unchecked return values (e.g., `Path.getParent()` can return null).
- **Floating point equality** using `==` instead of `Double.compare()` or epsilon comparison.
- **Incorrect API usage** (e.g., `new String(byte[])` without specifying charset).

#### Performance
- **Inefficient map iteration** using `keySet()` + `get()` instead of `entrySet()`.
- **Fields that should be static** (unread instance fields holding constants).

#### Resource Management
- **Unclosed database resources** (Statement, ResultSet, Connection not in try-with-resources).
- **Unsatisfied resource obligations** (resources created but not reliably closed on all paths).

#### Code Quality
- **Ignored return values** from methods with no side effects.
- **Static field writes from instance methods** (threading concerns).

### Exclusions

Exclusions are configured in `config/spotbugs/exclusion-filter.xml`. Each exclusion includes a comment explaining the rationale.

Current global exclusions:

| Bug Pattern | Category | Reason |
|---|---|---|
| `EI_EXPOSE_REP` | MALICIOUS_CODE | CLI application, not a library with untrusted callers |
| `EI_EXPOSE_REP2` | MALICIOUS_CODE | CLI application, not a library with untrusted callers |
| `VA_FORMAT_STRING_USES_NEWLINE` | BAD_PRACTICE | `\n` is intentional; `%n` would produce `\r\n` on Windows |

Current class-specific exclusions:

| Class | Bug Pattern | Reason |
|---|---|---|
| `AbstractDatabaseTest` | `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD` | JUnit `@BeforeEach` sets shared Flyway field for `@AfterEach` cleanup |

### Fixing Violations

SpotBugs HTML reports group findings by category and link to detailed descriptions. Common fixes:

| Bug Pattern | Fix |
|---|---|
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | Store the potentially-null return value in a local variable, add a null check before use |
| `FE_FLOATING_POINT_EQUALITY` | Use `Double.compare(a, b) == 0` instead of `a == b` |
| `DM_DEFAULT_ENCODING` | Add explicit charset parameter: `new String(bytes, StandardCharsets.UTF_8)` |
| `WMI_WRONG_MAP_ITERATOR` | Replace `for (K key : map.keySet()) { map.get(key) }` with `for (Map.Entry<K,V> entry : map.entrySet())` |
| `ODR_OPEN_DATABASE_RESOURCE` | Wrap Statement/ResultSet creation in try-with-resources |
| `RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT` | Either use the return value or remove the no-op method call (e.g., `.orElse(null)` on a discarded Optional) |
| `SS_SHOULD_BE_STATIC` | Change the instance field to `private static final` if it holds a constant |

### Adding New Exclusions

Only exclude confirmed false positives. To add an exclusion:

1. Verify the finding is genuinely a false positive (not a real bug you can fix)
2. Add a `<Match>` element to `config/spotbugs/exclusion-filter.xml`
3. Include a comment explaining why it's excluded
4. Scope the exclusion as narrowly as possible (prefer class-specific over global)

Example:
```xml
<!-- Reason for exclusion -->
<Match>
    <Class name="ca.jonathanfritz.ofxcat.SomeClass"/>
    <Bug pattern="SOME_PATTERN"/>
</Match>
```

## Error Prone

Error Prone is a Google-developed javac compiler plugin that catches bugs at compile time using the full type-resolved AST. Unlike the other static analysis tools, Error Prone runs during compilation itself — it hooks into `javac` and piggybacks on `compileJava` and `compileTestJava`, so it doesn't need a separate Gradle task.

### Configuration

- **Gradle plugin**: `net.ltgt.errorprone` version 4.1.0
- **Core version**: `com.google.errorprone:error_prone_core:2.36.0`
- **Mode**: ERROR-level checks only (`disableAllWarnings = true`)
- **Severity**: Errors fail the build (`allErrorsAsWarnings = false`)

### Running Error Prone

Error Prone runs automatically during compilation:

```bash
# Runs Error Prone as part of compilation
./gradlew compileJava compileTestJava

# Also runs as part of verify (via test → compileJava/compileTestJava)
./gradlew verify
```

There is no separate `errorprone` task — violations appear as compiler errors in the `compileJava` or `compileTestJava` output.

### What Error Prone Detects

Error Prone catches issues that source-level tools (Checkstyle, PMD) and bytecode-level tools (SpotBugs) miss, because it has access to the full type-resolved AST during compilation.

#### Optional/Stream Misuse
- **`OptionalOfRedundantMethod`** — `Optional.of(x).orElse(y)` where `Optional.of()` never returns empty, making the fallback dead code.
- **`ReturnValueIgnored`** — discarding the return value of methods with no side effects (e.g., `Optional.orElseThrow()` without using the result).

#### Type Safety
- **`MissingCasesInEnumSwitch`** — switch statements that don't cover all enum values.
- **`IncompatibleArgumentType`** — passing the wrong type to a generic method (e.g., calling `Map<String, Integer>.get(42)`).

#### Correctness
- **`InvalidPatternSyntax`** — invalid regex passed to `Pattern.compile()` or `String.matches()`.
- **`FormatString`** — format string mismatches in `String.format()` or `printf()`.
- **`DeadException`** — creating an exception without throwing it (e.g., `new IllegalArgumentException("msg");` instead of `throw new ...`).
- **`SelfEquals`** — `x.equals(x)` which is always true.

### Suppressions

For confirmed false positives, suppress with the `@SuppressWarnings` annotation using the check name:

```java
@SuppressWarnings("ReturnValueIgnored") // reason for suppression
public void myMethod() { ... }
```

Current suppressions:

| File | Suppressed Check | Reason |
|---|---|---|
| `DatabaseTransactionErrorHandlingTest.java` | `ReturnValueIgnored` | DAO insert is test setup; `orElseThrow()` asserts success |
| `ReportingWorkflowIntegrationTest.java` (3 methods) | `ReturnValueIgnored` | DAO inserts are test setup; `orElseThrow()` asserts success |

### Fixing Violations

Error Prone errors appear in compiler output with the check name in brackets (e.g., `[OptionalOfRedundantMethod]`). Each error includes a link to the detailed explanation at `https://errorprone.info/bugpattern/<CheckName>`.

| Check | Fix |
|---|---|
| `OptionalOfRedundantMethod` | Remove the redundant `Optional.of()` wrapper; use the inner Optional directly or rewrite with `if`/`else` |
| `ReturnValueIgnored` | Use the return value, assign to a variable, or suppress if the call is for its side effect |
| `DeadException` | Add `throw` before the exception constructor |
| `InvalidPatternSyntax` | Fix the regex string |
| `FormatString` | Match format specifiers to argument types and count |

## JaCoCo — Code Coverage

JaCoCo measures test coverage of production code. It runs automatically with the test suite and enforces a minimum coverage threshold as part of `./gradlew verify`.

### Configuration

- **Plugin**: Built-in Gradle `jacoco` plugin
- **Scope**: Main source set only (`src/main/java`). Test code is not measured.
- **Minimum line coverage**: 80%
- **Reports**: HTML + XML

### Running JaCoCo

```bash
# Generate coverage report (runs tests first)
./gradlew jacocoTestReport

# Check coverage meets minimum threshold (runs tests first)
./gradlew jacocoTestCoverageVerification

# Run everything (tests + static analysis + coverage)
./gradlew verify
```

Reports are generated at:
- `build/reports/jacoco/test/html/index.html` (HTML, browse per-class and per-method coverage)
- `build/reports/jacoco/test/jacocoTestReport.xml` (XML, machine-parseable)

### Coverage Threshold

The build enforces a minimum 80% line coverage across production code (excluding infrastructure classes listed below). If coverage drops below this threshold, `./gradlew verify` will fail with a message like:

```
Rule violated for bundle ofxcat: lines covered ratio is 0.79, but expected minimum is 0.80
```

To fix: add tests that exercise the uncovered lines. Use the HTML report to identify which classes and methods lack coverage.

### What JaCoCo Measures

JaCoCo tracks several coverage metrics. The enforced metric is **line coverage** — the percentage of executable lines in `src/main/java` that are exercised by at least one test.

The HTML report also shows:
- **Instruction coverage** — bytecode-level coverage
- **Branch coverage** — percentage of `if`/`switch` branches taken
- **Complexity coverage** — cyclomatic complexity paths exercised
- **Method coverage** — percentage of methods called by at least one test
- **Class coverage** — percentage of classes loaded during testing

### Coverage Exclusions

JaCoCo only instruments main source set classes by default. Test code, resources, and documentation are never included.

The following infrastructure classes are excluded from coverage measurement because they are thin wrappers around third-party libraries or Guice DI modules that cannot be meaningfully unit tested:

| Class | Reason |
|---|---|
| `CLI.java` | Wrapper around TextIO library for interactive CLI prompts |
| `CLIModule.java` | Guice module that binds CLI dependencies |
| `TextIOWrapper.java` | TextIO library adapter |
| `MatchingModule.java` | Guice module that loads keyword rules from filesystem |
| `Log4jLogger.java` | Adapter bridging Log4j to TextIO's logging interface |

To add a new exclusion, add the class path to the `jacocoExcludes` list in `build.gradle`.

## Verify Task

The `verify` Gradle task is the single command that checks whether a change is ready to commit:

```bash
./gradlew verify
```

It runs:
1. `compileJava` / `compileTestJava` - compilation with Error Prone checks (runs automatically as a prerequisite of `test`)
2. `spotlessCheck` - code formatting verification (Palantir Java Format)
3. `checkstyleMain` - style check on production code
4. `checkstyleTest` - style check on test code
5. `pmdMain` - bug/smell detection on production code
6. `pmdTest` - bug/smell detection on test code
7. `spotbugsMain` - bug pattern detection on production bytecode
8. `spotbugsTest` - bug pattern detection on test bytecode
9. `test` - the full test suite
10. `jacocoTestReport` - generate coverage report
11. `jacocoTestCoverageVerification` - enforce minimum 80% line coverage

If `verify` passes, the code is clean.

## Future Static Analysis Tools

See `docs/plans/AgentToolingPlan.md` for planned additions including custom architectural lint rules.
