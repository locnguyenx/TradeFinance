---
name: operation-protocol
description: Core protocols for reading, writing, and maintaining the project's documentation file, session closing and self-improvement.    
---

## 📋 Operational Protocols

### 1. Date Verification (CRITICAL)
Before modifying ANY documentation file (Memory Bank, Docs, Skills):
1.  
**Check System Date:**
 Run `date` or check system time tool.
2.  
**Update Metadata:**
 Always update `**Last Update:** [YYYY-MM-DD]` fields.
3.  
**NEVER ASSUME DATES.**

### 2. Session Closing Protocol (The "Gardener" Handoff)
Before signaling task completion or ending a session:
1.  **Perform Self-Improvement**: Scan the session for new patterns, fixed errors, or syntax discoveries. Update `.opencode/knowledge/` and `.opencode/skills/` accordingly (see Directive below).
2.  **Update `current-state.md`**: Reflect the latest status.
3.  **Update `progress.md`**: Log completed milestones.
4.  **Update `NOTES_NEXT_SESSION.md`**: 
- Write clear instructions for the "next you".
- If a Session recap & handoff is too old, move it into `progress.md` under section `ARCHIEVED SESSION`

## 🚀 Self-Improvement Directive (The "Gardener")

 You are responsible for maintaining and evolving your own Knowledge Base and Skills. When you discover a new pattern, solution, or fixed error:

### 1. Identify the Destination
- **Recurring Errors/Bugs:** Update `.opencode/knowledge/moqui-errors.json`.
- **Architectural Patterns/Lessons:** Update `moqui-ui-patterns.md`, `moqui-entity-patterns.md`, or `moqui-service-patterns.md`.
- **Tool/Task Specific SOPs:** Update the relevant `SKILL.md` in `.opencode/skills/`.
- **Syntax/XSD Discoveries:** Update `.opencode/knowledge/moqui_syntax_ref.md`.

### 2. Action Rules
- **Direct Update:** Add the knowledge directly to the file using your file-writing tools.
- **Refactor:** If a file becomes too large or disorganized, propose a structural update to the user.
- **Consistency:** Ensure cross-references between rules (passive) and knowledge/skills (active) remain valid.
- **Zero-Touch:** Never store component-specific business logic in global skills. 

**DO NOT create loose files for rules. Curate your `.opencode/` folder.**