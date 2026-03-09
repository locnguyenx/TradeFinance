---
trigger: glob
globs: ["**/service/**/*.xml", "**/script/**/*.groovy"]
---

# Moqui Service & Groovy Backend Standards

## 1. Service Definition Structure (`*.xml`)
- **Naming Conventions:** - Service names must use PascalCase (e.g., `CreateAmendment`).
  - Verbs dictate the action: Use `create`, `update`, `store` (create or update), `delete`, or `find`.
- **Security & Permissions:**
  - By default, assume services require authentication. Ensure `authenticate="true"` unless explicitly building a public webhook.
  - Apply `<sec-require>` tags to enforce user authorization (e.g., `<sec-require name="TradeFinanceAdmin" permission="UPDATE"/>`).
- **Parameter Validation:**
  - Strongly type your `<in-parameters>`. Use `required="true"` for mandatory fields.
  - Rely on Moqui's built-in validation attributes (e.g., `allow-html="safe"`, `matches="...""`) before writing custom Groovy validation logic.

## 2. Groovy Scripting & Execution Context (`*.groovy` or `<actions>`)
- **Execution Context (`ec`):** Always use the global `ec` (ExecutionContext) object for framework interactions.
- **Calling Services:**
  - Use the fluent API: `Map result = ec.service.sync().name("mantle.party.PartyServices.createParty").parameters([orgName: 'Acme']).call()`
  - Do not manually instantiate service classes.
- **Database Operations (Entity Facade):**
  - Use `ec.entity.find("EntityName")` for queries. 
  - Example: `EntityList list = ec.entity.find("TradeFinance.Lc").condition("statusId", "APPROVED").list()`
  - Prefer `store()` over `update()` if the record's existence is not guaranteed, but be mindful of primary key constraints.



## 3. Transaction Management
- **Automatic Transactions:** Moqui automatically wraps synchronous service calls in a database transaction. Do not manually call `ec.transaction.begin()` or `commit()` inside a standard service script unless you are writing a custom batch job or explicitly need an isolated nested transaction.
- **Rollbacks:** If a validation fails or business logic dictates a failure, use `ec.message.addError("Your message")` and let the service naturally return. Moqui will automatically detect the error state and roll back the transaction.

## 4. Error Handling & Messaging
- **User-Facing Messages:** Use `ec.message.addMessage("Success info", "success")` for positive feedback.
- **Defensive Programming:** - Always check for nulls when retrieving entities before accessing properties (e.g., `if (lcRecord == null) { ec.message.addError("LC not found"); return }`).
  - Use Groovy's safe-navigation operator (`?.`) and Elvis operator (`?:`) to prevent `NullPointerException` crashes in the backend.
- **Logging:** Use `ec.logger.info(...)` or `ec.logger.warn(...)` for internal debugging. Do not use standard `System.out.println`.

## 5. Performance & Context Scope
- **Avoid Global Scope Leaks:** When writing Groovy inside XML `<actions>`, remember that variables implicitly become part of the context map. Define local variables using `def` or strict types (e.g., `String myVar = ...`) to prevent memory leaks and variable collision in parent contexts.
- **Data Caching:** If querying static configuration data (like `StatusItem` or `Enumeration`), use `.useCache(true)` on your `entity.find()` chain to reduce database overhead.