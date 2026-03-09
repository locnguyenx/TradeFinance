---
trigger: always_on
---

# General rules and constraints

## Context
This project follows a "Zero-Touch" architecture. The Moqui framework and base runtime (e.g., `framework/`, `runtime/base/`, `runtime/lib/`) are treated as immutable infrastructure. 

## Policy (CRITICAL)
- **Scope Lockdown:** You are FORBIDDEN from modifying any files outside of the custom component directory: `runtime/component/TradeFinance/`.
- **Enforcement:** Any request to modify files outside this scope must be rejected.

- All development, modifications, and additions must be contained within `runtime/component/TradeFinance/`.
- All Agent intelligence (Rules, Workflows, Skills) must be in `.agent/`.

- **Documentation Sync:** All project specifications are in `./docs`. 
  * Business Requirements (BRD): `./docs/brd`
  * Test Specifications (TCD): `./docs/tcd`
  * Technical Specifications (TSD): `./docs/tsd`

- **Knowledge Exception:** You are authorized and required to modify files in `.agent/knowledge/` when executing learning skills:
   - `moqui_syntax_ref.md`: XML syntax
   - `moqui-errors.json`: common errors troubleshooting
   - `moqui_patterns.md`: findings and lesson learned on moqui screen

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
  * **The Implementation (FTL Location):** UI rendering macros are located in `runtime/template/screen-macro/`.

* **Component vs URL:** component name use PascalCase (e.g., `TradeFinance`), `appRoot` use kebab-case (e.g., `trade-finance`).

* Component Structure
Always follow the standard Moqui directory layout for your component:
   - `data/`: Security and seed data (XML).
   - `entity/`: Entity definitions (XML).
   - `screen/`: UI screens (XML).
   - `service/`: Service definitions (XML). **Crucial**: The directory structure inside `service/` must match the service package name.
