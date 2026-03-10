# Notes for Next Session

## Status
- Amendment creation and list view are now fully standardized using the "Dialog & Transition Include" pattern.
- The `Amendments` tab in LC Detail is synchronized with `FindAmendment.xml`.
- BRD updated to reflect these design standards.

## Technical Details
- Reusable templates located in `template/lc/`: `CreateAmendment.xml`, `AmendmentTransitions.xml`.
- Redirection now uses sparse paths (`//${appRoot}/...`) in shared transitions for global reliability.
- List view uses `LcAmendmentDetailView` for consistency.

## Next Steps
- Apply the same "Reusable Dialog" pattern to Drawing creation.
- Resume field expansion for Drawings.
- Update MT707 generator logic to align with the shadow record model.
