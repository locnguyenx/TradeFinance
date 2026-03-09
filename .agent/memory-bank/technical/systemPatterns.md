# System Patterns

## Entity Architecture
- **Master-Detail:** `LetterOfCredit` is the hub, linked to `LcAmendment`, `LcDrawing`, `LcCharge`, `LcProvision`.
- **Amendment Architecture:** Uses a "Shadow Record" model in `LcAmendment`. When an amendment is created, the entire current state of the LC is cloned into `LcAmendment` for independent editing.

## Service Patterns
- **create#LcAmendment:** Clones data from the original `LetterOfCredit` to a new `LcAmendment` record (Draft).
- **confirm#LcAmendment:** Upon approval, data from `LcAmendment` is written back to the primary `LetterOfCredit` and the `amendmentNumber` is incremented.
- **Transactions:** Use `transaction-savepoint` for atomicity.

## UI Patterns
- **Modular Dashboard:** Global overview management.
- **Tab Panel:** LC Details divided into functional tabs (MainLC, Amendments, Drawings, Financials) for clarity.
- **Premium Status Chips:** Transaction and Lifecycle statuses must be displayed as color-coded chips (`q-chip`) in the header for visual hierarchy. Use `green-8`, `blue-8`, `orange-8`, and `red-8` for semantic consistency.
- **Safe Subscreen Detection (CRITICAL):** When using subscreens, headers and tabs must be hidden on the list view but shown on detail views. Use the pattern `!['FindLc'].contains(sri.screenUrlInfo.targetScreen?.getScreenName())`. 
  - **DANGER:** Do not include the parent screen name (e.g., `'Lc'`) in the exclusion list, as direct navigation or redirects to the parent with an ID will hide the header.
- **Button Navigation:** Use `style="q-btn bg-primary text-white"` for primary buttons. Avoid idiosyncratic attributes like `btn-type`. Ensure `url-type="screen"` is used for all internal navigation.
- **View Entities:** Use `view-entity` for joining tables (e.g., `LcAmendmentDetailView`) to display centralized information (e.g., LC Number, Applicant Name).

## XML & Framework Constraints
- **Widget Wrapping:** Inside `<field-layout>`, all widgets (`display-entity`, `container`, `link`) must be wrapped in an explicit `<field>` or referenced via `<field-ref>` to prevent `formInstance` null pointer exceptions in the renderer.

## Naming Conventions
- Entity/Screen files: PascalCase (e.g., `FindAmendment.xml`).
- Service: verb-noun (e.g., `create#LcAmendment`).
- View-Entity Aliases: Uppercase (e.g., `AMND`, `LC`).

**Last Updated:** 2026-03-06
