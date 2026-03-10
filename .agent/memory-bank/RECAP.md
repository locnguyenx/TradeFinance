# Moqui Session Handoff
**Branch:** feature/fix-lc-status-transition-navigation
**Modified:** 
- **Screens:** `Lc.xml`, `Amendment.xml`, `Drawing.xml` (Fixed redundant tabs and transition redirects).
- **Tests:** `TradeFinanceScreensSpec.groovy` (New exclusion test; updated 'Close View' assertion).
- **Core Agent:** `moqui-testing.md`, `moqui-test-runner/SKILL.md` (Integrated individual test execution instructions).

**Next Step:** Perform manual smoke tests on the "Status Change" flow to ensure the dynamic redirect resolves correctly across all user roles.
