# Moqui UI & Screen Patterns
## Introduction
## Patterns & Lession Learned
### 1. Robust Screen Hierarchy (SimpleScreens Pattern)
**Problem:** Detail headers or tabs appearing on list views (leakage) or disappearing on direct navigation to the parent.
**Standard Pattern (following SimpleScreens/Asset):**
1. **Parent Screen**: Fetch the entity and use its presence as the primary condition.
   ```xml
   <section name="DetailHeader" condition="record && !sri.screenUrlInfo.targetScreen?.getScreenName()?.matches('Find.*|ParentName')">
   ```

### Robust Screen Hierarchy Guard
To ensure detail headers and tabs only show on sub-screens (and not the list/parent screen), use `sri.screenUrlInfo.extraPathNameList` to detect the presence of a sub-path:

```xml
<section name="LcDetailHeader" condition="lc && sri.screenUrlInfo.extraPathNameList">
```

This is more robust than checking for specific screen names or using regex, as it correctly identifies when the user is at the parent screen level (where `extraPathNameList` is empty).
2. **Sub-screens**: Explicitly mark ID parameters as `required="true"`. This allows Moqui to handle tab visibility more naturally.
3. **Menu Exclusion**: Set `default-menu-include="false"` on "Find" screens that are default sub-screen items to prevent redundant tabs.

**Lession Learned (Regex Guard):** The regex `matches('Find.*|ParentName')` is the most robust way to ensure headers are hidden on BOTH the search screen and the parent screen itself while remaining visible on all other detail tabs (Financials, History, etc.).

### 2. Layout & Styling Rules
- **Encapsulation:** NEVER place a `<link>` or `<container>` directly inside a `<field-layout>`. It must be wrapped in a `<field>` and referenced via `<field-ref>`.
- **Quasar Classes:** `<container>` does not support Quasar `class` attributes. Use the `style` attribute (e.g., `style="q-card shadow-2"`).
- **Standard Actions:** Use `Create`, `Save` (NEVER "Update"), and `Delete` (style `text-negative`). Primary actions belong in the top header via `<field-ref>`.
- **Navigation:** Always use `url-type="screen"` and relative paths. Use `${lastScreenUrl ?: '.'}` for back links.
- **Refresh Logic:** Add `reload-save="true"` to `<form-single>` if it modifies data shown in the header/tabs.

### 3. Stale UI Cache
- **Symptom:** Tabs persist after switching a subscreen-panel from `type="tab"` to `type="popup"`.
- **Fix:** Execute `./gradlew cleanAll` and perform a hard browser refresh (Cmd+Shift+R).

### UI Fix: Form List Column Field-Ref
Inside `<form-list-column>`, always use `<field-ref>` to include fields. Using `<field>` directly will result in an empty list display.

### UI: Relative Transitions in Nested Modules
When nesting screens in subdirectories (e.g., `ImportLc/List`), relative URLs in transitions must account for the new depth. Use `../../Detail` instead of `../Detail` to reach screens in the parent directory.

### UI: Tabbed Navigation
Use `<subscreens-panel type="tab"/>` instead of legacy `<subscreens-tabs/>` for modular parent screens. The `<subscreens-tabs/>` tag is outmoded and may show "not yet implemented" errors in modern Moqui renderers.

### UI: Widget Template Case Sensitivity
Moqui widget templates like `enumDropDown` are case-sensitive. parameters (e.g., `enumTypeId`) must match the database/entity case exactly.


