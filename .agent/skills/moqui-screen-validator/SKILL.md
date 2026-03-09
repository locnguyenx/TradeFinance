# Skill: moqui-screen-validator
**Description**: Used in screen design, diagnostics. Acts as a strict XML linter, XSD boundary checker, and syntax cache updater.

## Execution Steps
1. **Knowledge Retrieval:** Read the syntax dictionary at `.agent/knowledge/moqui_syntax_ref.md`.
2. **XSD Boundary Audit:** Scan the target XML file. Ensure no HTML (`<div>`) or Quasar (`<q-btn>`) tags exist directly in the XML tree.
3. **Encapsulation Audit:** Verify any client-side code is wrapped perfectly in `<text type="html"><![CDATA[ ... ]]></text>`.
4. **Deep Verification:** If a Moqui tag is missing from your `moqui_syntax_ref.md` cache:
   - Read `framework/xsd/xml-screen-3.xsd`. If the tag is absent, flag it as an illegal hallucination.
   - Check `runtime/template/screen-macro/` for attribute verification.
5. **Self-Correction:** If you validated a new tag via Step 4, append a new row to the Markdown table in `.agent/knowledge/moqui_syntax_ref.md` detailing the tag, parent, and attributes.
6. **Report:** Output a Markdown summary of violations fixed and/or new tags learned.