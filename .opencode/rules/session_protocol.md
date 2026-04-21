# Memory Bank Protocol (The Brain)

This skill governs how the Agent (You) interacts with the project's long-term memory system and defines the core operational rules.

## 🧠 Memory Bank Architecture

The 
**Memory Bank**
 is your single source of truth. It persists context between sessions. 
**You have NO other memory.**

### Location
`.opencode/memory-bank/` at root of workspace

### Core Files (Reading Order)
When starting a task or session, you MUST read these files in order to "boot up" your context:

1.  
**`memory-bank/core/current-state.md`**
: 🎯 
**THE NOW**
. What is happening, active phase, tasks.
2.  
**`memory-bank/core/projectbrief.md`**
: 📋 
**THE MISSION**
. What we are building and why.
3.  
**`memory-bank/core/productContext.md`**
: 👥 
**THE USER**
. Who we are selling to.
4.  
**`memory-bank/technical/techContext.md`**
: 🔧 
**THE TOOLS**
. Stack versions and config.
5.  
**`memory-bank/technical/systemPatterns.md`**
: 🏗️ 
**THE PATTERNS**
. Architecture rules.
6.  
**`memory-bank/core/progress.md`**
: 📊 
**THE HISTORY**
. What has been done.
7.  
**`memory-bank/NOTES_NEXT_SESSION.md`**
: 📝 
**THE HANDOVER**

## 📋 Operational Protocols

### English Only Code
*   
**Code/Comments:**
 100% English. No exceptions.
*   
**UI/Content:**
 100% English (MVP).
*   
**Variable Names:**
 English (e.g., `getFlow`, not `obtenerFlujo`).
*   
**Memory Bank Files:**
 100% English.


### Automated Exit Protocol (Mandatory)
- **Trigger:** Before signaling task completion or ending a session, you MUST: Initiate the Session-Closing and Self-Improvement Protocol
- **Enforcement:** If the user approves the end of a session by says something like "goodbye" or "done" or "final session" you must perform these steps before your final response.
