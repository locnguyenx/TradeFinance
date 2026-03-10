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

### Robust Screen Hierarchy Guard (Definitive "Clean Parent" Pattern)
Based on project best practices (as seen in `SimpleScreens`), avoid declaring ID parameters in parent/shell screens that host both list and detail views.

#### 1. Menu-Level Clearing (The Entry Point)
Clear parameters at the menu level to ensure a clean start for list/search screens.
```xml
<!-- In ImportLc.xml -->
<subscreens-item name="Lc" location="..." parameter-map="[lcId:null]"/>
```

#### 2. The Clean Parent (The Shell)
Comment out or remove parameter declarations in the parent screen. This prevents the parent from "adopting" parameters that should only belong to sub-screens.

```xml
<!-- Lc.xml (Parent) -->
<!-- don't explicitly declare parameters here
    <parameter name="lcId"/>
-->

<actions>
    <!-- Use context fields instead of declaring parameters -->
    <set field="lcId" from="lcId ?: lcSeqId"/>
    <if condition="lcId">
        <entity-find-one entity-name="..." value-field="lc"/>
    </if>
</actions>

<widgets>
    <!-- Visibility is naturally handled by the presence of 'lc' -->
    <section name="LcHeader" condition="lc">
        <widgets>...</widgets>
    </section>
</widgets>
```

#### 3. Targeted Sub-screens (The Detail)
Sub-screens (like `MainLC.xml` or `AmendmentDetail.xml`) SHOULD declare their parameters as `required="true"`. This makes them robust entry points for detail views.

**Why this works**:
- **Menu Freshness**: The menu ensures the list is clean.
- **Natural Visibility**: By not declaring parameters in the shell, you avoid "parameter stickiness" issues where a shell screen might hold onto an ID and pass it to a sub-screen that doesn't want it (like a Find screen).
- **Simplicity**: No complex `extraPathNameList` or `targetScreen` exclusion lists are needed in the UI code.

**Benefits**:
- **Clean UI**: Visibility conditions stay simple (`condition="item"`).
- **Navigation Safety**: Prevents "parameter leakage" from persistent IDs.
- **Maintenance**: No need to maintain a list of all "Detail" tabs; only list the search/parent screens.
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


### 4. Transition Logic Reusability (transition-include)
**Problem:** Duplicating transition logic (especially for creation/deletion) across multiple screens leads to "stale logic" where one screen's button breaks when the underlying service or redirection changes.
**Solution:**
1.  **Shared Transition File**: Create a dedicated XML screen file (e.g., `template/lc/LcTransitions.xml`) to house common transitions.
2.  **Include**: Use `<transition-include name="..." location="..." />` in any screen that needs the logic.

### 5. Robust Redirection in Quasar (Relative vs Sparse)
**Problem**: Redirection in Quasar's hash-routing can be brittle.
1. **Full Reloads**: Absolute paths (`/`) or Sparse paths (`//`) often trigger a full page reload in the browser, which can break session state or be slow.
2. **Path Resolution**: Using just the screen name in `url` (e.g., `url="${screenName}"`) can sometimes fail to resolve correctly in the Quasar router.

**Solution**:
- **Prefer Explicit Relative Paths**: Use `./` prefix for redirects within the same screen group.
- **Example**: `<default-response url="./MainLC" parameter-map="[lcId:lcId]"/>`
- **Why**: The `./` prefix ensures the Quasar router treats the navigation as an in-app hash change, preserving the SPA experience and ensuring the path is correctly appended to the current route.

### 6. Organizing Shared Fragments
**Pattern**: Store reusable XML fragments (shared forms, dialogs, transitions) in a `template/` directory within the component (e.g., `template/lc/`). This keeps the `screen/` directory focused strictly on navigation and page structure, matching Moqui's own framework organization.
