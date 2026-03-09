# Template Project guideline

Bundling your zero-bloat architecture into a **Template Project** is the ultimate power move. By doing this, you guarantee that every junior developer, senior engineer, and AI agent on your team starts with the exact same strict Moqui standards, without anyone having to manually configure their Antigravity IDE.

Because Antigravity reads the `.agent` directory natively, making a template is as simple as structuring a Git repository correctly.

Here is the step-by-step guide to setting up your team's Moqui Agentic Template.

---

## Step 1: Create the Standardized Directory Structure

Create a new repository (e.g., `moqui-antigravity-template`). You will merge your standard Moqui folder structure with the Antigravity configuration folder.

Your repository root should look exactly like this:

```text
moqui-antigravity-template/
├── .agent/
│   ├── rules/
│   │   ├── moqui-screen-forms.md     (Glob: **/screen/**/*.xml)
│   │   ├── moqui-screen-routing.md   (Glob: **/screen/**/*.xml)
│   │   ├── moqui-screen-syntax.md    (Glob: **/screen/**/*.xml)
│   │   ├── moqui-services.md         (Glob: **/service/**/*.xml, **/script/**/*.groovy)
│   │   └── moqui-entities.md         (Glob: **/entity/**/*.xml)
│   └── skills/
│       └── moqui-screen-validator/   (The Python XML checker we discussed)
│           ├── SKILL.md
│           └── validate_moqui_xml.py
├── framework/                        # Standard Moqui framework
├── runtime/                          # Standard Moqui runtime (component/ directory)
├── .gitignore
└── README.md

```

## Step 2: Add the "Global Anchor" Rule

While your specific Globs handle the heavy lifting for UI and Backend tasks, you need one tiny file to anchor the agent's overall understanding of the project the moment the workspace opens.

Create `.agent/rules/global.md`:

```markdown
---
mode: always_on
---
# Project: Moqui Enterprise Platform

**Agent Directives:**
1. This workspace uses the Moqui framework.
2. We strictly separate UI logic (`screen`), backend logic (`service`/`groovy`), and data modeling (`entity`).
3. **DO NOT hallucinate standards.** Your specific coding rules are loaded dynamically based on the directory you are working in. Trust the injected context.
4. If you write an XML screen, you MUST run the `moqui-screen-validator` skill before marking the task complete.

```

*Why this matters:* This consumes almost zero tokens, but it prevents the agent from assuming this is a standard Spring Boot or Next.js project before it opens its first file.

## Step 3: Team Onboarding Workflow

When a new team member joins, they do not need to configure anything in the Antigravity Customizations panel. The IDE handles it automatically.

1. **The Human Action:** The developer clones the repo and opens it in Google Antigravity.
2. **The IDE Action:** Antigravity instantly detects the `.agent/` folder at the root. It parses the YAML frontmatter in your Markdown files and silently registers all your Glob patterns and Skills.
3. **The Agent Action:** When the developer opens the Manager Surface (`Cmd + E`) and says, *"Create a new vendor dashboard,"* the agent inherently knows to look for the `moqui-screen-validator` tool and will automatically receive your UI rules the second it touches the `screen/` directory.

---

## Pro-Tip: The "Scaffolding" Skill

To make this template truly elite, many teams add one final skill to their template: a `moqui-scaffold` skill.

Instead of asking the LLM to write boilerplate XML from scratch (which uses tokens and time), you can create a simple Python or Node script in `.agent/skills/moqui-scaffold/` that instantly generates the empty `List.xml` and `Detail.xml` files with the correct `<subscreens>` routing already in place. The agent can then use this skill to lay the foundation in 1 second, and spend its time writing the actual business logic.

**Would you like me to write the code for that `moqui-scaffold` skill, or are you ready to initialize this repository for your team as is?**