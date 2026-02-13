# Agent Tooling Plan for ofxcat

**Goal:** Build the scaffolding, feedback loops, and guardrails that let an AI coding agent work on this codebase with increasing confidence and decreasing need for human intervention.

**Inspiration:** [OpenAI's Harness Engineering](https://openai.com/index/harness-engineering/) describes a shift from writing code to *designing environments* that help agents produce reliable work. Their core insight: **discipline shows up in the scaffolding, not the code.** The tooling, abstractions, and feedback loops that keep the codebase coherent matter more than the code itself.

Their specific lessons that apply here:
- **"Give the agent a map, not a 1,000-page instruction manual."** A monolithic instruction file crowds out the task. Use progressive disclosure: a small entry point that points to deeper sources of truth.
- **Enforce invariants, not implementations.** Care about boundaries, correctness, and reproducibility. Within those boundaries, allow freedom in how solutions are expressed.
- **Custom lint rules with remediation instructions.** Error messages that tell the agent *how to fix* the issue, not just that a violation occurred.
- **Application legibility.** Make logs, output, and application state directly inspectable by the agent.
- **"Garbage collection."** Continuous quality sweeps that catch drift, not just gates at commit time.
- **In-repo knowledge as system of record.** From the agent's point of view, anything it can't access in-context effectively doesn't exist.

This plan applies those ideas to a concrete Java/Gradle/JUnit project.

---

## Current State

### What exists today
- Gradle build with JUnit 5, shadow JAR, enhanced test logging
- 43 test classes with `AbstractDatabaseTest` base class and `TestUtils` helpers
- Flyway-managed SQLite schema (13 migrations)
- Log4j2 logging to file and console (test)
- Documentation: CLAUDE.md (architecture map), GenAIGuide.md (collaboration rules), CodebaseOverview.md (comprehensive reference)

### What's missing
- No static analysis (Checkstyle, SpotBugs, PMD, Error Prone)
- No code coverage measurement
- No CI/CD pipeline (no GitHub Actions)
- No pre-commit hooks
- No automated formatting enforcement
- No dependency vulnerability scanning
- No way for the agent to verify "did I break anything?" beyond running tests
- No structured way for the agent to run the application and inspect its behavior
- No mechanical enforcement of the layered architecture

---

## Proposed Tooling (Prioritized)

### Phase 0: Documentation Structure and Agent Legibility

The Harness Engineering team learned the hard way that one big `AGENTS.md` fails: context is scarce, too much guidance becomes non-guidance, and it rots instantly. Instead, they treat it as a **table of contents** pointing to deeper sources of truth, with progressive disclosure.

This project already has decent bones here (CLAUDE.md is concise, GenAIGuide.md and CodebaseOverview.md hold the detail). But there are gaps.

#### 0.1 Restructure Documentation for Progressive Disclosure

**Why:** The agent needs to quickly find relevant context without loading everything into its working memory. Right now, CLAUDE.md points to two docs files, but there's no index of what lives where across the repo.

**What to do:**
- Keep CLAUDE.md short (~100 lines) as the map. It should answer: what is this project, how do I build/test/run it, and where do I look for deeper context?
- Add a `docs/index.md` that catalogs all documentation with one-line descriptions
- When new tooling is added, document it in a dedicated file (e.g., `docs/StaticAnalysis.md`) and link from CLAUDE.md, rather than expanding CLAUDE.md itself

**Agent benefit:** The agent starts each session with a small, stable entry point and knows exactly where to look for deeper context on any topic.

#### 0.2 Make the Application Runnable and Inspectable

**Why:** The Harness Engineering team made their app "bootable per worktree" so the agent could launch it, drive it, and inspect its behavior. For a CLI app like ofxcat, this means the agent should be able to run commands and parse the output.

**What to do:**
- Add a Gradle task (e.g., `./gradlew run --args="help"`) that runs the application with arguments, so the agent doesn't need to know the JAR path
- Ensure all CLI output is parseable (consistent formatting, exit codes that reflect success/failure)
- Document sample commands for each mode (import, get accounts, get categories, get transactions, combine) in a `docs/CLIReference.md` with example invocations and expected output
- Consider adding a `--dry-run` flag to import that validates an OFX file without writing to the database, so the agent can test import behavior safely

**Agent benefit:** The agent can exercise the application directly and reason about its behavior, not just its code. This is critical for debugging and validating fixes.

#### 0.3 Structured Log Access

**Why:** The Harness Engineering team exposed logs, metrics, and traces to agents via queryable APIs. For ofxcat, the equivalent is making log output during tests and application runs easily accessible and parseable.

**What to do:**
- Ensure test log output includes the test name as context (Log4j2's `%X` for MDC or thread name)
- Add a Gradle task or script that tails/filters `~/.ofxcat/ofxcat.log` by level or logger name
- Consider adding JSON-format log output option for machine parsing (Log4j2 `JsonLayout`)
- Document log file locations and how to interpret common log patterns in `docs/Observability.md`

**Agent benefit:** When something goes wrong, the agent can read logs and correlate them to specific operations, rather than guessing from stack traces alone.

---

### Phase 1: Fast Feedback Loops (highest impact, lowest effort)

These give the agent immediate signals about whether a change is correct.

#### 1.1 Checkstyle — Consistent Code Style

**Why:** The agent needs a mechanical way to verify it matched the project's style conventions. Right now the only check is "match surrounding code" (GenAIGuide.md), which is subjective.

**What to do:**
- Add the Checkstyle Gradle plugin
- Create a `config/checkstyle/checkstyle.xml` based on a Google or Sun baseline, then tune it to match the existing code style
- Configure `checkstyleMain` and `checkstyleTest` tasks
- Set severity to `warning` initially so the build doesn't break on existing violations, then ratchet up over time

**Agent benefit:** After making changes, run `./gradlew checkstyleMain` and get a concrete pass/fail. No ambiguity about style.

#### 1.2 SpotBugs — Bug Pattern Detection

**Why:** Catches null pointer risks, resource leaks, incorrect API usage, and concurrency issues that are hard to spot in code review.

**What to do:**
- Add the SpotBugs Gradle plugin
- Start with `effort = 'max'` and `reportLevel = 'medium'`
- Exclude known false positives in a filter file as they surface
- Generate HTML reports for easy reading

**Agent benefit:** After writing code, run `./gradlew spotbugsMain` to catch bug patterns before a human even looks at it.

#### 1.3 JaCoCo — Code Coverage

**Why:** The agent needs to know whether its tests actually exercise the code it changed. Coverage isn't a goal in itself (GenAIGuide says as much), but it's a crucial signal for identifying untested paths.

**What to do:**
- Add the JaCoCo Gradle plugin
- Configure `jacocoTestReport` to generate HTML + XML reports
- Add a `jacocoTestCoverageVerification` task with a low initial minimum (e.g., 50% line coverage) that can ratchet up
- Configure coverage for the main source set only (not test code)

**Agent benefit:** After writing tests, run `./gradlew jacocoTestReport` and read the report to verify coverage of the changed code. The XML report is machine-parseable.

#### 1.4 Compiler Warnings as Errors

**Why:** Java compiler warnings (unchecked casts, deprecated API usage, etc.) are free bug detection.

**What to do:**
- Add to `build.gradle`:
  ```groovy
  tasks.withType(JavaCompile) {
      options.compilerArgs += ['-Xlint:all', '-Werror']
  }
  ```
- Fix any existing warnings first (there may be a handful)

**Agent benefit:** `./gradlew compileJava` becomes a strict correctness gate. If it passes, the code at least has no compiler-level issues.

---

### Phase 2: Guardrails (prevent mistakes before they land)

#### 2.1 Pre-commit Hook — Build Gate

**Why:** The GenAIGuide already says "NEVER SKIP, EVADE OR DISABLE A PRE-COMMIT HOOK." Having one enforces that code compiles and tests pass before every commit.

**What to do:**
- Create a `.githooks/pre-commit` script that runs:
  1. `./gradlew compileJava compileTestJava` (fast compilation check)
  2. `./gradlew test` (full test suite)
  3. `./gradlew checkstyleMain` (style check)
- Configure git to use the hooks directory: `git config core.hooksPath .githooks`
- Document setup in CLAUDE.md

**Agent benefit:** A commit that breaks compilation or tests is mechanically prevented. The agent gets immediate feedback instead of discovering the issue later.

**Consideration:** The full test suite adds time to every commit. An alternative is running only `compileJava` and `checkstyleMain` in pre-commit, and the full test suite in a pre-push hook or CI.

#### 2.2 Gradle Verification Task — One Command to Rule Them All

**Why:** The agent shouldn't need to remember which checks to run. One command should run everything.

**What to do:**
- Add a `verify` task to `build.gradle` that depends on:
  - `compileJava`
  - `test`
  - `checkstyleMain`
  - `spotbugsMain`
  - `jacocoTestReport`
- Document it in CLAUDE.md as the standard "am I done?" command

**Agent benefit:** `./gradlew verify` is the single command that answers "is this change ready for review?"

---

### Phase 3: Deeper Analysis and Custom Rules

#### 3.1 Custom Lint Rules with Remediation Instructions

**Why:** This is one of the most powerful ideas from the Harness Engineering article. Off-the-shelf linters say "violation at line 42." Custom rules say "violation at line 42 — DAO classes must not import from the service package. Move this logic to a service class and inject the DAO there." The error message itself becomes agent instructions.

**What to do:**
- Write custom Checkstyle checks (or use Checkstyle's `RegexpSingleline` and `ImportControl` modules) that encode this project's specific invariants:
  - **Layer violations:** DAO classes must not import from `service` or `cli` packages
  - **DTO immutability:** DTOs in `datastore.dto` must not have setter methods
  - **Test naming:** Test classes must end with `Test` and extend `AbstractDatabaseTest` if they use database access
  - **Resource cleanup:** `Connection` and `PreparedStatement` must be used in try-with-resources
- For each rule, write the error message as a remediation instruction the agent can follow
- Consider ArchUnit (see 5.2) for rules that are easier to express as architecture tests

**Agent benefit:** When the agent makes a structural mistake, the error output tells it exactly what to do. This is the "inject remediation instructions into agent context" pattern from the article.

#### 3.2 Error Prone — Compile-time Bug Detection

**Why:** Google's Error Prone catches bugs at compile time that SpotBugs catches at bytecode level. They're complementary. Error Prone has better detection for common Java mistakes (comparison of incompatible types, missing `@Override`, etc.).

**What to do:**
- Add the Error Prone compiler plugin
- Start with default checks enabled
- Selectively disable checks that produce false positives for this codebase

**Agent benefit:** Bug patterns caught at compile time, before tests even run. Fastest possible feedback.

#### 3.3 Dependency Vulnerability Scanning

**Why:** The project uses 15+ transitive dependencies. A known vulnerability in any of them is a security risk.

**What to do:**
- Add the OWASP Dependency-Check Gradle plugin
- Configure to run on `./gradlew dependencyCheckAnalyze`
- Set failure threshold at CVSS 7+ (high severity)

**Agent benefit:** When updating dependencies or adding new ones, the agent can verify no known vulnerabilities are introduced.

#### 3.4 PMD — Code Smell Detection

**Why:** PMD catches design-level issues: overly complex methods, empty catch blocks, unused variables, copy-paste code. These align well with the GenAIGuide's emphasis on simplicity and readability.

**What to do:**
- Add the PMD Gradle plugin
- Start with a conservative ruleset (just `basic`, `unusedcode`, `empty`)
- Expand the ruleset as existing violations are fixed

**Agent benefit:** Mechanical enforcement of "keep it simple." The agent can verify it didn't introduce unnecessary complexity.

---

### Phase 4: CI/CD Pipeline (enables autonomous workflow)

#### 4.1 GitHub Actions — Automated Build and Test

**Why:** Every push and PR should be automatically verified. This is the safety net that catches anything the pre-commit hook missed (e.g., platform-specific issues, forgotten files).

**What to do:**
- Create `.github/workflows/build.yml`:
  ```yaml
  name: Build and Test
  on: [push, pull_request]
  jobs:
    build:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with:
            java-version: '21'
            distribution: 'temurin'
        - run: ./gradlew verify
        - uses: actions/upload-artifact@v4
          if: always()
          with:
            name: test-reports
            path: build/reports/
  ```
- Add status badge to README

**Agent benefit:** The agent (or the human reviewing its PR) gets a clear green/red signal on GitHub. Reports are archived as build artifacts.

#### 4.2 Coverage Reporting in PRs

**Why:** When the agent opens a PR, the reviewer should see at a glance whether test coverage improved or regressed.

**What to do:**
- Add a JaCoCo report step to the GitHub Actions workflow
- Use a coverage comment action (e.g., `madrapps/jacoco-report`) to post coverage deltas on PRs

**Agent benefit:** Objective evidence that the agent's tests are meaningful.

---

### Phase 5: Advanced Capabilities (future, builds on earlier phases)

#### 5.1 Test Impact Analysis and Tagging

**Why:** Running the full test suite for every small change is slow. The agent should be able to run just the tests affected by its changes.

**What to do:**
- Investigate Gradle's build cache and incremental test support
- Tag tests with JUnit 5 `@Tag` annotations (e.g., `@Tag("unit")`, `@Tag("integration")`, `@Tag("dao")`)
- Add Gradle tasks for subsets: `./gradlew unitTest`, `./gradlew integrationTest`
- Consider `gradle-test-logger-plugin` for better formatted test output

**Agent benefit:** Faster iteration cycles. The agent can make a DAO change and run `./gradlew test --tests "*DaoTest"` for feedback in seconds, then run the full suite before committing.

#### 5.2 Architectural Enforcement via ArchUnit

**Why:** The layered architecture (CLI -> Service -> DAO -> DB) is documented but not enforced. ArchUnit can make it mechanical. The Harness Engineering team built their entire project around "strictly validated dependency directions and a limited set of permissible edges."

**What to do:**
- Add ArchUnit dependency
- Write tests that enforce:
  - DAOs don't depend on services
  - Services don't depend on CLI
  - DTOs have no dependencies on other layers
  - All public DAO methods take/return DTOs, not raw SQL types
  - Matching package doesn't depend on CLI or datastore packages directly

**Agent benefit:** When adding new code, the agent gets a test failure if it violates the architecture. The error message explains the violation and the correct dependency direction.

#### 5.3 Mutation Testing (PIT)

**Why:** Code coverage tells you what lines are exercised, but not whether the tests would catch a bug. Mutation testing actually introduces bugs and verifies tests catch them.

**What to do:**
- Add the PIT (pitest) Gradle plugin
- Run periodically (not on every commit — it's slow)
- Focus on critical business logic: categorization, transfer matching, balance calculations

**Agent benefit:** The agent can verify its tests are actually effective, not just achieving line coverage.

#### 5.4 Database Migration Validation

**Why:** The agent might need to write new Flyway migrations. Getting migration order or SQL syntax wrong can corrupt the database.

**What to do:**
- Add a dedicated Gradle task that runs Flyway `migrate` against a fresh in-memory SQLite, then runs `validate`
- Ensure this runs as part of the `verify` task
- Consider adding a Flyway `repair` step in CI to catch broken migration checksums

**Agent benefit:** Immediate feedback on whether a new migration is valid, without risking the real database.

#### 5.5 Quality Tracking ("Garbage Collection")

**Why:** The Harness Engineering team found that agents replicate existing patterns — including bad ones. Over time this leads to drift. Their solution: recurring quality sweeps that scan for deviations and open targeted fixes. "Technical debt is like a high-interest loan: it's almost always better to pay it down continuously."

**What to do:**
- Create a `docs/QualityGrades.md` that tracks the health of each package/layer (test coverage, known issues, lint violations)
- Periodically (e.g., monthly) have the agent scan for:
  - Dead code (unused methods, unreachable branches)
  - Test coverage regressions
  - Stale documentation that doesn't match code behavior
  - TODO/FIXME items that have been open for too long
- The agent proposes targeted fix-up commits, not sweeping rewrites

**Agent benefit:** Quality doesn't silently degrade between sessions. The agent can always start from a clean baseline.

---

## Implementation Priority

| Phase | Item | Effort | Impact | Dependencies |
|-------|------|--------|--------|-------------|
| 0 | Documentation restructure | Small | High | None |
| 0 | App runnable via Gradle | Tiny | High | None |
| 0 | Structured log access | Small | Medium | None |
| 1 | Checkstyle | Small | High | None |
| 1 | SpotBugs | Small | High | None |
| 1 | JaCoCo | Small | High | None |
| 1 | Compiler warnings | Tiny | Medium | None |
| 2 | Pre-commit hook | Small | High | Phase 1 items |
| 2 | Verify task | Tiny | High | Phase 1 items |
| 3 | Custom lint rules | Medium | High | Checkstyle |
| 3 | Error Prone | Medium | Medium | None |
| 3 | OWASP Dep Check | Small | Medium | None |
| 3 | PMD | Small | Medium | None |
| 4 | GitHub Actions | Medium | High | Phase 2 |
| 4 | Coverage in PRs | Small | Medium | JaCoCo + GH Actions |
| 5 | Test tagging | Small | Medium | None |
| 5 | ArchUnit | Medium | Medium | None |
| 5 | Mutation testing | Medium | Low | JaCoCo |
| 5 | Migration validation | Small | Medium | None |
| 5 | Quality tracking | Small | Medium | Phase 1-3 tools |

---

## What This Enables (Graduated Responsibility)

**Phase 0-1 complete — the agent can verify its own work:**
- Make a code change, run `./gradlew verify`, get concrete pass/fail on compilation, tests, style, and bug patterns
- Run the application and inspect its behavior
- Read logs to diagnose issues

**Phase 2-3 complete — the agent can be trusted with routine tasks:**
- Pre-commit hooks prevent broken commits mechanically
- Custom lint rules catch structural mistakes and tell the agent how to fix them
- Vulnerability scanning prevents unsafe dependency changes

**Phase 4 complete — the agent's work is independently verified:**
- PRs are automatically verified on GitHub
- Coverage deltas are visible to reviewers
- Every push gets a green/red signal independent of what the agent claims

**Phase 5 complete — the agent maintains long-term quality:**
- Architecture violations are caught as test failures
- Tests are provably effective via mutation testing
- Quality tracking prevents silent degradation between sessions
- The agent can run targeted test subsets for fast iteration

---

## How to Keep This Plan Alive

This document is itself subject to the "map, not manual" principle. As items are implemented:
1. Mark them done here
2. Document the tool in a dedicated file under `docs/` (not in CLAUDE.md directly)
3. Add a one-line pointer from CLAUDE.md to the new doc
4. Update `docs/index.md`

If an item turns out to be wrong or unnecessary after investigation, note why and remove it. A stale plan is worse than no plan.
