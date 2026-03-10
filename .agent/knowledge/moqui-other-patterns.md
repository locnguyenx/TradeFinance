# Moqui Other Patterns (Integration, Testing, Diagnostics)

## Introduction

Patterns & Lession Learned for common development concerns like security, integration, Testing and Diagnostic

## Common Patterns & Lession Learned

### Security: Password Hashing
Demo user accounts in XML data files require **SHA-256** hashed passwords. 
Example: The password `moqui` is hashed as `d72023cb602fa4815410631f9d45a995`.


### XML: Prolog Requirement
Always ensure screen files start with `<?xml version="1.0" encoding="UTF-8"?>`. If this is missing (common after refactors or manual moves), you will see `SAXParseException: Content is not allowed in prolog`.

## Integration

### Integration Seaming
- **Mocking:** Define strict service contracts for external systems (CBS, Swift). Implement mocks using `<script>` tags that return success and simulated data.


## Testing
### 1. Environment & State
- **The reloadSave Rule:** Every test run MUST be preceded by `./gradlew reloadSave`.
- **Sequence Safety:** Use `ec.entity.tempSetSequencedIdPrimary(..., 960000, 10)` in `setupSpec()` to avoid PK collisions during tests.
- **Resilient Assertions:** Avoid exact size checks (e.g., `history.size() == 5`). Use thresholds (`>= 5`) and `.find { ... }` to account for background audit noise.

### Strict Assertions for Screen Rendering Tests

When rendering Moqui XML screens during unit testing, the framework can often silently swallow critical UI exceptions (like `EntityException` or `SAXParseException`).

While these exceptions are dumped to the `moqui.log` file, the `ScreenTestRender.errorMessages` array may remain empty, causing a test to return a "false positive" PASS.

**Solution**:
When writing JUnit/Spock test specifications for screen rendering, you must explicitly search the returned HTML string (`str.output`) for framework exception keywords.

```groovy
expect:
// Standard check (often insufficient for deep rendering crashes)
!str.errorMessages 

// Explicit strict assertions
!str.output.contains("Error rendering screen")
!str.output.contains("EntityException")
!str.output.contains("Freemarker Error")
containsAll // Verify expected UI text is actually present
```
### Proper Error Assertion in Moqui Services

When testing a Moqui service that is expected to fail with an error, developers often find that `ec.message.hasError()` returns false even when the service aborted.

**Root Cause**: 
In Moqui XML service definitions, the tag `<message error="true">...` appends the string to `messageList`, but **not** to `errorList`. This causes assertions like `ec.message.hasError()` to fail in test specs.

**Solution**:
Instead of the `<message>` element, use a `<script>` block to explicitly invoke `addError`:
```xml
<script>ec.message.addError("Your specific error message")</script>
<return/>
```
This ensures `ec.message.hasError()` correctly registers the failure in the `ExecutionContext`.

### Prevent Double Test Execution
In Moqui, if you define a JUnit5 `@Suite` that references Spock `Specification` classes, Gradle's `test` task might discover and run *both* the suite and the individual classes.

This causes tests to execute twice, which leads to primary key collisions and logical failures when relying on static demo data.

**Solution**:
Configure the `test` block in `build.gradle` to only run the Suite:
```gradle
test {
    useJUnitPlatform {
        filter {
            includeTestsMatching '*Suite'
        }
    }
}
```
