---
trigger: glob
globs: **/screen/**/*.xml
---

# Moqui Screen Routing & Navigation Standards

## 1. Navigation Paths & URLs
- **Pathing (CRITICAL):** Use RELATIVE paths (e.g., `../../Lc/MainLC`) accounting for directory depth. AVOID double-slash absolute paths (`//ImportLc/...`) unless targeting the root.
- **URL Type:** Always use `url-type="screen"` for linking to other screens.
- **Back Button:** Always use the caption `Back`. Base style: `q-btn-flat text-grey-7`.

## 2. Parent Screen & Subscreen Pattern
- **Panel Types:** Use `<subscreens-panel type="popup"/>` for main module navigation. Use `<subscreens-panel type="tab"/>` for internal record views (do NOT use legacy `<subscreens-tabs/>`).
- **Default Routing:** Always define a `default-item` in `<subscreens>` to prevent initial module load errors.
- **Naming Conventions:** Physical files use PascalCase (`ImportLcList.xml`). URL/appRoot uses kebab-case (`trade-finance`). Set `appRoot` in `<always-actions>`.

## 3. Context & Parameter Preservation
- **Preservation:** When navigating away, pass `lastScreenUrl`. Target screens must use this variable in their close link: `<link url="${lastScreenUrl ?: '.'}" .../>`.
- **Selective Passing:** If `lastScreenUrl` contains "Find", explicitly clear child sequence IDs in the `parameter-map` so the parent returns to a clean list. Initialize variables (e.g., `lcIdToPass = null`) in `<actions>` to prevent stale scope data.

## 4. Safe Subscreen Detection (CRITICAL)
- **Problem:** When accessing a parent URL, the `targetScreen` name is the parent, not the default child. A simple `!= 'FindX'` check fails, leaking tabs into list views.
- **Pattern:** Use a list of excluded screens and the Groovy safe-navigation operator: 
  `condition="lcId &amp;&amp; !['FindLc', 'Lc'].contains(sri.screenUrlInfo.targetScreen?.getScreenName())"`