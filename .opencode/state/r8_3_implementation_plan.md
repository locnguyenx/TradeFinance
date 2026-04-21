# Sequence 2: R8.3 LC Application (Draft Creation & Validation)

This plan follows the refined **Analyze & Plan** step of the project's `/tdd` workflow.

## 1. Feature Status: 🚧 Analyze & Plan (PLANNING)
- **Status**: Awaiting Approval of Test Outlines.

## 2. Unit Test Outline (Spec: `TradeFinanceApplicationSpec.groovy`)

Based on [BDD-R8.3-UC1](file:///Users/me/myprojects/moqui-antigravity-new/runtime/component/TradeFinance/docs/bdd/BDD-R8.3-UC1_CreateDraftLCApplication.md), I will implement the following tests:

### A. Creation Scenarios (RED -> GREEN)
1.  **test_create_draft_success**:
    - **Inputs**: Valid mandatory fields (Applicant, Beneficiary, Amount, Currency, Expiry, ProductId).
    - **Assertions**: `lcId` exists, `transactionStatusId == 'LcTxDraft'`, `lcStatusId == 'Draft'`, `LC_CHG_ISSUANCE` record created, `LcHistory` record exists.
2.  **test_create_missing_mandatory**:
    - **Inputs**: Missing `productId` or `applicant`.
    - **Assertions**: `ec.message.hasError() == true`, no record created.
3.  **test_create_invalid_swift_chars**:
    - **Inputs**: Field `descriptionOfGoods` containing `#` or `@`.
    - **Assertions**: Error message "Invalid characters detected", no record created.
4.  **test_create_constraint_violations**:
    - **Inputs**: `lcNumber` > 16 chars, Expiry < Issue Date.
    - **Assertions**: Field-specific validation error, no record created.
5.  **test_create_amount_tolerance_format**:
    - **Inputs**: `amountTolerance` = "5/5".
    - **Assertions**: Success.

### B. Management Scenarios (RED -> GREEN)
6.  **test_update_draft_history**:
    - **Activity**: Update Applicant Name.
    - **Assertions**: Field updated, new `LcHistory` entry with `changeType: 'Update'`.
7.  **test_delete_draft_success**:
    - **Activity**: Call `delete#LetterOfCredit` on a Draft LC.
    - **Assertions**: Database query returns null for `lcId`.
8.  **test_delete_non_draft_failure**:
    - **Activity**: Set LC to `Applied` status via database, then call delete service.
    - **Assertions**: Service returns error, record still exists.

## 3. Targeted Production Changes

### Services (to be modified in Phase 2: GREEN)
- `moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit`:
    - Add parameter validation.
    - Implement SWIFT X-Charset check.
    - Add automated charge calculation call.
    - Add history logging.
- `moqui.trade.finance.TradeFinanceServices.update#LetterOfCredit`:
    - Add history logging.
- `moqui.trade.finance.TradeFinanceServices.delete#LetterOfCredit`:
    - Add status check (`LcTxDraft`).

## 4. Intervention Checklist
- [x] **Analyze & Plan**: Outlined Unit Tests (Wait for approval).
- [ ] **RED**: Run tests to prove failure.
- [ ] **GREEN**: Implement minimal code.
- [ ] **REFACTOR**: Cleanup and architectural sync.

> [!IMPORTANT]
> **STOP**: I am currently waiting for your approval of the **Unit Test Outline** (Section 2) before moving to the **RED Phase**.
