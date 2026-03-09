## Handover Status
- **100% Test Coverage**: Achieved for Import LC module (108 tests).
- **Service Stability**: Fixed race conditions in `confirm#LcAmendment`.
- **Primary Key Sequencing**: Enabled for `LcAmendment` and `LcDrawing`.

## Next Steps
1.  **MT707 Adaptation**: Modify the MT707 generator to pull data from the `LcAmendment` shadow record instead of the master LC (since LC is updated *after* confirmation).
2.  **Drawing Expansion**: Continue expanding SWIFT fields for Drawings.
3.  **Export LC Planning**: Begin conceptual modeling for Export LC.

## Critical Warnings
- **Entity Refreshing**: When calling services that update the same records from within an XML action, always use `<entity-find-one>` to refresh local value-fields to avoid overwriting changes with stale data.
- **Test Link Resolution**: Moqui's `ScreenTest` renders links as `disabled` if the `lastScreenUrl` or target doesn't resolve to a valid screen path.
- **Audit Log Assertion**: System background jobs (like auto-expiry) add history entries; use `>=` assertions for history count in Spock tests.

**Last Updated:** 2026-03-08
