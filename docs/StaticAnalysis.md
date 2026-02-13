# Static Analysis

This document describes the static analysis tools configured for this project, what they enforce, and how to work with them.

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
- **No tab characters**. Use spaces for indentation.
- **Line length**: 200 characters maximum. Import and package lines are exempt.
- **Files must end with a newline**.
- **Whitespace required** around operators, after commas, after keywords (`if`, `for`, `try`, etc.).

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
| `LineLength` | Break the line. For builder chains, wrap after each `.method()` call. For long parameter lists, put each parameter on its own line |
| `WhitespaceAfter` | Add a space after commas, keywords, etc. |
| `ConstantName` | Use `UPPER_SNAKE_CASE` or `camelCase` for `static final` fields |

## Verify Task

The `verify` Gradle task is the single command that checks whether a change is ready to commit:

```bash
./gradlew verify
```

It runs:
1. `checkstyleMain` - style check on production code
2. `checkstyleTest` - style check on test code
3. `test` - the full test suite

If `verify` passes, the code is clean.

## Future Static Analysis Tools

See `docs/AgentToolingPlan.md` for planned additions including SpotBugs, JaCoCo, Error Prone, and custom architectural lint rules.
