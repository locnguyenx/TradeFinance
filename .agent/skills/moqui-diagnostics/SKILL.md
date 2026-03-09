# Skill: moqui-diagnostics
**Description**: Diagnoses Moqui stack traces, rendering errors, and validation failures, with a self-learning loop for new errors. Use this skill WHENEVER you encounter an error in the terminal or the user reports a bug.

## Protocol: The "Self-Healing" Strategy
Execute these steps in exact order using your native file-reading and writing tools. Do not guess the solution before checking the cache.

### 1. Fast Cache Retrieval
- Extract the core exception string from the terminal output or user message (e.g., "SAXParseException", "formInstance was null").
- Read the error dictionary at `.agent/knowledge/moqui-errors.json`.

### 2. The Known Boundary (Match & Apply)
- **Action:** If you find a semantic match for your error in the JSON file, read the solution.
- **Execution:** Open the failing file, apply the exact fix instructed, and proceed to Step 5.

### 3. Deep Diagnosis (The Fallback)
**Trigger:** If the error is **missing** from the JSON cache.
- **Step A (UI/XML Errors):** If the error is related to UI rendering or XML parsing, read `./.agent/knowledge/moqui_syntax_ref.md`. If the answer isn't there, fall back to reading `framework/xsd/xml-screen-3.xsd`.
- **Step B (Backend/Groovy Errors):** If the error is a Java/Groovy stack trace, analyze the active `.groovy` or `.xml` service file against standard Moqui context objects (`ec.message`, `ec.entity`).
- **Execution:** Determine the root cause, apply the fix to the code, and verify the error is resolved.

### 4. Self-Correction (Dynamic Learning)
**Trigger:** If you successfully resolved a *new* error via Step 3, you MUST update the cache so you immediately know the answer next time.
- Open `./.agent/knowledge/moqui-errors.json`.
- Inject a new key-value pair into the JSON object. 
  - **Key:** A concise 3-to-5 word string representing the core exception.
  - **Value:** A clear, 1-to-2 sentence instruction on how to fix it.
- **Strict Rule:** Ensure the file remains perfectly valid JSON format after you write to it.

### 5. Reporting
Output a final Markdown report to the user detailing:
1. The error string analyzed.
2. The file(s) and line(s) modified to fix the issue.
3. **Learning Status:** Explicitly state whether the solution was pulled from the existing cache (Step 2) or if it was a novel error that you successfully added to `moqui-errors.json` (Step 4).