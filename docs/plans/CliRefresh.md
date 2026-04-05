# CLI Refresh

## Goals

1. **Always run in the spawned terminal** — no new window, no Swing fallback
2. **Colors** — ANSI styling that degrades gracefully when piped
3. **Capturable output** — plain text when stdout is not a TTY, so Claude and shell pipelines can consume it cleanly
4. **Modern feel** — typography, layout, and feedback that makes the CLI feel like a tool you want to use

## Problem with textio

`TextIoFactory.getTextIO()` uses `ServiceLoader` to pick the "best" terminal at runtime. In some environments (IntelliJ, Windows Terminal piped to clipboard, etc.) it picks `SwingTextTerminal`, which spawns a separate window. This is a fundamental design problem with the library, not a configuration issue.

textio also has no real ANSI color support — the `textio.properties` system is a workaround, not a solution — and it does not detect pipes, so output is always polluted with ANSI escapes even when captured.

## Solution: JLine 3

[JLine 3](https://github.com/jline/jline3) is the terminal abstraction used by JShell, Spring Shell, and most modern JVM CLI frameworks. It:

- Always uses the system terminal via `TerminalBuilder.builder().system(true).build()` — no auto-detect fallback to Swing
- Detects TTY vs. pipe automatically; when stdout is piped, ANSI codes are stripped
- Has first-class ANSI color/style support via `AttributedString` and `AttributedStyle`
- Supports `LineReader` for interactive input with history, completion, and proper terminal handling
- Works on Windows via a JNI backend (`jline-terminal-jni`) that talks to the Windows Console API

### Dependencies

```groovy
// Replace org.beryx:text-io with:
implementation 'org.jline:jline:3.29.0'
implementation 'org.jline:jline-terminal-jni:3.29.0'  // native Windows Console API
```

The `jline-terminal-jni` artifact ships a native library that JLine extracts at runtime. No native install is required on the user's machine.

## Architecture Changes

### CLIModule

Provide `Terminal` and `LineReader` instead of `TextIO`:

```java
@Provides
Terminal provideTerminal() throws IOException {
    return TerminalBuilder.builder().system(true).build();
}

@Provides
LineReader provideLineReader(Terminal terminal) {
    return LineReaderBuilder.builder().terminal(terminal).build();
}
```

### CLI

Keep the same public API (same method signatures). Replace all TextIO internals with JLine equivalents. The `Terminal` replaces `TextIO` and the `LineReader` replaces the `TextIO` input readers.

`TextIOWrapper` is deleted — it existed only to make `TextIO` injectable for testing. JLine's constructor is clean.

### Testing

Test doubles currently subclass `CLI` with `super(null, null)` and override the methods they test. After the refactor `CLI` will have `CLI(Terminal terminal, LineReader reader)`, so test doubles use `super(null, null)` as before. This still works because the overriding subclass never calls the overridden methods that touch the terminal.

## Visual Design

### Colour Palette

| Role | Style |
|---|---|
| Section headers / labels | Cyan, bold |
| Primary values | White, bold |
| Positive amounts | Blue |
| Negative amounts | Red |
| Prompts | Yellow |
| Secondary info (fit IDs, metadata) | White, dim |
| Errors | Red, bold |
| Success / auto-categorized | Green |

### Welcome Banner

Render the existing ASCII art in cyan. No change to the art itself.

### Transaction Display

Right-align labels (12 chars), colour values:

```
   Trans. Id  T201401
        Date  2024-01-15
        Type  DEBIT
      Amount  -$52.49          ← red
 Description  GROCERIES PLUS
     Account  RBC Chequing
     Balance  $1,247.83        ← blue
```

### Progress Bar

Unicode block chars, overwrites in place when TTY:

```
Importing  ████████████░░░░░░░░  62%  (31/50)
```

Full block `█` for filled, light shade `░` for empty. Falls back to the existing `[====>   ]` style when piped.

### Category/Choice Prompts

```
Select a category:
  1  Groceries
  2  Restaurants
  3  Utilities
→  4  New Category

Choice: 
```

Numbered list, `→` marker on the last/escape option, prompt in yellow. Input validation loops until a valid number is entered.

### Yes/No Prompts

```
Mark as subscription? [Y/n]:  
```

Default is uppercase (pre-selected). Enter accepts the default.

### Error Messages

```
✗  Account name must not be blank
```

`✗` in red, message in white.

## Capturable Output

When stdout is not a TTY (i.e. piped to a file, `clip`, or Claude), JLine's `terminal.writer()` emits plain text automatically — no ANSI escapes. No extra flags needed.

To verify from a terminal:
```bash
java -jar ofxcat.jar report | cat   # cat forces pipe mode — should print clean text
java -jar ofxcat.jar import file.ofx 2>&1 | head -50
```

This is how Claude can verify workflows without stripping escape codes.

## Files Changed

| File | Change |
|---|---|
| `build.gradle` | Remove `org.beryx:text-io`, add `org.jline:jline`, `org.jline:jline-terminal-jni` |
| `cli/CLIModule.java` | Provide `Terminal` and `LineReader` instead of `TextIO` |
| `cli/CLI.java` | Replace TextIO internals with JLine; same public API |
| `cli/TextIOWrapper.java` | Delete |
| `src/main/resources/textio.properties` | Delete |
| `src/test/java/.../cli/CLIInputValidationTest.java` | Update `super(null, null)` call if constructor signature changes |

Services (`ReportingService`, `TransactionCategoryService`) inject `CLI` and are not changed — the public API stays the same.

`TransactionImportService` gains a `ProgressCallback` overload (see below).

## Implementation Phases

### Phase 1 — Library swap

1. Update `build.gradle`: remove textio, add JLine
2. Rewrite `CLIModule` to provide `Terminal` and `LineReader`
3. Rewrite `CLI.java` internals to use JLine; keep all method signatures
4. Delete `TextIOWrapper.java` and `textio.properties`
5. Update `CLIInputValidationTest` test doubles if needed
6. Run `./gradlew verify`

### Phase 2 — Visual polish

1. Implement the colour palette using `AttributedStyle` constants
2. Right-aligned label layout for transaction/transfer display
3. Unicode progress bar (with TTY detection fallback)
4. Styled category choice prompt and yes/no prompt

### Phase 2a — Import progress bar

Wire a `ProgressCallback` into the interactive import flow. The parser returns all transactions upfront (before categorization begins), so `total` is known before the first tick.

**`TransactionImportService`**: Add `importTransactions(File, ProgressCallback)` following the same pattern as `TokenMigrationService` and `GapDetectionService`. The existing `importTransactions(File)` delegates to it with `ProgressCallback.NOOP`. The callback fires once per transaction as categorization proceeds, pausing naturally while the user answers prompts.

**`OfxCat.java`**: Replace `transactionImportService.importTransactions(file)` with:
```java
transactionImportService.importTransactions(file,
    (current, total) -> cli.updateProgressBar("Importing", current, total));
cli.finishProgressBar();
```

Also remove the two `// TODO` comments that call this out (lines 138–139).

The progress bar sits above the per-transaction output. During interactive prompts it stays visible on screen; it advances when the user submits their choice and the next transaction begins processing.

### Phase 3 — Verification

1. Manual smoke test: run import with a real OFX file
2. Pipe smoke test: `java -jar ofxcat.jar report | cat` — confirm clean plain text
3. Confirm no regression in `./gradlew verify`
