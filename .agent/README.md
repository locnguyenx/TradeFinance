# 🚀 Moqui + Google Antigravity Template (Native Agent Architecture)

Welcome to the standardized Moqui development template. This repository is pre-configured with **Google Antigravity** agentic workflows. It ensures that both human developers and AI agents strictly adhere to our team's Moqui UI, routing, and backend standards.



## 🧠 The "Zero-Bloat, Zero-Code" AI Architecture

This project uses a highly optimized agentic architecture. Instead of overwhelming the AI with a massive rulebook or relying on fragile Python scripts, we use **Glob-targeted Rules** and **Instruction-Only Skills**. The agent relies entirely on its native semantic reasoning and built-in file system tools.

### Directory Structure

```text
moqui-antigravity-template/
├── .agent/
│   ├── rules/                 # Passive guardrails loaded dynamically
│   │   ├── global.md          (Always On)
│   │   ├── moqui-screen-*.md  (Glob: **/screen/**/*.xml)
│   │   ├── moqui-services.md  (Glob: **/service/**/*.xml, *.groovy)
│   │   └── moqui-entities.md  (Glob: **/entity/**/*.xml)
│   ├── skills/                # Active Standard Operating Procedures (SOPs)
│   │   ├── moqui-scaffold/
│   │   │   └── SKILL.md       (Instructions for native file generation)
│   │   ├── moqui-screen-validator/
│   │   │   └── SKILL.md       (Instructions for semantic XML linting)
│   │   └── moqui-diagnostics/
│   │       └── SKILL.md       (Instructions for semantic error matching)
│   └── knowledge/             # On-Demand reference data
│       └── moqui-errors.json  (Searched by the diagnostics skill)
├── framework/                 # Standard Moqui framework
├── runtime/                   # Standard Moqui runtime
└── README.md

```

### 1. Rules (Passive Guardrails)

Located in `.agent/rules/`, these Markdown files act as the AI's "Brain." They use **Glob Patterns** to inject context *only* when the agent opens a relevant file, saving thousands of tokens:

* **UI Rules (`screen`):** Enforces Quasar layout rules, button naming (`Save`, not `Update`), and secure subscreen routing.
* **Backend Rules (`service`/`groovy`):** Enforces Execution Context (`ec`) standards, safe-navigation (`?.`), and transaction management.
* **Database Rules (`entity`):** Enforces Moqui data types and SQL-free view-entity structures.

### 2. Skills (Native Agent SOPs)

Located in `.agent/skills/`, these are instruction files that tell the agent *how* to use its native file-reading and writing abilities. There are no Python scripts to maintain.

* **`moqui-scaffold`**: The agent generates perfectly routed Parent, Find, and Detail XML screens by following our boilerplate instructions.
* **`moqui-screen-validator`**: The agent acts as a semantic linter, reading its own XML output to catch XSD layout violations and Quasar styling errors before finishing a task.
* **`moqui-diagnostics`**: When an error occurs, the agent opens our local JSON knowledge base, matches the stack trace using its semantic reasoning, and applies our exact historical fixes.

---

## 🛠️ Quick Start Guide

**For Developers & AI Agents:**

1. Clone this repository and open it in **Google Antigravity**.
2. The IDE will automatically detect the `.agent/` folder and register the Globs and Skills.
3. Open the Manager Surface (`Cmd + E` on Mac, `Ctrl + E` on Windows).
4. **To build a new UI:** Type *"Use the `moqui-scaffold` skill to generate an Equipment tracker module in `runtime/component/my-app/screen/MyApp`."*
5. **To validate your work:** Type *"Run the `moqui-screen-validator` skill on my active file."*
6. **If you encounter a build error:** Paste the stack trace into the chat and say, *"Use `moqui-diagnostics` to find the fix for this."*

## 🛡️ Key Standards Enforced Automatically

* **Strict XML:** No unclosed tags, proper `<![CDATA[ ... ]]>` usage, and no raw widgets directly inside `<field-layout>`.
* **Quasar Styling:** Forcing the use of `style="q-card..."` instead of `class="q-card..."` on containers.
* **Relative Routing:** Blocking absolute paths (`//...`) to prevent "out of application" routing errors.
* **Defensive Groovy:** Eliminating `NullPointerException` risks by enforcing the Elvis (`?:`) and safe-navigation (`?.`) operators.

---
*Note: Do not delete or modify the YAML frontmatter in the `.agent/rules/` files, as the Antigravity IDE relies on them for dynamic token management.*