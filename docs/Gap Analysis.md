# Documentation Gap Analysis for System Rebuild

## What Another AI Would Need vs. What We Have

### ✅ Already Sufficient

| Document | Path | Completeness |
| :--- | :--- | :---: |
| Business Requirements (BRD) | `docs/brd/business_requirements.md` | ✅ 100% |
| Import LC BRD | `docs/brd/brd_import_lc.md` | ✅ 100% |
| MT700 Field Spec | `docs/brd/MT/MT700.md` | ✅ 100% |
| Implementation Plan | `.agents/state/implementation_plan.md` | ✅ 95% |
| Consistency Report | `docs/consistency_report.md` | ✅ 100% |

### ❌ Missing / Insufficient for From-Scratch Rebuild

| # | Gap | Why It's Needed | Severity |
| :--- | :--- | :--- | :---: |
| 1 | **Technical Design Spec (TDS)** — Complete entity definitions with ALL field names, types, and relationships | The implementation plan says "30+ SWIFT fields" but doesn't list every one. Another AI cannot create `TradeFinanceEntities.xml` without the exact field spec. | 🔴 Critical |
| 2 | **Service Logic Spec** — Service signatures, parameters, AND business logic pseudocode | Services like `create#LcAmendment` (shadow record cloning) have complex Groovy logic. The plan describes WHAT but not HOW. | 🔴 Critical |
| 3 | **Screen Architecture Spec** — Moqui XML screen patterns and widget hierarchy for each screen | Another AI needs to know wrapper vs. subscreen patterns, `conditional-field` usage, `section-iterate` for status buttons, etc. | 🟡 High |
| 4 | **Moqui Setup Guide** — How to clone framework, create component, configure `MoquiConf.xml` | Basic bootstrapping instructions are not documented. | 🟡 High |
| 5 | **Security Configuration Spec** — Roles, artifact groups, authorization mappings | The actual XML exists in `20_TradeFinanceSecurityData.xml` but is not described in any doc. | 🟡 High |

### Proposed Solution

> [!IMPORTANT]
> Create a single **Technical Design Specification (TDS)** document at `docs/tds/technical_design_spec.md` that covers all 5 gaps above. This document, combined with the existing BRD and implementation plan, will form a complete "blueprint package" for rebuilding.

### TDS Proposed Structure:
1. **Moqui Setup & Bootstrap** — Framework clone, component creation, MoquiConf
2. **Entity Specification** — Every entity with every field (name, type, PK, defaults)
3. **Service Specification** — Every service with verb/noun, params, and logic pseudocode
4. **Status Flows & Seed Data** — Complete StatusFlow definitions and enumerations
5. **Security Configuration** — Roles, artifact groups, authorization matrix
6. **Screen Architecture** — Pattern templates for wrapper, find, and detail screens
7. **Demo Data Structure** — How to create representative test data

### Final Blueprint Package (After TDS Creation):
```
docs/
├── brd/
│   ├── business_requirements.md    (What to build - business)
│   ├── brd_import_lc.md            (What to build - Import LC detail)
│   └── MT/MT700.md                 (SWIFT field reference)
├── tds/
│   └── technical_design_spec.md    (How to build - technical spec)
├── consistency_report.md           (Verification cross-reference)
└── (implementation_plan is in .agents/state/)
```
