# Moqui Testing & Environment Setup (Lessons Learned)

> **Agent Directive:** Read this file when asked to write Spock tests, troubleshoot test failures, or reset the Moqui database environment.

## Running Tests (regular test cycles)
After the snapshot exists,**DO NOT RUN THE MOQUI SERVER MANUALLY.** Always use the `./gradlew reloadSave` commands to run tests individually so that they execute in a clean environment that terminates upon completion.

**Gradle Build Task Behaviors:**
- `task cleanDb`: Removes data (database, elasticsearch, txlog), but leaves built JARs and classes intact. Use this when you want to reset data but trust the build.
- `task clean` / `task cleanAll`: Removes all generated artifacts, including compiled classes and framework-level generated JS/resources.
- `task load`: Compiles code and loads all XML data files into the DB.
- `task reloadSave`: Restores the database from a pre-calculated snapshot (`runtime/db/save`). Fast and essential for Spock test cycles.

**The "Data Issue" Reset Pattern**:
If error message is about data itegrity violation, missing data, or other data-related issues:
1. Run `./gradlew cleanDb loadSave` to reset the database, then rerun the test.
2. If this approach doesn't work, check the xml data setup for duplicated or missing data. And/or check the entity.

**The "Abnormal System" Reset Pattern**:
If the Moqui system becomes unresponsive, tests hang indefinitely, or you see `NoClassDefFoundError` even after a successful build:
1. Run `./gradlew cleanAll` to wipe all potentially corrupt build artifacts.
2. Run `./gradlew load` or `./gradlew loadSave` (make a data snapshot) to rebuild the framework and component classes from scratch.
3. Resume testing with `./gradlew :runtime:component:TradeFinance:test`, or run testing with `./gradlew reloadSave :runtime:component:TradeFinance:test` if you want to use the snapshot.

**CRITICAL RULE:** Do NOT touch any files outside of the application component folder you are working in (e.g. `./runtime/component/TradeFinance`). If a framework-level file is suspected to be broken, inform the user and request them to verify it manually or pull it from the source. NEVER attempt to fix, modify, or restore framework code.

For example, to run tests inside `runtime/component/TradeFinance`:
`./gradlew reloadSave runtime:component:TradeFinance:test -x combineBaseJs -x combineVuetJs` (use `-x` to bypass broken framework tasks if necessary)

## 1. The Environment Mandates
* **NEVER** run `java -jar moqui.war` for data loading or testing. 
* **The `reloadSave` Rule:** Every single test run MUST be preceded by `reloadSave` to ensure database consistency and prevent data pollution.
  * Command: `JAVA_HOME=... ./gradlew reloadSave :runtime:component:TradeFinance:test`
* **Data Refresh:** If entity or data files change, run `cleanDb load loadSave` BEFORE using `reloadSave`.
* **Abnormal Hangs:** If tests hang or throw `NoClassDefFoundError`, run `./gradlew cleanAll`, then `loadSave` to wipe corrupt artifacts.

## 2. Spock Testing Traps
* **Type Coercion:** `result.success == true` might fail if an XML service returns the String `"true"`. Assert: `result.success == true || result.success == "true"`.
* **Resilient Assertions:** System jobs add background logs. Avoid exact size assertions (e.g., `history.size() == 5`). Use thresholds (`>= 5`) and `.find { ... }` instead of hardcoded array indices (`history[3]`).
* **Sequence Collisions:** If testing auto-increment tables, use `ec.entity.tempSetSequencedIdPrimary("...", 960000, 10)` in `setupSpec()` to force a safe starting value.

## 3. UI Screen Testing
* `ScreenTestRender` `assertContains` checks often fail because Quasar collapses or lazy-loads data. 
* Focus tests on static layout strings (e.g., Tab Headers) or use `@Ignore` to bypass legacy screen tests to focus resources on Service unit testing.

## Testing Checklist & Lessons Learned

> [!IMPORTANT]
> **Rule of Clean Environment**: Every single test run MUST be preceded by `reloadSave` to ensure database consistency. Running tests without it leads to "polluted" data and unreliable results.

### The `reloadSave` Mandate
- **Why**: Moqui tests often depend on demo data or specific starting states. If Run A modifies a record, Run B will see that modified record unless the DB is restored.
- **How**: Always use the command format: 
  `JAVA_HOME=... ./gradlew reloadSave :runtime:component:[ComponentName]:test`
- **Snapshot Refresh**: If you modify `entity/` or `data/` files, you MUST run `cleanDb load loadSave` BEFORE using `reloadSave`.

### Spock Discovery Rules
- **Package Hygiene**: Ensure all Specs and Suites are in the correct package (e.g., `package moqui.trade.finance`).
- **Gradle Filters**: Check `build.gradle` for `filter { includeTestsMatching ... }` to ensure your Suite is being targeted.
- **Test report and Test result file**:
    - Checking test HTML report in html files at directory `./runtime/component/TradeFinance/build/reports/tests/test/`
    - Finding the test results XML file to see the actual error output at 
