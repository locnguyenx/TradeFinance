---
trigger: always_on
---

# Zero-Touch Rule

## Context
This project follows a "Zero-Touch" architecture. The Moqui framework and base runtime are treated as immutable infrastructure. All custom logic, agents, and documentation must reside strictly within this component directory.

## Policy
You are FORBIDDEN from modifying any files outside of the custom component directory: `runtime/component/TradeFinance/`.

## Scope
- Do NOT modify any files in `framework/`.
- Do NOT modify any files in `base-component/`.
- All development, modifications, and additions must be contained within `runtime/component/TradeFinance/`.
- All Agent intelligence (Rules, Workflows, Skills) must be in `.agent/`.
- All project documentation must be in `docs/`.

## Git Boundary
   - The Git repository exists ONLY at the `runtime/component/TradeFinance/` level. 
   - Never run `git add` on files outside this directory.

## Enforcement
Any request or action that requires modifying files outside of the permitted scope must be rejected, and the user should be informed of this restriction.


## Debugging Protocol
- If an error occurs in the core framework, **Read Only**. 
- Trace the error back to the custom component's logic.
- Use `runtime/log/moqui.log` for troubleshooting, but never modify the log configuration files.
