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

## What's Been Built

The following tooling is fully implemented and enforced on every commit via `./gradlew verify` and a pre-commit hook.

### Static Analysis Pipeline

All tools are documented in `docs/StaticAnalysis.md`.

| Tool | What it does | Added in |
|------|-------------|----------|
| **Spotless** (Palantir Java Format) | Deterministic code formatting — 4-space indent, 120-char lines. `spotlessApply` fixes, `spotlessCheck` gates. | Phase 1 |
| **Checkstyle** | Semantic style rules: naming, imports, braces, coding correctness. Whitespace rules removed (handled by Spotless). | Phase 1 |
| **PMD** | Code smells, dead code, design issues, error-prone patterns. | Phase 1 |
| **SpotBugs** | Bug patterns in compiled bytecode: null derefs, resource leaks, incorrect API usage. | Phase 1 |
| **Error Prone** | Compile-time bug detection via javac plugin: type safety, Optional misuse, format strings. | Phase 1 |
| **JaCoCo** | Code coverage with 80% minimum line coverage enforcement. | Phase 1 |

### Build Infrastructure

| Tool | What it does |
|------|-------------|
| **`./gradlew verify`** | Single command: spotlessCheck + checkstyle + PMD + SpotBugs + Error Prone + tests + coverage |
| **Pre-commit hook** | Runs `./gradlew verify` before every commit. Lives in `.githooks/pre-commit`. |
| **Dependabot** | Weekly dependency vulnerability scanning via GitHub. Config in `.github/dependabot.yml`. |

### Architecture Enforcement

| Tool | What it does |
|------|-------------|
| **ArchUnit** (`LayeredArchitectureTest`) | 6 rules enforcing the layered architecture as JUnit tests. |

ArchUnit enforces:
1. Full layer dependency graph (which layers may access which)
2. DTO isolation (pure data, no imports from other layers)
3. IO isolation (self-contained OFX parsing)
4. DAO protection (no upward dependencies to services/CLI)
5. No circular dependencies between packages
6. Safety net: new packages must be registered before use

The layer rules are also documented in CLAUDE.md so the agent knows the architecture before writing code.

### Documentation

| Document | Purpose |
|----------|---------|
| **CLAUDE.md** | Entry point: build commands, architecture overview with layer rules, pointers to deeper docs |
| **docs/GenAIGuide.md** | Collaboration rules, coding standards, TDD process |
| **docs/CodebaseOverview.md** | Comprehensive technical reference |
| **docs/StaticAnalysis.md** | All static analysis tools: config, rules, how to fix violations |

---

## What's Left

### Remaining Low-Hanging Fruit

These are straightforward to implement and fill gaps in the current tooling.

#### GitHub Actions — Automated Build and Test

**Status:** Not started
**Effort:** Small
**Why:** Every push and PR should be automatically verified. The pre-commit hook only gates local commits — it doesn't catch platform-specific issues, forgotten files, or force-pushed branches that bypass hooks.

**What to do:**
- Create `.github/workflows/build.yml` that runs `./gradlew verify` on push and PR
- Upload test and coverage reports as build artifacts
- Add a JaCoCo coverage comment action on PRs (e.g., `madrapps/jacoco-report`)
- Add status badge to README

**Agent benefit:** PRs get a green/red signal independent of what the agent claims locally.

#### Compiler Warnings as Errors

**Status:** Done. Added `-Xlint:all -Werror` to `JavaCompile` options. Fixed 13 warnings: deprecated `StringUtils.startsWith()` → `String.startsWith()`, missing `serialVersionUID` on exceptions, deprecated `Option.builder().build()` → `.get()`, `this-escape` via `final` class, deprecated `RandomUtils` → `ThreadLocalRandom`.

#### Test Tagging and Targeted Execution

**Status:** Done. Tagged `AbstractDatabaseTest` with `@Tag("database")` (inherited by all 19 subclasses) and 3 integration tests with `@Tag("integration")`. Added three Gradle tasks: `unitTest` (229 tests, excludes database/integration), `databaseTest` (148 tests, database tests excluding integration), `integrationTest` (14 tests). All three partition the full 391-test suite exactly.

#### Database Migration Validation

**Status:** Partially addressed. `AbstractDatabaseTest` already runs Flyway against an in-memory SQLite for every database test, which validates migrations implicitly. A dedicated task would make this explicit.
**Effort:** Tiny

