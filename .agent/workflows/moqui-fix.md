---
command: "moqui-fix"
description: "Diagnoses terminal/build errors by reading the internal JSON knowledge base and applying the fix."
---

# 🛠️ Moqui Auto-Fix

> **Agent Directive:** When invoked to fix a failing component, do not guess the solution. Use your native tools to consult our specific troubleshooting database first.

## Step 1: Identify the Error
Analyze the recent terminal output, standard error stream, or the user's message. Identify the exact exception string (e.g., `SAXParseException: Content is not allowed in prolog`, or `formInstance was null`).

## Step 2: Consult the Knowledge Base
Use your native file-reading tool to open and read `.agent/knowledge/moqui-errors.json`. 

## Step 3: Match and Resolve
1. Compare your identified error against the keys in the JSON file.
2. **If a match is found:** Read the associated solution. Open the failing Moqui file, apply the exact fix instructed by the JSON, and explain the correction to the user.
3. **If no match is found:** State that the error is not in the knowledge base, and fall back to your general reasoning to propose a fix.