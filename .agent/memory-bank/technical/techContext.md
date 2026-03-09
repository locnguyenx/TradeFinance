# Technical Context

## Technology Stack
- **Framework:** Moqui Framework 3.0.0+
- **Database:** H2 Database (Dev/Test)
- **UI:** Quasar/VueJS, Moqui Screens (XML-based)
- **Languages:** Groovy (Services/Logic), Java (Utils), XML (Entities/Screens/Data)
- **Environment:** MacOS, Gradle for build and dependency management.

## Configuration
- **Component:** `runtime/component/TradeFinance`
- **Entities:** Defined in `TradeFinanceEntities.xml`.
- **Services:** Organized in `AmendmentServices.xml`, `DrawingServices.xml`.
- **Demo Data:** `30_TradeFinanceDemoData.xml` (Schema updated 2026-03-06).

## Development Workflow
- Build and Test: `./gradlew load` for data, `./gradlew test` for suite.
- XSD compliance: Validate against Moqui schemas in `framework/xsd/`.

**Last Updated:** 2026-03-06
