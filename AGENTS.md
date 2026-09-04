# Project Development Rules for SensorsOff

## Git & Documentation Mandates
1. **Always maintain `CHANGELOG.md`**:
   - For every change, update `CHANGELOG.md` at the project root following Keep a Changelog format.
   - Every entry must detail:
     - **Problem Analysis**: What was the issue and what user experience or telemetry was observed.
     - **Root Cause**: The underlying technical cause (e.g. IPC latency, process fork overhead, lifecycle race condition).
     - **Code Changes**: Exact files, classes, methods, and algorithmic logic changed.
     - **Telemetry & Verification**: Expected timings and verification results.
2. **No Commit Messages in Conversation**:
   - Do NOT display or print Conventional Commit message blocks in conversational chat responses.
   - All conventional commit records must be maintained directly and silently inside `CONVENTIONAL_COMMITS.md` at the project root.
3. **Always maintain `CONVENTIONAL_COMMITS.md`**:
   - For every change or release, append/update `CONVENTIONAL_COMMITS.md` at the project root with the full conventional commit message entry (including type, scope, problem statement, root cause, detailed changes, and verification).
4. **Always maintain `PROBLEM_ANALYSIS_ROOT_CAUSE.md`**:
   - For every bug, issue, performance optimization, or architecture change, append/update `PROBLEM_ANALYSIS_ROOT_CAUSE.md` at the project root with the dedicated Problem Analysis, Root Cause, and Engineered Resolution & Impact entry.
