# TDD Master Plan: Trade Finance System

This document outlines the strategic roadmap for transitioning the entire Trade Finance system to 100% TDD-verified status, using a holistic "Triple-Thread" approach.

## 1. Core Principles
- **Specification First**: No code exists without a corresponding BDD scenario and a passing Spock Spec.
- **Holism**: Every test suite must cover Configuration (Seed), Lifecycle (Service), and Resilience (CBS/Integration).
- **Atomic Migrations**: We will handle one BDD file at a time to ensure 100% quality before moving to the next.

## 2. The TDD Migration Workflow (Per Feature)

For every BDD file in `docs/bdd/`, we will execute the following 5-step cycle:

1.  **Alignment [ACTION REQUIRED]**: I will present the BDD scenarios. You must verify they align with the latest BRD.
2.  **Structural RED**: Write tests that verify the existence of configured entities and service signatures. Run to fail.
3.  **Functional GREEN**: Implement/Refine the minimal service logic to satisfy the scenario. Run to pass.
4.  **Resilience EDGE**: Use the `Simulator` to inject failure points (Insufficent funds, Timeouts) and verify system rollbacks.
5.  **Sync & Handover**: Update the Walkthrough to proof the implementation.

## 3. Human Intervention Points

To ensure the highest delivery quality, your intervention is required at these specific points:

### A. BDD Validation (The "Truth" Check)
- **When**: Before starting any code/test work for a feature.
- **Instruction**: Check `docs/bdd/[Feature].md`. Ensure the "Given/When/Then" steps accurately reflect your business preference. If not, provide a "Correction Directive" (e.g., "The IPC Supervisor should be the ONLY one who can approve this").

### B. Unit Test Plan Approval (The "Technical" Check)
- **When**: After I propose the implementation plan in PLANNING mode.
- **Instruction**: Verify the "Outlined Unit Tests" cover all edge cases in the BDD. Ask for more tests if you feel a scenario is under-tested.

### C. UI/UX Verification (The "Human" Check)
- **When**: After a feature is marked GREEN.
- **Instruction**: Since Spock cannot see the screen, I will provide a deep link (e.g., `trade-finance/lc/detail?lcId=123`). You must open the browser, perform a manual action, and confirm the visual layout (colors, chips, labels) meets your aesthetic standards.

## 4. Priority Roadmap (Implementation Order)

| Sequence | BDD Document | Scope | Status |
| :--- | :--- | :--- | :--- |
| 1 | `BDD_R8.11_ProvisionAndCharge.md` | Finalize Integration & Resilience | **DONE** |
| 2 | `BDD_R8.3-UC1_CreateDraftLCApplication.md` | Core LC Creation & Validation | **DONE** |
| 2.1 | Manual Charge Management | Defect Fix & Charge CRUD | **DONE** |
| 3 | `BDD_R8.4_LcIssuanceAndCbs.md` | Issuance & Accounting | **PLANNING** |
| 4 | `BDD_R8.5_LcAmendmentProcessing.md` | Shadow Record Model & Cloning | Pending |
| 5 | `BDD_R8.8-R8.10_DrawingLifecycle.md` | Document Exam & Refusal | Pending |

## 5. Definition of Done (DoD)
A feature is "DONE" only when:
- [x] 100% of BDD Scenarios have a corresponding passing test in a Spock Spec.
- [x] No @Ignore tests exist except for infrastructure-blocked items.
- [x] User has explicitly approved the UI rendering in the browser.
- [x] Walkthrough includes successful log traces from the `Simulator`.
