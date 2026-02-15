# AI Collaboration Guide

This document captures design philosophy, working norms, and practices that aren't mechanically enforced by the build tooling. For static analysis rules, formatting, architecture enforcement, and coverage requirements, see `docs/StaticAnalysis.md` and the ArchUnit tests — those are enforced by `./gradlew verify`.

## Working Relationship

- We're colleagues. No formal hierarchy.
- Don't be a sycophant. I need honest technical judgment, not agreement.
- Push back when you disagree with my approach. Cite technical reasons if you have them, but gut feelings are fine too.
- Speak up immediately when you don't know something or we're in over our heads.
- Ask for clarification rather than making assumptions.
- If you're stuck, stop and ask for help — especially where human input would be valuable.
- Discuss architectural decisions (framework changes, major refactoring, system design) together before implementation. Routine fixes and clear implementations don't need discussion.

## Proactiveness

When asked to do something, just do it — including obvious follow-up actions needed to complete the task properly. Only pause to ask for confirmation when:
- Multiple valid approaches exist and the choice matters
- The action would delete or significantly restructure existing code
- You genuinely don't understand what's being asked
- I specifically ask "how should I approach X?" (answer the question, don't jump to implementation)

## Design Philosophy

- YAGNI. The best code is no code. Don't add features we don't need right now.
- KISS: Keep it Simple, Stupid. Don't overcomplicate things.
- When it doesn't conflict with YAGNI, architect for extensibility and flexibility.
- Make the smallest reasonable changes to achieve the desired outcome.
- Prefer simple, clean, maintainable solutions over clever or complex ones.
- Work hard to reduce code duplication, even if the refactoring takes extra effort.
- Never throw away or rewrite implementations without explicit permission.
- Get explicit approval before implementing backward compatibility.
- Fix broken things immediately when you find them. Don't ask permission to fix bugs.

## Naming

Names tell what code does, not how it's implemented or its history:
- Never use implementation details in names (e.g., "ZodValidator", "MCPWrapper", "JSONParser")
- Never use temporal/historical context (e.g., "NewAPI", "LegacyHandler", "ImprovedInterface")
- Never use pattern names unless they add clarity (e.g., prefer "Tool" over "ToolFactory")

Good: `Tool`, `RemoteTool`, `Registry`, `execute()`
Bad: `AbstractToolInterface`, `MCPToolWrapper`, `ToolRegistryManager`, `executeToolWithValidation()`

## Code Comments

- Comments explain WHAT the code does or WHY it exists
- Never add comments about what used to be there, how something changed, or that it's "improved"
- Never add instructional comments telling developers what to do
- Never remove code comments unless they are provably false
- If you catch yourself writing "new", "old", "legacy", "wrapper", or "unified" in names or comments, stop and find a name that describes the thing's actual purpose

## Test Driven Development

For every new feature or bugfix, follow TDD:
1. Write a failing test that validates the desired functionality
2. Run the test to confirm it fails as expected
3. Write only enough code to make the failing test pass
4. Run the test to confirm success
5. Refactor if needed while keeping tests green

## Testing Practices

- All test failures are your responsibility, even if they're not your fault.
- Never delete a test because it's failing. Raise the issue instead.
- Never write tests that only test mocked behavior. Tests must exercise real logic.
- Never implement mocks in end-to-end tests. Use real data.
- Never ignore test output — logs and messages often contain critical information.

### Testing Priorities
- **Priority 1**: Critical business logic (categorization, transfers, balance calculations)
- **Priority 2**: Error handling and edge cases
- **Test data**: Unit tests use synthetic data; integration tests use realistic transaction patterns
- **Adversarial testing**: Include SQL injection, extreme values, malformed inputs, Unicode/special chars

## Version Control

- Never use `git add -A` without checking `git status` first.
- When starting work without a clear branch, create a WIP branch.
- Commit frequently, even if high-level tasks aren't done yet.

## Systematic Debugging

Always find the root cause. Never fix symptoms or add workarounds.

### Phase 1: Root Cause Investigation (before attempting fixes)
- Read error messages carefully — they often contain the solution
- Reproduce consistently before investigating
- Check recent changes: git diff, recent commits

### Phase 2: Pattern Analysis
- Find similar working code in the codebase
- Compare against reference implementations
- Identify differences between working and broken code
- Understand dependencies the pattern requires

### Phase 3: Hypothesis and Testing
1. Form a single hypothesis. State it clearly.
2. Make the smallest possible change to test it.
3. Verify before continuing. If wrong, form a new hypothesis — don't stack fixes.
4. When you don't know, say so.

### Phase 4: Implementation
- Have the simplest possible failing test case
- Never add multiple fixes at once
- Never claim to implement a pattern without reading it completely first
- Test after each change
- If your first fix doesn't work, stop and re-analyze
