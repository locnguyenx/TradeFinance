---
trigger: always_on
---

# Moqui Session Protocol

**Objective:** Standardize multi-session development within the Moqui framework.

## 1. Environment & Context

* Location of `RECAP.md`: `.agent/memory-bank/` directory

* Location of templates: `.agent/templates/` directory

* **Primary Context:** Always check for `RECAP.md` upon session start. This is the authoritative "handoff" document.

## 2. Git & Concurrency (Isolated Sessions)

* **Isolation:** Use a dedicated feature branch for every session.
* **Verification:** Run `git branch --show-current` at the start of every session. If on `main` or `master`, prompt the user to create a branch.
* **Focus:** Focus edits strictly on Moqui component files; ignore runtime logs, temporary files, or database stores.
* **Conflict Prevention:** If `git status` shows modifications that this session did not initiate, notify the user before proceeding.

## 3. Automated Exit Protocol (Mandatory)
- **DEFINTION OF THE DONE:** a task is completed only when all required tests are passed, or user approves the manual testing result
- **Trigger:** Before signaling task completion or ending a session, you MUST:
    1. Initiate the Self-Improvement Protocol
    2. Run the procedure in the '/recap' workflow.
    3. **Auto-Commit**: Run \`git add .agent/ RECAP.md\` and \`git commit -m "docs: auto-update session recap [skip ci]"\`.
- **Enforcement:** If the user approves the end of a session by says something like "goodbye" or "done" you must perform these steps before your final response.

## 4. Documentation Compliance (Mandatory)
- **Source of Truth:** The files in \`/docs/brd/\` and \`/docs/tds\` are the ultimate authority for this project.
- **Pre-Flight Check:** Before performing any Moqui service creation or entity modification, you MUST:
    1. Read the relevant section of the BRD/Design doc.
    2. Confirm that the proposed code change aligns with the documented architecture.
- **Verification:** If a user request contradicts the Technical Design, you must point out the discrepancy and ask for clarification before proceeding.