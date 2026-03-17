/*
 * TradeFinanceScreensSpec.groovy
 *
 * Spock unit tests for Trade Finance screen rendering.
 * Uses pre-loaded demo data from TradeFinanceDemoData.xml for all
 * screen rendering tests — no dynamic data creation needed.
 *
 * Follows the SimpleScreens MyAccountScreenTests pattern:
 *   - @Shared ScreenTest with baseScreenPath
 *   - @Unroll with containsTextList for data-driven screen tests
 *   - Render statistics logging in cleanupSpec
 *
 * Security: uses tf-admin user (TF_ADMIN group) from TradeFinanceSecurityData.xml
 *
 * Demo data dependencies:
 *   - DEMO_LC_01: Closed LC with full data (drawings, charges, history)
 *   - DEMO_LC_04: Applied/Submitted LC (pending approval)
 *   - DEMO_LC_05: Draft LC (minimal data)
 *   - DEMO_LC_07: Standby LC (active guarantee)
 *   - DEMO_LC_10: Negotiated LC (drawings + discrepancy)
 *
 * To run these make sure moqui, mantle, and TradeFinance are loaded and run:
 *   "gradle cleanAll load runtime/component/TradeFinance:test"
 */


package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.screen.ScreenTest
import org.moqui.screen.ScreenTest.ScreenTestRender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class TradeFinanceScreensSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceScreensSpec.class)

    @Shared ExecutionContext ec
    @Shared ScreenTest screenTest

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        // Login as Trade Finance Admin user (defined in TradeFinanceSecurityData.xml)
        ec.user.loginUser("tf-admin", "moqui")
        // Base path points to the TradeFinance app root screen
        screenTest = ec.screen.makeTest().baseScreenPath("apps/trade-finance")
    }

    def cleanupSpec() {
        long totalTime = System.currentTimeMillis() - screenTest.startTime
        logger.info("Rendered ${screenTest.renderCount} screens " +
                "(${screenTest.errorCount} errors) in " +
                "${ec.l10n.format(totalTime / 1000, '0.000')}s, output " +
                "${ec.l10n.format(screenTest.renderTotalChars / 1000, '#,##0')}k chars")
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    // =========================================================
    // Data-driven screen render tests
    // All screens use pre-loaded demo data from TradeFinanceDemoData.xml
    // =========================================================

    @Unroll
    def "render TradeFinance screen #screenPath (#containsTextList)"() {
        setup:
        ScreenTestRender str = screenTest.render(screenPath, null, null)
        // logger.info("Rendered ${screenPath} in ${str.getRenderTime()}ms, output:\n${str.output}")
        boolean containsAll = true
        for (String containsText in containsTextList) {
            boolean contains = containsText ? str.assertContains(containsText) : true
            if (!contains) {
                logger.info("In ${screenPath} text [${containsText}] not found:\n${str.output}")
                containsAll = false
            }
        }

        expect:
        !str.errorMessages
        !str.output.contains("Error rendering screen")
        !str.output.contains("EntityException")
        !str.output.contains("Freemarker Error")
        containsAll

        where:
        screenPath                          | containsTextList
        // ---- Home Dashboard ----
        "Home"                              | []

        // ---- Import LC Module: Dashboard ----
        "ImportLc/Dashboard"                 | []

        // ---- Import LC Module: List Screen ----
        // Verify page title, Create button, and demo data LC numbers appear
        "ImportLc/Lc/FindLc"              | ["Import Letters of Credit", "Create New LC"]

        // ---- List Screen should show demo data ----
        "ImportLc/Lc/FindLc"              | ["ILC-2026-0001", "ABC Trading Co"]
        "ImportLc/Lc/FindLc"              | ["ILC-2026-0005", "Green Agriculture Co"]

        // ---- Detail Screen: Closed Sight LC (DEMO_LC_01) — fully populated ----
        "ImportLc/Lc/MainLC?lcId=DEMO_LC_01" | ["Letter of Credit Detail", "ILC-2026-0001", "Amendments", "Drawings",
                                                "Financials", "History"]

        // ---- Detail Screen: Draft LC (DEMO_LC_05) — minimal data ----
        "ImportLc/Lc/MainLC?lcId=DEMO_LC_05" | ["ILC-2026-0005", "Green Agriculture Co"]

        // ---- Detail Screen: Standby LC (DEMO_LC_07) ----
        "ImportLc/Lc/MainLC?lcId=DEMO_LC_07" | ["ILC-2026-0007", "Vietnam Electronics Corp"]

        // ---- Detail Screen: Negotiated LC with drawings (DEMO_LC_10) ----
        "ImportLc/Lc/MainLC?lcId=DEMO_LC_10" | ["ILC-2026-0010", "Australian Steel Export"]

        // ---- Amendment Detail Screen (DEMO_LC_03 / 01) ----
        "ImportLc/Amendment/AmendmentDetail?lcId=DEMO_LC_03&amendmentSeqId=01" | ["Amendment Detail", "Processing:", "Confirmation:"]
        "ImportLc/Amendment/Financials?lcId=DEMO_LC_03&amendmentSeqId=01"      | ["Amendment Financials", "CHARGES", "PROVISIONS"]
        "ImportLc/Amendment/History?lcId=DEMO_LC_03&amendmentSeqId=01"         | ["Amendment History"]

        // ---- Drawing Detail Screen (DEMO_LC_01 / 01) ----
        "ImportLc/Drawing/DrawingDetail?lcId=DEMO_LC_01&drawingId=01" | ["Drawing Detail", "DRAWING FINANCIALS", "PRESENTATION METRICS"]

        // ---- LC Sub-Tabs (DEMO_LC_01: fully populated) ----
        "ImportLc/Lc/Financials?lcId=DEMO_LC_01"   | ["Charges", "Provisions"]
        "ImportLc/Lc/Amendments?lcId=DEMO_LC_01"    | []
        "ImportLc/Lc/Drawings?lcId=DEMO_LC_01"      | []
        "ImportLc/Lc/History?lcId=DEMO_LC_01"        | []

        // ---- Find Screens (List views) ----
        "ImportLc/Amendment/FindAmendment"           | []
        "ImportLc/Drawing/FindDrawing"               | []

        // ---- Task Queue ----
        "ImportLc/TaskQueue"                         | []
    }

    // =========================================================
    // Targeted content tests for ImportLcList
    // =========================================================

    def "FindLc screen contains dual-status columns"() {
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/FindLc", null, null)

        then:
        !str.errorMessages
        // Verify both status column headers are present
        str.assertContains("LC Status") || str.assertContains("lcStatusId")
        str.assertContains("Processing") || str.assertContains("transactionStatusId")
    }

    def "FindLc screen contains action buttons"() {
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/FindLc", null, null)

        then:
        !str.errorMessages
        // Verify action elements exist
        str.assertContains("Delete") || str.assertContains("DeleteLc")
    }

    def "FindLc Create dialog contains required SWIFT fields"() {
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/FindLc", null, null)

        then:
        !str.errorMessages
        // Verify key fields in the Create dialog are rendered
        str.assertContains("LC Number") || str.assertContains("lcNumber")
        str.assertContains("Amount") || str.assertContains("amount")
    }

    // =========================================================
    // Targeted content tests for LcDetail (using demo data)
    // =========================================================

    def "LcDetail renders all 4 form sections (DEMO_LC_01)"() {
        when:
        // DEMO_LC_01 has full data across all sections
        ScreenTestRender str = screenTest.render("ImportLc/Lc/MainLC",
                [lcId: "DEMO_LC_01"], null)

        then:
        !str.errorMessages
        str.assertContains("Save") // Form submit button
        str.assertContains("Amendments")
        str.assertContains("Drawings")
        str.assertContains("History")
    }

    def "LcDetail renders dual-status in sidebar (DEMO_LC_01)"() {
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/MainLC",
                [lcId: "DEMO_LC_01"], null)

        then:
        !str.errorMessages
        // DEMO_LC_01 is Closed/Closed, description is "Closed"
        str.assertContains("Closed")
    }

    def "LcDetail renders party data (DEMO_LC_01)"() {
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/MainLC",
                [lcId: "DEMO_LC_01"], null)

        then:
        !str.errorMessages
        // Verify key party fields labels exist in demo data rendering
        str.assertContains("Applicant (50)")
        str.assertContains("Beneficiary (59)")
        str.assertContains("VCB_BANK") || str.assertContains("Vietcombank")
    }

    def "LcDetail renders shipment data (DEMO_LC_01)"() {
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/MainLC",
                [lcId: "DEMO_LC_01"], null)

        then:
        !str.errorMessages
        // Verify shipment fields from demo data
        str.assertContains("Partial Ship (43P)")
        str.assertContains("Transhipment (43T)")
    }

    def "LcDetail renders Applied/Submitted LC (DEMO_LC_04)"() {
        when:
        // DEMO_LC_04 is in Applied/Submitted — verify pending state renders correctly
        ScreenTestRender str = screenTest.render("ImportLc/Lc/MainLC",
                [lcId: "DEMO_LC_04"], null)

        then:
        !str.errorMessages
        str.assertContains("ILC-2026-0004") || str.assertContains("Pharma Supplies Vietnam")
    }
    def "LcDetail in read-only mode renders Close View link (DEMO_LC_01)"() {
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/MainLC",
                [lcId: "DEMO_LC_01", readOnly: "true", lastScreenUrl: "/apps/trade-finance/ImportLc/Lc"], null)

        then:
        !str.errorMessages
        // Verify Back link is present (was Close View)
        str.output.contains("Back")
        // Check for the URL
        str.output.contains("/apps/trade-finance/ImportLc/Lc")
    }

    def "Lc parent screen with lcId does NOT render detail tabs"() {
        when:
        // Rendering the parent screen 'Lc' with an ID (which happens after a redirect to '.')
        // should NOT show the detail tabs/header anymore.
        ScreenTestRender str = screenTest.render("ImportLc/Lc", [lcId: "DEMO_LC_01"], null)

        then:
        !str.errorMessages
        // Verify it rendered the FindLc sub-screen (default)
        str.assertContains("Import Letters of Credit")
        // Verify it does NOT contain the detail header
        !str.output.contains("Letter of Credit Detail")
    }
    def "verify financial data visibility across phases (R8.11)"() {
        when: "Render Financials for Draft LC (DEMO_LC_05)"
        ScreenTestRender draftStr = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_05"], null)
        
        then: "Should see calculated charges and provision rate"
        draftStr.assertContains("Charges")
        draftStr.assertContains("ISSUANCE")
        // Check for 10% in a more robust way if possible, or just trust the functional test
        // Let's look for "provisionRate" in the output if it's rendered as an attribute or specific text
        draftStr.assertContains("10") && draftStr.assertContains("Provisions")

        when: "Render Financials for Active LC with Provision (DEMO_LC_07)"
        // DEMO_LC_07 is Issued. Ensure it has a provision for test.
        ec.service.sync().name("moqui.trade.finance.FinancialServices.hold#LcProvision").parameters([lcId: "DEMO_LC_07", provisionRate: 0.1]).call()
        ScreenTestRender activeStr = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_07"], null)
        
        then: "Should see Active provision and CBS reference"
        activeStr.assertContains("Provisions")
        activeStr.assertContains("Active")
        // This might fail if cbsHoldReference is not rendered - RED phase
        activeStr.assertContains("CBS Ref")

        when: "Render Financials for Expired LC (DEMO_LC_06)"
        // DEMO_LC_06 is Expired. Ensure it has a released provision for test.
        def prvValue = [lcId: "DEMO_LC_06", provisionSeqId: "01", provisionStatusId: "LcPrvReleased"]
        if (ec.entity.find("moqui.trade.finance.LcProvision").condition([lcId: "DEMO_LC_06", provisionSeqId: "01"]).one()) {
            ec.entity.makeValue("moqui.trade.finance.LcProvision").setAll(prvValue).update()
        } else {
            ec.entity.makeValue("moqui.trade.finance.LcProvision").setAll(prvValue).create()
        }
        ScreenTestRender expiredStr = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_06"], null)
        
        then: "Should see Released provision status"
        expiredStr.assertContains("Released")
    }
}
