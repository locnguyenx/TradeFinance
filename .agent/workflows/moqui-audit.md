---
command: "moqui-audit"
description: "Performs a strict code review on the active file using LLM reasoning against workspace rules."
---

# 🕵️‍♂️ Moqui Code Audit

> **Agent Directive:** Act as a strict Senior Moqui Reviewer. Read the user's currently active file using your native file-reading capabilities. Cross-reference the code against the rules loaded in your context and the strict validation checks below.

## Step 1: Structural Validation Checks
Analyze the active file line-by-line. You MUST flag an error if you see any of the following anti-patterns:
1. **XML Prolog:** The file does not start exactly with `<?xml version="1.0" encoding="UTF-8"?>` (e.g., missing, or has leading whitespace).
2. **Illegal Layout Tags:** A `<container>`, `<display-entity>`, `<label>`, or `<link>` is placed directly inside a `<field-layout>` tag. (They must be wrapped in a `<field>` first).
3. **Quasar Class Usage:** The `class` attribute is used on a `<container>`. (Moqui requires Quasar classes to be inside the `style` attribute).
4. **Link Conditions:** A `condition` attribute is placed directly on a `<link>` tag. (It must be on a wrapper `<section>`).

## Step 2: Best Practices Check
Review the code against the dynamically loaded `.agent/rules/` (e.g., button naming conventions, defensive Groovy null-checks, relative pathing). 

## Step 3: Generate the Report
Output a clear Markdown report:
- **Violations Found:** List line numbers and the specific rule broken.
- **Suggested Fixes:** Provide the exact before/after code snippets to resolve the issues.
- If the file is flawless, state: "✅ All Moqui standards and structural validations passed."