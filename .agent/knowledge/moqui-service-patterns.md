# Moqui Service & Backend Patterns

## Introduction

## Patterns & Lession Learned
### 1. The "Read-Refresh-Update" Pattern (CRITICAL)
**Problem:** Calling a child service that modifies a record makes the parent's local `EntityValue` stale. Subsequent updates overwrite child changes.
**Fix:** Re-fetch the record via `<entity-find-one>` *after* internal service calls.

### 2. Logic & Error Handling
- **Boolean Returns:** Spock Traps! Result Map booleans may revert to Strings. Assert `result.success == true || result.success == "true"`.
- **Conditionals:** `<else>` MUST be nested *inside* `<if>`.
- **LIKE Queries:** Use `EntityCondition.LIKE`, NOT `EntityList.LIKE`.
- **Error Triggering:** `<message error="true">` does NOT fail transactions. Use `<script>ec.message.addError("...")</script>`.

### Service Resolution: Directory Matching
Service XML files MUST reside in a directory structure that exactly matches their package name (e.g., `service/moqui/trade/finance/TradeFinanceServices.xml` for package `moqui.trade.finance`).
