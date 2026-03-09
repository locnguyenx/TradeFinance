---
trigger: glob
globs: **/entity/**/*.xml
---

# Moqui Entity & Database Modeling Standards

## 1. Entity Definitions (Tables)
- **Naming Conventions:** - Entity names must use `PascalCase` (e.g., `LetterOfCredit`).
  - Field names must use `camelCase` (e.g., `issueDate`, `applicantName`).
  - Always include a logical `package` attribute representing the module (e.g., `package="tradefinance.lc"`).
- **Primary Keys:**
  - Define exactly one `<field>` with `is-pk="true"`. Moqui natively prefers single-column surrogate keys (typically named `lcId`, `amendmentId`).
- **Audit Stamps:** - Do not manually add fields like `lastUpdatedStamp` or `createdTxStamp`. Moqui's Entity Engine automatically injects and manages these audit fields on every standard entity.

## 2. Field Types & Constraints
- **Data Types:** Use Moqui's semantic types defined in `FieldTypes.xml` (e.g., `id`, `text-short`, `text-medium`, `date`, `timestamp`, `currency-amount`). Do not use raw SQL types like `VARCHAR(255)` or `INT`.
- **Relationships:**
  - Define foreign keys using the `<relationship>` tag (e.g., `<relationship type="one" related="mantle.party.Party" short-alias="party"/>`).
  - Use `short-alias` to make relational queries cleaner in Groovy.

## 3. View Entities (SQL-Free Joins)
- **Structure:** Use `<view-entity>` to create pre-defined joins instead of writing raw SQL in services.
- **Members:** Define the base and joined tables using `<member-entity>`. 
- **Aliases:** Explicitly map the fields you want to expose using `<alias>`. Do not select `*` by default for large joins.
- **Join Logic:** Use `<key-map>` to link members. For `LEFT OUTER JOIN` behavior, ensure you set `join-optional="true"` on the `<member-entity>`.

## 4. Performance & Caching
- **Entity Caching:** Use `cache="true"` on the `<entity>` definition *only* for static or slowly changing data (e.g., `StatusItem`, `Enumeration`, `CountryCode`). Never enable entity-level caching on highly transactional tables like `OrderHeader` or `LcAmendment`.
- **Indexes:** Explicitly define an `<index>` for fields frequently used in search conditions (`ec.entity.find().condition(...)`) that are not primary or foreign keys.

## 5. Seed Data & XML Fixtures
- When creating seed data files (`*Data.xml`), wrap records in `<entity-facade-xml type="seed">`.
- Use `type="seed-initial"` for data that should only be loaded on a completely fresh database build, and `type="seed"` for configuration data that is safe to overwrite/update on subsequent deployments.