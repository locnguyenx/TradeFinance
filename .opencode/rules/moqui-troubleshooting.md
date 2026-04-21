---
paths:
  - "runtime/component/TradeFinance/**/*"
keywords:
  - "error"
  - "bug"
  - "fix"
  - "issue"
  - "troubleshoot"
  - "Content is not allowed in prolog"
  - "Doing nothing for element"
  - "could not find field"
---

# Moqui Troubleshooting & Known Errors

## XML Syntax Errors
- **Symptom:** "Content is not allowed in prolog"
  - **Fix:** Remove any whitespaces or hidden characters before the `<?xml ... ?>` tag at the very top of the file.
- **Symptom:** "Doing nothing for element X"
  - **Fix:** You used an XML tag in a context where the Moqui renderer doesn't support it (e.g., `<row>` inside a widget that only supports `container`). Switch to `<container style="row">`.

## Field & Form Errors
- **Symptom:** "could not find field with name X referred to in a field-ref"
  - **Fix:** The field is implicitly defined via `auto-parameters` but used in a `<field-layout>`. Explicitly define the field (e.g., `<field name="lcId"><default-field><hidden/></default-field></field>`) *before* the layout block.
- **Symptom:** Empty Form Lists
  - **Fix:** Verify that `<field-ref>` is used inside `<form-list-column>`. Ensure the `list` attribute in `<form-list>` exactly matches the list name produced in `<actions>`.

## UI & State Issues
- **Symptom:** Persistent Tabs remaining on the screen after changing to a popup.
  - **Fix:** This is a Quasar browser cache issue. Instruct the user to perform a hard refresh (Cmd+Shift+R).