directory `runtime/component/TradeFinance/build/test-results/test/`

### Debugging Flow
1. If a test fails, verify it's not a data pollution issue by running with `reloadSave`.
2. check `runtime/log/moqui.log` for hidden entity errors.
3. use `logger.info()` in Specs to dump variables if assertions fail.

### Spock Type Coercion Trap
- When calling `ec.service.sync()...call()`, the returned result is a Map. While Groovy handles truthiness well, sometimes boolean returns from XML services revert to Strings.
- **Trap**: `result.success == true` might fail if `success` is actually `"true"`.
- **Fix**: Use `result.success == true || result.success == "true"` in assertions.

### Screen Testing vs Quasar UI
- When heavily utilizing Quasar for tabs, dialogs, and dynamic rendering, `ScreenTestRender` `assertContains` checks for basic screen text may fail because Quasar collapses or lazy-loads data. 
- Ensure you test for static layout strings (e.g., Tab Headers like "AMENDMENTS") rather than deep data components if lazy-loading is suspected, OR use `@Ignore` to bypass legacy screen tests to focus resources solely on Service unit testing.

### 7. Parameter Naming Standardization
- **Pattern**: When building cross-module navigation (e.g., from Lc to Amendment), standardize ID parameter names. Use `lcId`, `amendmentSeqId`, and `drawingId` consistently in all screens, links, and services.
- **Why**: Mixed naming (e.g., `drawingSeqId` vs `drawingId`) leads to broken links and navigation failures during screen transitions.

### The "Stale UI Cache" Issue
- **Symptom**: After changing a screen wrapper from `type="tab"` to `type="popup"`, the tabs still appear in the browser.
- **Why**: The Moqui framework/renderer may have cached the old screen structure in generated JS artifacts or browser session.
- **Fix**: Run `./gradlew cleanAll` and perform a hard refresh in the browser (Cmd+Shift+R).

### UI Troubleshooting
- **Sequenced IDs**: If a table uses sequenced primary keys (auto-increment) and you are writing Spock tests that require predictable IDs, use:
  `ec.entity.tempSetSequencedIdPrimary("your.package.EntityName", 960000, 10)` in the `setupSpec()` block. This forces the sequence to start at a known safe value, avoiding test collisions.
- **EntityException during test loads**: If `java -jar moqui.war` was accidentally used, it might corrupt Elasticsearch nodes or H2 file stores. Stop the server, and run `./gradlew cleanDb load loadSave` to completely wipe and recreate the entity data stores from scratch.
- **NoSql Entity Errors**: Moqui sometimes throws NoSql EntityExceptions during the `framework:test` phase (e.g., `Error finding TestNoSqlEntity`). This is often a framework-level noise issue and does not necessarily mean your custom SQL entities are failing. Always check your specific Component test XML reports instead of relying on the global console output.

### Service Troubleshooting & Common Gotchas
- **"Content is not allowed in prolog"**: Check for whitespaces before the `<?xml ... ?>` tag or a missing prolog entirely.
- **Case Sensitivity**: Remember that entity names in `entity-find` and field names are case-sensitive and must match the database definition (`TradeFinanceEntities.xml`).
- **Error Handling in Services**: The XML `<message error="true">` tag adds messages to `messageList` but NOT to `errorList`. This means `ec.message.hasError()` will return `false`. To properly trigger an error state that fails a service or transaction, use script execution: `<script>ec.message.addError("Your error message")</script>`.
- **Entity Find LIKE Queries**: When performing `LIKE` queries with `EntityFind`, use `EntityCondition.LIKE` (e.g., `.condition("fieldName", EntityCondition.LIKE, "PATTERN%")`), NOT `EntityList.LIKE` (which does not exist).
- **XML Conditional Structures (`<if>/<else>`)**: In Moqui XML actions, the `<else>` element MUST be nested *inside* the `<if>` element (e.g., `<if condition="..."><log message="then"/><else><log message="else"/></else></if>`). Putting `<else>` as a sibling after `</if>` will result in the else block being silently ignored.
- **Cascade Deletion**: When deleting records that act as foreign keys for other records (like `LcHistory` depending on `LetterOfCredit`), you must manually cascade delete the child records first using `<entity-delete-by-condition>` before deleting the parent record.
- **The "Read-Refresh-Update" Pattern (CRITICAL)**:
    - **Issue**: When an XML service calls another service (e.g., `transition#Status`) that modifies a record, the local `EntityValue` object in the parent service's `actions` block becomes **stale**. Subsequent updates using that stale object will **overwrite** the changes made by the child service.
    - **Fix**: Always fetch the record fresh *after* any internal service calls that might modify it before performing further logic or updates.
    - **Example**:
      ```xml
      <service-call name="...transition#Status" .../>
      <entity-find-one entity-name="..." value-field="myVal"/> <!-- REFRESH -->
      <set field="myVal.otherField" value="xxx"/>
      <entity-update value-field="myVal"/>
      ```
