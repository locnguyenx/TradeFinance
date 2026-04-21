# Sequence 3: R8.4 LC Issuance

Implementation of Letter of Credit issuance workflow, including status transitions, provision activation, contingent accounting, and SWIFT MT700 generation.

## User Review Required

> [!IMPORTANT]
> The existing `LifecycleServices.xml:issue#LetterOfCredit` and `FinancialServices.xml:hold#LcProvision` have a logic mismatch causing duplicate provisions. This plan includes fixing this as part of the GREEN phase.

## Proposed Changes

### [Component: Trade Finance Services]

#### [MODIFY] [TradeFinanceServices.xml](file:///Users/me/myprojects/moqui-antigravity-new/runtime/component/TradeFinance/service/moqui/trade/finance/TradeFinanceServices.xml)
- Update `approve#LcByTradeOperator` to pass `provisionSeqId` to `hold#LcProvision`.

#### [MODIFY] [FinancialServices.xml](file:///Users/me/myprojects/moqui-antigravity-new/runtime/component/TradeFinance/service/moqui/trade/finance/FinancialServices.xml)
- Update `hold#LcProvision` to accept `provisionSeqId`.
- Implement `update` logic if `provisionSeqId` is provided, instead of always `create`.

#### [MODIFY] [LifecycleServices.xml](file:///Users/me/myprojects/moqui-antigravity-new/runtime/component/TradeFinance/service/moqui/trade/finance/LifecycleServices.xml)
- Ensure `issue#LetterOfCredit` correctly triggers all downstream accounting and document generation.

### [Component: Testing]

#### [MODIFY] [TradeFinanceIssuanceSpec.groovy](file:///Users/me/myprojects/moqui-antigravity-new/runtime/component/TradeFinance/src/test/groovy/moqui/trade/finance/TradeFinanceIssuanceSpec.groovy)
- **Enhanced Verification**:
  - Verify `AcctgTrans` (contingent) creation.
  - Verify `Invoice` creation for upfront charges.
  - Verify SWIFT MT700 document content (basic format check).
  - Add test for "Issue" transition guard (must be Approved).

## Verification Plan

### Automated Tests
- Run `TradeFinanceIssuanceSpec.groovy`.
- Expected: 
  - **RED**: Tests fail due to missing accounting/invoice assertions (and existing status bug).
  - **GREEN**: All tests pass after service fixes.
