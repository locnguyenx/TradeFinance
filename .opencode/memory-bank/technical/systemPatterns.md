# System Patterns

## Entity Architecture
- **Master-Detail:** `LetterOfCredit` is the hub, linked to `LcAmendment`, `LcDrawing`, `LcCharge`, `LcProvision`.
- **Amendment Architecture:** Uses a "Shadow Record" model in `LcAmendment`. When an amendment is created, the entire current state of the LC is cloned into `LcAmendment` for independent editing.

## Service Patterns
- **create#LcAmendment:** Clones data from the original `LetterOfCredit` to a new `LcAmendment` record (Draft).
- **confirm#LcAmendment:** Upon approval, data from `LcAmendment` is written back to the primary `LetterOfCredit` and the `amendmentNumber` is incremented.
- **Transactions:** Use `transaction-savepoint` for atomicity.
- **Refresh Pattern (CRITICAL):** Use `<entity-find-one>` immediately after status transition service calls to refresh the local context from the database and avoid stale data in UI or subsequent logic.

## UI Patterns
- **Modular Dashboard:** Global overview management.
- **Tab Panel:** LC Details divided into functional tabs (MainLC, Amendments, Drawings, Financials) for clarity.
- **Screen Parity & SWIFT Grouping:** Maintain 100% field parity between Master records (`LetterOfCredit`) and Shadow records (`LcAmendment`). Group fields logically (General, Parties, Shipment, Credit/Collateral) and use SWIFT field IDs in labels.
- **Premium Status Chips:** Transaction and Lifecycle statuses must be displayed as color-coded chips (`q-chip`) in the header for visual hierarchy. Use `green-8`, `blue-8`, `orange-8`, and `red-8` for semantic consistency.
- **Safe Subscreen Detection (Regex Guard):** The most robust way to ensure headers are hidden on BOTH search screens and the parent screen while remaining visible on detail tabs is using regex: `matches('Find.*|ParentName')`.
- **Button Navigation:** Use `style="q-btn bg-primary text-white"` for primary buttons. Avoid idiosyncratic attributes like `btn-type`. Ensure `url-type="screen"` is used for all internal navigation.
- **View Entities:** Use `view-entity` for joining tables (e.g., `LcAmendmentDetailView`) to display centralized information (e.g., LC Number, Applicant Name).
- **Reusable Dialogs:** Use `transition-include` and `include-screen` from `template/` to centralize creation logic (e.g., `CreateAmendment.xml`).

## XML & Framework Constraints
- **Widget Wrapping:** Inside `<field-layout>`, all widgets (`display-entity`, `container`, `link`) must be wrapped in an explicit `<field>` or referenced via `<field-ref>` to prevent `formInstance` null pointer exceptions in the renderer.

## Naming Conventions
- Entity/Screen files: PascalCase (e.g., `FindAmendment.xml`).
- Service: verb-noun (e.g., `create#LcAmendment`).
- View-Entity Aliases: Uppercase (e.g., `AMND`, `LC`).

**Last Updated:** 2026-03-11
