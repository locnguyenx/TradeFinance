---
paths:
  - "runtime/component/TradeFinance/src/test/**/*.groovy"
---

# Moqui Testing Constraints

## 1. Test Writing Standards
* **Framework:** All tests MUST be written using the Spock framework (Groovy).
* **Package Hygiene:** Ensure all Specs and Suites are placed in the correct package (e.g., `package moqui.trade.finance`).

## 2. The Knowledge Base Pointer (CRITICAL)
* Before writing or modifying any test assertions, UI screen tests, or mock data setups, you MUST read the exact testing patterns and traps defined in `.opencode/knowledge/moqui-testing.md`.
* Pay special attention to the rules regarding **Type Coercion Traps**, **Resilient Assertions** (avoiding exact size checks), and **Sequence Collisions** documented in that file.

## 3. Test Specification Sync
* After writing or modifying any test, Update the Test Specification document for working module (i.e Import LC)  in the component's `docs/tcd` directory.