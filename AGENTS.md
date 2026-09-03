# Project Development Rules for SensorsOff

## Git & Documentation Mandates
1. **Always maintain `CHANGELOG.md`**:
   - For every change, update `CHANGELOG.md` at the project root following Keep a Changelog format.
   - Every entry must detail:
     - **Problem Analysis**: What was the issue and what user experience or telemetry was observed.
     - **Root Cause**: The underlying technical cause (e.g. IPC latency, process fork overhead, lifecycle race condition).
     - **Code Changes**: Exact files, classes, methods, and algorithmic logic changed.
     - **Telemetry & Verification**: Expected timings and verification results.
2. **Commit Message Format**:
   - Always provide a comprehensive, conventional commit message in conversational responses detailing the problem, root cause, and changes for easy copy-pasting into GitHub.