**What to do:**
- Add a Gradle task that runs Flyway `migrate` + `validate` against a fresh in-memory SQLite
- Consider adding to `verify` dependencies

**Agent benefit:** Fast, explicit feedback on migration validity without running the full test suite.

---

### Harder Problems

These require more design work and may not have obvious solutions.

#### Application Runnability and Inspection

**Status:** Not started
**Effort:** Medium-Large
**Why:** The agent can verify code correctness via tests, but cannot run the application and inspect its behavior. For a CLI app that prompts for user input, this is hard — the agent can't interact with a REPL-style interface.

**Challenges:**
- The application requires interactive CLI input (TextIO library) for most operations (import, categorization). The agent cannot drive an interactive prompt.
- Import requires real OFX files and writes to `~/.ofxcat/ofxcat.db`. Running the app modifies persistent state.
- The `help` command works non-interactively, but most useful commands don't.

**Possible approaches:**
- Add a `--dry-run` flag to import that validates without writing to the database
- Add a `./gradlew run --args="help"` task so the agent doesn't need to know the JAR path
- Add non-interactive batch modes for common operations (e.g., `--yes` to auto-accept prompts)
- Document sample commands and expected output in a `docs/CLIReference.md`
- Consider whether integration tests (which already exercise the full stack) adequately cover this need

**Open question:** Is this actually needed, or do the 391 tests (including integration tests that exercise import/categorization/reporting workflows) already give sufficient confidence? The cost-benefit may not justify the effort.

#### Structured Log Access

**Status:** Not started
**Effort:** Small-Medium
**Why:** When something goes wrong at runtime, the agent needs to read logs and correlate them to operations. Currently logs go to `~/.ofxcat/ofxcat.log` and console, but there's no structured way to query or filter them.

**Possible approaches:**
- Add a Gradle task that tails/filters `~/.ofxcat/ofxcat.log` by level or logger name
- Add JSON-format log output option for machine parsing (Log4j2 `JsonLayout`)
- Document log file locations and common patterns in `docs/Observability.md`
- Add test name context to log output via MDC

**Open question:** How often does the agent actually need runtime logs vs. test output? Test failures already include full stack traces. This may be more valuable for production debugging than agent-assisted development.

#### Mutation Testing (PIT)

**Status:** Not started
**Effort:** Medium
**Why:** Coverage tells you what lines are exercised, not whether tests would catch a bug. Mutation testing introduces bugs and verifies tests catch them.

**Challenges:**
- PIT is slow (minutes, not seconds) — can't run on every commit
- Needs careful scoping to avoid overwhelming output
- Diminishing returns for well-tested code

**What to do:**
- Add the PIT Gradle plugin
- Run periodically (weekly or on-demand), not as part of `verify`
- Focus on critical business logic: categorization, transfer matching, balance calculations

#### Quality Tracking ("Garbage Collection")

**Status:** Not started
**Effort:** Small-Medium
**Why:** Agents replicate existing patterns, including bad ones. Quality silently degrades between sessions without periodic sweeps.

**What to do:**
- Create a `docs/QualityGrades.md` that tracks per-package health (coverage, known issues)
- Periodic agent-driven sweeps for: dead code, coverage regressions, stale docs, old TODOs
- Targeted fix-up commits, not sweeping rewrites

---

## Implementation Priority (remaining items)

| Item | Effort | Impact | Status |
|------|--------|--------|--------|
| GitHub Actions CI | Small | High | Not started |
| Compiler warnings as errors | Tiny | Medium | Done |
| Test tagging | Small | Medium | Done |
| Migration validation task | Tiny | Low | Partially addressed by existing tests |
| Application runnability | Medium-Large | Uncertain | Needs design |
| Structured log access | Small-Medium | Low-Medium | Needs design |
| Mutation testing | Medium | Low | Not started |
| Quality tracking | Small-Medium | Medium | Not started |

---

## How to Keep This Plan Alive

This document is itself subject to the "map, not manual" principle. As items are implemented:
1. Update status here
2. Document the tool in a dedicated file under `docs/` (not in CLAUDE.md directly)
3. Add a one-line pointer from CLAUDE.md to the new doc

If an item turns out to be wrong or unnecessary after investigation, note why and remove it. A stale plan is worse than no plan.
