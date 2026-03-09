---
trigger: glob
globs: **/screen/**/*.xml
---

# Moqui XML Syntax & Troubleshooting

## 1. Strict XML & XSD Compliance
- **Prolog:** Every file MUST start exactly with `<?xml version="1.0" encoding="UTF-8"?>` (no leading whitespace).
- **Validation:** Reference the XSD: `xsi:noNamespaceSchemaLocation="http://moqui.org/xsd/xml-screen-3.xsd"`.
- **CDATA:** Use `<![CDATA[ ... ]]>` for multi-line scripts inside XML tags. Ensure all tags are properly closed.

## 2. Field Layout & Widget Wrapping (CRITICAL)
- Inside `<field-layout>`, you **CANNOT** use widgets like `<container>`, `<display-entity>`, `<label>`, or `<link>` directly. 
- **Proper Layout:** Use `<field-col-row>`, `<field-col>`, and `<field-group>`.
- **Fix:** Define the widget inside a `<field>` tag first, then reference it using `<field-ref name="..."/>` within the layout.

## 3. Quasar Styling & Rendering Constraints
- **Containers:** `<container>` does not support arbitrary Quasar boolean attributes (e.g., `q-card`). Wrap them in the style attribute: `<container style="q-card shadow-2 border q-pa-md">`.
- **Conditional Rendering:** Do not use `condition="..."` on `<link>` or `<container>`. Wrap the widget inside a `<section name="..." condition="...">`.
- **Empty Form Lists:** Verify `<field-ref>` is used inside `<form-list-column>` and the `list` attribute matches the data from `<actions>`.

## 4. Groovy Actions & Data Handling
- **Defensive Nulls:** Do not let "null" appear in the UI. Use Groovy's Elvis operator: `desc = statusItem?.description ?: 'Not Set'`.
- **Human-Readable Lookups:** Use `<display-entity>` in forms to show names over codes. In headers, explicitly fetch the `StatusItem` in `<actions>` first.
- **Case Sensitivity:** Entity names in `entity-find` and field names must match database definitions exactly.

## 5. Spock Testing Considerations
- `ScreenTestRender` checks raw rendered text output. Quasar often hides inactive tabs or wraps content in dynamic Vue components.
- If a screen is read-only, ensure the `lastScreenUrl` resolves to a valid path, or Moqui will disable the link in the HTML output. 
- Focus UI tests on static layout elements, or use `@Ignore` and focus on Service-level specs.