# Skill: moqui-screen-validator
**Description**: Acts as a strict XML linter and syntax cache. Use this skill to audit your own work immediately after you create or modify a Moqui screen, or when the user explicitly asks for a validation check.

## Protocol: The "Strict-XML" Strategy
Execute these steps in exact order using your native file-reading and writing tools.

### 1. Fast Cache Retrieval
- Read the syntax dictionary at `.agent/knowledge/moqui_syntax_ref.md`. Use this as your primary source of truth for allowed tags and their properties.

### 2. The XSD Boundary Check
- Scan the target XML file. Any tag acting as a direct child of the XML tree (e.g., inside `<widgets>`) MUST be a valid Moqui XSD element.
- **Prohibition:** NEVER allow HTML (e.g., `<div>`) or Quasar (e.g., `<q-btn>`) tags directly in the XML tree. Flag these immediately as SAX Parse Errors.

### 3. Foreign Code Encapsulation Check
- Verify that any Client-Side Code (HTML/Vue/Quasar) or attributes with forbidden characters (like `@click`) are strictly encapsulated using this exact pattern:
  `<text type="html"><![CDATA[ YOUR_CLIENT_CODE_HERE ]]></text>`
- Flag any raw client code that is not wrapped in this escape hatch.

### 4. Deep Verification (The Fallback)
If you encounter a Moqui tag in the target file that is **missing** from the `moqui_syntax_ref.md` cache:
- **Step A (Legality):** Read `framework/xsd/xml-screen-3.xsd` (or the relevant XSD). If the tag is NOT defined there, **STOP** and flag it as an illegal hallucination.
- **Step B (Usage):** If valid, optionally check `runtime/template/screen-macro/` to verify its supported attributes and rendering behavior.

### 5. Self-Correction (Dynamic Learning)
If you successfully validated a new tag via Step 4, you MUST update the cache so you don't have to read the XSD next time.
- Open `./.agent/knowledge/moqui_syntax_ref.md`.
- Append a new row to the markdown table at the bottom of the file in this exact format:
  `| <tag-name> | Allowed Parent | Key Attributes | Notes |`

### 6. Reporting
Output a final Markdown report to the user detailing:
1. Any structural or encapsulation violations found (with the code to fix them).
2. Any new tags you learned and added to the `moqui_syntax_ref.md` cache.
3. If no errors are found, state: "✅ Strict-XML Validation Passed."