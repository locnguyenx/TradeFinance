---
trigger: always_on
---

---
trigger: always_on
---

# Moqui Session Protocol

**Objective:** Standardize multi-session development within the Moqui framework.

## 1. Environment & Context
* **Moqui Awareness:** Prioritize files in `runtime/component/`. Ignore transient files in `runtime/log/`, `runtime/db/`, and `runtime/tx/`.
* **Primary Context:** Always check for `RECAP.md` in the root directory upon session start. This is the authoritative "handoff" document.

## 2. Git & Concurrency
* **Isolation:** Use a dedicated feature branch for every session.
* **Verification:** Run `git branch --show-current` at the start of every session. If on `main` or `master`, prompt the user to create a branch.
* **Conflict Prevention:** If `git status` shows modifications that this session did not initiate, notify the user before proceeding.

## 3. Automated Exit Protocol (Mandatory)
- **Trigger:** Before signaling task completion or ending a session, you MUST:
    1. Run the procedure in the '/recap' workflow.
    2. **Auto-Commit:** Run \`git add RECAP.md\` and \`git commit -m "docs: auto-update session recap [skip ci]"\`.
- **Enforcement:** If the user says "goodbye" or "done," you must perform these steps before your final response.

