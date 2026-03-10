# Skill: moqui-test-runner
**Description**: Executes Moqui Spock tests safely, parsing the generated HTML/XML reports to diagnose failures. Use this whenever the user asks to "run tests" or "test the module".

## Execution Steps
Do not guess the Gradle command. Use your native tools to execute this strict testing protocol:

> [!IMPORTANT]
> **NEVER** run `java -jar moqui.war` for data loading. This command starts a full web server and keeps it running indefinitely — it is NOT a one-shot data loader.

All data loading and test execution must use `./gradlew` tasks only.

### 1. Environment Verification
- **Read the Law:** Open and read `.agent/knowledge/moqui-testing.md` to refresh your memory on the exact `./gradlew` execution mandates (specifically the `reloadSave` rule).
- **Verify Path:** Ensure you are targeting the correct component for testing (e.g., `TradeFinance`).

### 2. Execution
- Check if there's a running process of this moqui application. If YES then kill it.
- Use your bash/terminal tool to execute the test command. 
- **Required Format:** `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home ./gradlew reloadSave :runtime:component:[ComponentName]:test`
- **Individual Spec (Fast):** To run a single test class (e.g., `TradeFinanceScreensSpec`), append `--tests [PackageName].[ClassName]`:
  `... ./gradlew reloadSave :runtime:component:TradeFinance:test --tests moqui.trade.finance.TradeFinanceScreensSpec`
- *Note:* If the user specifies they changed entity data recently, execute `cleanDb loadSave` first.

> [!CAUTION]
> Do NOT combine `reloadSave` with `load` or `loadSave` in the same command. `reloadSave` restores from the saved snapshot, which would undo the fresh load.

### 3. Parse the Results
Do not rely solely on the terminal output. If a test fails, use your file-reading tools to inspect the actual test reports:
- **XML Output:** Read the detailed failure traces in `runtime/component/[ComponentName]/build/test-results/test/`.
- **Log Check:** Check `runtime/log/moqui.log` for hidden entity or SQL errors that caused the failure.

### 4. Report & Auto-Diagnose
Output a final Markdown report to the user:
- State whether the suite passed or failed.
- If it failed, extract the specific assertion failure from the XML report.
- If the failure matches a known issue in the `moqui-testing.md` knowledge base (like a Type Coercion Trap or a Stale UI Cache), point it out and offer to fix the code.