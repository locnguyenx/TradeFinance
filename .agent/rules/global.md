---
trigger: always_on
---

# General rules and constraints

## Context
This project follows a "Zero-Touch" architecture. The Moqui framework and base runtime are treated as immutable infrastructure. All custom logic, agents, and documentation must reside strictly within this component directory.

## Policy
You are FORBIDDEN from modifying any files outside of the custom component directory: `runtime/component/TradeFinance/`.

## Scope
- All development, modifications, and additions must be contained within `runtime/component/TradeFinance/`.
- All Agent intelligence (Rules, Workflows, Skills) must be in `.agent/`.
- All project documentation must be in `docs/`.

## Git Boundary
   - The Git repository exists ONLY at the `runtime/component/TradeFinance/` level. 
   - Never run `git add` on files outside this directory.

## Enforcement
Any request or action that requires modifying files outside of the permitted scope must be rejected, and the user should be informed of this restriction.


## Moqui Ecosystem Standards
* **Core Framework:** Moqui Framework.
* **XML Standards:**
  * **Naming:** PascalCase ONLY for all files (e.g., `CreateTrade.xml`, `VendorServices.xml`).
  * **The Law (Schema Source):** Adhere strictly to schemas defined in `framework/xsd/`.
  * **The Implementation (FTL Location):** Assume UI rendering macros are located in `runtime/template/screen-macro/`.
* If you write an XML screen, you MUST run the `moqui-screen-validator` skill before marking the task complete.
