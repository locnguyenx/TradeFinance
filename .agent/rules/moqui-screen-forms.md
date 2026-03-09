---
trigger: glob
globs: **/screen/**/*.xml
---

# Moqui Form & UI Standards

## 1. Button Standards & Placement
- **Record Manipulation:** Use `Create` (initial creation), `Save` (updates - NEVER "Save Changes", "Update", or "Apply"), and `Delete` (styled `text-negative`).
- **Workflow Actions:** - Primary (Submit/Confirm): `bg-primary text-white`
  - Bank Actions (Approve): `bg-teal text-white`
  - Negative Actions (Reject/Cancel): `bg-red-8 text-white`
- **Placement:** Place primary "Save" and workflow actions in the top header via `<field-ref>`. For long forms, repeat "Save" at the bottom.

## 2. Form Patterns
- **Form List:** Use `<form-list>` with `header-dialog="true"`. Inside `<form-list-column>`, always use `<field-ref>`. Have action buttons for Edit/Delete and a popup dialog for Create.
- **Form Detail:** Use `<form-single>`. Include Save and Cancel buttons for editable forms.
- **Pop-up Dialog:** Use `<container-dialog>` with `header-dialog="true"`. Include only required fields (skip auto-generated ones). Match title/subtitle to the entity.
- **Dashboards:** For KPI Cards, use `<container>` with styles like `bg-primary-1 text-primary-10 q-pa-lg rounded-borders`.

## 3. Idiomatic Disabling & UI Status
- **Form Disabling:** Do not manually style buttons as disabled. Add a `condition` to the transition (e.g., `<transition name="..." condition="!isReadOnly">`). The Quasar macros will automatically set `formDisabled=true` for the submit buttons.
- **Premium Status Chips:** Avoid plain text for statuses in headers. Resolve the description and semantic color (e.g., `green-8`, `blue-8`) in `<actions>`, then render: `<label text="${statusDesc}" style="q-chip bg-${statusColor} text-white shadow-1 text-weight-bold q-px-sm"/>`