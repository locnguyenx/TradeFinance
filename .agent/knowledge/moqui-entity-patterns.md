# Moqui Entity & Data Patterns

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
