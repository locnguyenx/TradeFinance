# Moqui Development Patterns & Lessons Learned

## 🎨 UI & Screen Patterns

### 1. Safe Subscreen Detection (Header Visibility)
**Problem:** Detail headers or tabs appearing on list views (leakage) or disappearing on direct navigation to the parent.
**Pattern:**
```xml
<section name="DetailHeader" condition="recordId && !['FindRecord'].contains(sri.screenUrlInfo.targetScreen?.getScreenName())">
```
**DANGER:** 
- NEVER include the parent screen name (e.g., `'Lc'`) in the exclusion list. 
- If the user lands on the parent URL with an ID (e.g., `.../Lc?lcId=123`), the `targetScreen` is the parent itself. Excluding it will hide the header.
- ONLY exclude explicitly designated search/list screens (e.g., `'FindLc'`, `'LcList'`).

### 2. Layout & Styling Rules
- **Encapsulation:** NEVER place a `<link>` or `<container>` directly inside a `<field-layout>`. It must be wrapped in a `<field>` and referenced via `<field-ref>`.
- **Quasar Classes:** `<container>` does not support Quasar `class` attributes. Use the `style` attribute (e.g., `style="q-card shadow-2"`).
- **Standard Actions:** Use `Create`, `Save` (NEVER "Update"), and `Delete` (style `text-negative`). Primary actions belong in the top header via `<field-ref>`.
- **Navigation:** Always use `url-type="screen"` and relative paths. Use `${lastScreenUrl ?: '.'}` for back links.
- **Refresh Logic:** Add `reload-save="true"` to `<form-single>` if it modifies data shown in the header/tabs.

### 3. Stale UI Cache
- **Symptom:** Tabs persist after switching a subscreen-panel from `type="tab"` to `type="popup"`.
- **Fix:** Execute `./gradlew cleanAll` and perform a hard browser refresh (Cmd+Shift+R).

---

## 🏗️ Entity & Data Patterns

### 1. Structure & Conventions
- **Naming:** CamelCase for entities, package names must match directory structure.
- **Surrogate Keys:** Use exactly one `<field>` with `is-pk="true"` and `type="id"`.
- **ID Generation:** Set `primary-key-sequence="true"` on the `<entity>` tag for automatic ID generation.
- **Audit:** Framework injects `lastUpdatedStamp` and `createdTxStamp` automatically.

### 2. Relationships & Views
- **Relationships:** Use `<relationship type="one" related="...">` to link to framework entities. Enables auto-joins.
- **Views:** Use `<view-entity>` for SQL-free joins. AVOID raw SQL in services.
- **Caching:** `cache="true"` ONLY for static configuration (Enums, StatusItems).

### 3. Data Loading Order
- **Sequence:** Seed Data → Initial Data → Demo Data.
- **Demo Strategy:** Explicitly define PK IDs in Demo XMLs (e.g., `lcId="DEMO_LC_01"`) for predictable testing.

---

## ⚙️ Service & Backend Patterns

### 1. The "Read-Refresh-Update" Pattern (CRITICAL)
**Problem:** Calling a child service that modifies a record makes the parent's local `EntityValue` stale. Subsequent updates overwrite child changes.
**Fix:** Re-fetch the record via `<entity-find-one>` *after* internal service calls.

### 2. Logic & Error Handling
- **Boolean Returns:** Spock Traps! Result Map booleans may revert to Strings. Assert `result.success == true || result.success == "true"`.
- **Conditionals:** `<else>` MUST be nested *inside* `<if>`.
- **LIKE Queries:** Use `EntityCondition.LIKE`, NOT `EntityList.LIKE`.
- **Error Triggering:** `<message error="true">` does NOT fail transactions. Use `<script>ec.message.addError("...")</script>`.

---

## 🧪 Testing & Integration Patterns

### 1. Environment & State
- **The reloadSave Rule:** Every test run MUST be preceded by `./gradlew reloadSave`.
- **Sequence Safety:** Use `ec.entity.tempSetSequencedIdPrimary(..., 960000, 10)` in `setupSpec()` to avoid PK collisions during tests.
- **Resilient Assertions:** Avoid exact size checks (e.g., `history.size() == 5`). Use thresholds (`>= 5`) and `.find { ... }` to account for background audit noise.

### 2. Integration Seaming
- **Mocking:** Define strict service contracts for external systems (CBS, Swift). Implement mocks using `<script>` tags that return success and simulated data.
