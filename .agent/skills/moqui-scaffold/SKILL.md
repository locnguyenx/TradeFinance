# Skill: moqui-scaffold
**Description**: Scaffolds a new Moqui UI module (Parent, Find, and Detail screens) using native file-writing tools. Use this whenever the user asks to create a new UI, module, or screen.

## Execution Instructions
Do not execute a script. Use your native file-writing capabilities to create the following three files in the requested directory. Replace `[Name]` with the requested entity name in PascalCase (e.g., `Vendor`) and `[name]` with the camelCase version (e.g., `vendor`).

**File 1: `[Name].xml` (Parent Screen)**
Write a standard Moqui parent screen. 
- Include transitions for `find[Name]` and `[Name]Detail`.
- Include `<subscreens default-item="Find[Name]">`.
- Include a `<section>` wrapper for safe subscreen detection: `condition="[name]Id && !['Find[Name]', '[Name]'].contains(sri.screenUrlInfo.targetScreen?.getScreenName())"`.

**File 2: `Find[Name].xml` (List Screen)**
Write the list screen.
- Include `<entity-find entity-name="YourNamespace.[Name]" list="[name]List">` in `<actions>`.
- Create `<form-list name="[Name]List" list="[name]List" header-dialog="true">`.
- Add a `<container-dialog>` for creation.
- In row actions, map the ID to keep parent routing clean: `<script>[name]IdToPass = [name]Id</script>`.

**File 3: `[Name]Detail.xml` (Detail Screen)**
Write the detail editing screen.
- Require parameter `[name]Id`.
- Fetch record with `<entity-find-one>`.
- Build a `<form-single>` to update the record.
- Add a back button linking to `${lastScreenUrl ?: '.'}`.