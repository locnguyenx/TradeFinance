# Test Coverage Report

Cross-reference of all **31 services** and **20+ screen paths** against **~70 test methods** across 5 Spock spec files.

---

## 1. Service Coverage Matrix

### TradeFinanceServices.xml (6 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `validate#LetterOfCredit` | ServicesSpec | 6 tests (valid/invalid chars, length, date, tolerance) | ✅ |
| `create#LetterOfCredit` | ServicesSpec | 3 tests (defaults, linked Request, history) | ✅ |
| `update#LetterOfCredit` | ServicesSpec | 2 tests (success + non-existent) | ✅ |
| `delete#LetterOfCredit` | ServicesSpec | 3 tests (Draft OK, Applied fail, Closed fail) | ✅ |
| `transition#LcStatus` | ServicesSpec | 4 tests (Draft→Applied→Issued, invalid, non-existent) | ✅ |
| `transition#TransactionStatus` | ServicesSpec | 5 tests (Draft→Submit→Approve→Close, invalid, reject/reopen) | ✅ |

### AmendmentServices.xml (3 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `create#LcAmendment` | Phase2, Phase4 | create amendment, restrict immutable fields | ✅ |
| `confirm#LcAmendment` | Phase4 | Full create→submit→approve→confirm cycle | ✅ |
| `transition#AmendmentStatus` | Phase4 | Called indirectly via `submit#` and `approve#` aliases | ✅ |

### DrawingServices.xml (5 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `create#LcDrawing` | Phase2 | create drawing, verify Received status | ✅ |
| `examine#LcDrawing` | — | — | ❌ **MISSING** |
| `record#LcDiscrepancy` | — | — | ❌ **MISSING** |
| `resolve#LcDiscrepancy` | — | — | ❌ **MISSING** |
| `transition#DrawingStatus` | — | — | ❌ **MISSING** |

### FinancialServices.xml (3 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `calculate#LcCharges` | Phase2 | Basic charge calculation | ✅ |
| `hold#LcProvision` | Phase2, Phase3 | Hold provision, verify CBS ref | ✅ |
| `release#LcProvision` | Phase2, Phase3 | Release, verify status change | ✅ |

### LifecycleServices.xml (2 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `issue#LetterOfCredit` | Phase3 | Issue LC, verify MT700 doc attachment | ✅ |
| `revoke#LetterOfCredit` | Phase4 | Fail irrevocable, succeed revocable + MT799 | ✅ |

### SwiftServices.xml (4 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `generate#SwiftMt700` | Phase3 | Generate MT700, verify structure tags | ✅ |
| `generate#SwiftMt707` | Phase4 | Indirectly via `confirm#LcAmendment` | ✅ |
| `generate#SwiftMt799` | Phase4 | Indirectly via `revoke#LetterOfCredit` | ✅ |
| `generate#SwiftMt734` | — | — | ❌ **MISSING** |

### CbsIntegrationServices.xml (5 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `hold#Funds` | Phase3 | Direct mock test | ✅ |
| `release#Funds` | Phase3 | Direct mock test | ✅ |
| `post#AccountingEntries` | — | — | ❌ **MISSING** |
| `check#CreditLimit` | — | — | ❌ **MISSING** |
| `get#ExchangeRate` | — | — | ❌ **MISSING** |

### Other Services (3 services)

| Service | Test File | Test Method(s) | Status |
| :--- | :--- | :--- | :---: |
| `send#LcNotification` | Phase3 | Verify history + real notification delivery | ✅ |
| `check#LcExpiry` | Phase3 | Auto-expire test | ✅ |
| `attach#LcDocument` | Phase2 | Basic attachment | ✅ |

---

## 2. Screen Coverage Matrix

| Screen Path | Test File | Status |
| :--- | :--- | :---: |
| `Home` | ScreensSpec | ✅ |
| `ImportLc/Dashboard` | ScreensSpec | ✅ |
| `ImportLc/Lc/FindLc` | ScreensSpec | ✅ (5 tests: title, data, columns, buttons, dialog) |
| `ImportLc/Lc/MainLC` | ScreensSpec | ✅ (7 tests: 4 LCs, sections, status, party, shipment, read-only) |
| `ImportLc/Lc/Financials` | — | ❌ **MISSING** |
| `ImportLc/Lc/Amendments` | — | ❌ **MISSING** |
| `ImportLc/Lc/Drawings` | — | ❌ **MISSING** |
| `ImportLc/Lc/History` | — | ❌ **MISSING** |
| `ImportLc/Amendment/FindAmendment` | — | ❌ **MISSING** |
| `ImportLc/Amendment/AmendmentDetail` | ScreensSpec | ✅ |
| `ImportLc/Amendment/Financials` | ScreensSpec | ✅ |
| `ImportLc/Amendment/History` | ScreensSpec | ✅ |
| `ImportLc/Drawing/FindDrawing` | — | ❌ **MISSING** |
| `ImportLc/Drawing/DrawingDetail` | ScreensSpec | ✅ |
| `ImportLc/TaskQueue` | — | ❌ **MISSING** |

---

## 3. Missing Test Scenarios

Beyond the untested services/screens above, these business-logic scenarios lack dedicated tests:

| # | Scenario | BRD Reference | Priority |
| :--- | :--- | :--- | :---: |
| 1 | **Drawing examination flow**: Received → Compliant vs Discrepant | §8.6.1-8.6.2 | 🔴 High |
| 2 | **Discrepancy recording and resolution** (Accept/Reject/Waive) | §8.6.3-8.6.4 | 🔴 High |
| 3 | **MT734 generation** on drawing refusal | §8.6.4 | 🟡 Medium |
| 4 | **CBS post#AccountingEntries** stub validation | §5.2 | 🟡 Medium |
| 5 | **Amendment rejection** (confirm with `isAccepted=false`) | §8.5.4 | 🟡 Medium |
| 6 | **Drawing status transition** (Compliant→Paid, Accepted→Paid) | §8.6.5-8.6.7 | 🟡 Medium |
| 7 | **Concurrent amendment test** (2 amendments on same LC) | §8.5.5 | 🔵 Low |

---

## 4. Coverage Summary

| Area | Total | Tested | Coverage |
| :--- | :---: | :---: | :---: |
| **Services** | 31 | 24 | **77%** |
| **Screens** | 15 | 8 | **53%** |
| **Key Scenarios** | 7 missing | 0 | **0%** |

> [!WARNING]
> **Current overall coverage: ~70%.** The gaps are concentrated in the **Drawing examination workflow** and **CBS stub services**. All core LC CRUD, status transitions, SWIFT MT700/MT707/MT799, and lifecycle (issuance, revocation, expiry) are fully covered.

---

## 5. Recommended Actions (Priority Order)

1. 🔴 **Add `TradeFinanceDrawingFlowSpec`** — Test `examine#LcDrawing`, `record#LcDiscrepancy`, `resolve#LcDiscrepancy`, `transition#DrawingStatus`, and `generate#SwiftMt734`
2. 🟡 **Add screen render tests** for `Financials`, `Amendments`, `Drawings`, `History` tabs under `ImportLc/Lc/` and `FindAmendment`, `FindDrawing`, `TaskQueue`
3. 🟡 **Add CBS stub tests** for `post#AccountingEntries`, `check#CreditLimit`, `get#ExchangeRate`
4. 🔵 **Add amendment rejection test** (confirm with `isAccepted=false`)
