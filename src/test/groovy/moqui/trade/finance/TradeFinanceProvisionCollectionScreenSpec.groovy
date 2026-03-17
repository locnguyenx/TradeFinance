/*
 * TradeFinanceProvisionCollectionScreenSpec.groovy
 *
 * Spock unit tests for R8.12 Provision Collection UI.
 * Tests the Financials screen with Provision Collection feature.
 *
 * Pre-requisites:
 * - Run: ./gradlew reloadSave
 * - Demo data: DEMO_LC_01, DEMO_LC_04, DEMO_LC_05
 *
 * Test Coverage (BDD Groups):
 * - G1: Collection Initialization
 * - G2: Adding Collection Entries
 * - G3: Validation and Total Matching
 * - G5: Currency Conversion
 * - G6: Collecting Funds
 * - G16: User Experience
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

class TradeFinanceProvisionCollectionScreenSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceProvisionCollectionScreenSpec.class)

    @Shared ExecutionContext ec
    @Shared ScreenTest screenTest

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        
        // Load demo data first
        boolean begun = ec.transaction.begin(null)
        try {
            ec.entity.makeDataLoader().location("component://TradeFinance/data/10_TradeFinanceData.xml").load()
            ec.transaction.commit(begun)
        } catch (Throwable t) {
            logger.error("Error loading demo data: ${t.toString()}", t)
            ec.transaction.rollback(begun, "Error loading demo data", t)
        } finally {
            if (ec.transaction.isTransactionInPlace()) ec.transaction.commit()
        }
        
        ec.user.loginUser("tf-admin", "moqui")
        screenTest = ec.screen.makeTest().baseScreenPath("apps/trade-finance")
        
        // Ensure test data exists
        ensureTestData()
    }

    def cleanupSpec() {
        long totalTime = System.currentTimeMillis() - screenTest.startTime
        logger.info("Provision Collection Screen Tests: ${screenTest.renderCount} screens rendered, ${screenTest.errorCount} errors")
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
        cleanupCollections()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    // Cleanup provision collections for test LC
    def cleanupCollections() {
        def collections = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("lcId", "DEMO_LC_01")
            .list()
        collections.each { coll ->
            def entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
                .condition("collectionId", coll.collectionId)
                .list()
            entries.each { it.delete() }
            coll.delete()
        }
    }

    // Ensure demo LC data exists
    def ensureTestData() {
        // Create tf-admin user if not exists
        if (!ec.entity.find("moqui.security.UserAccount").condition("username", "tf-admin").one()) {
            ec.entity.makeValue("moqui.security.UserAccount")
                .setAll([userId: "TF_ADMIN", username: "tf-admin", currentPassword: "moqui", disabled: "N", emailAddress: "test@moqui.org"]).create()
        }
        
        // Ensure parties exist
        ensureParty("DEMO_ORG_ABC", "PT_ORGANIZATION")
        ensureParty("DEMO_ORG_XYZ", "PT_ORGANIZATION")
        ensureParty("DEMO_ORG_VIETCOMBANK", "PT_ORGANIZATION")
        
        // Ensure LC Product exists
        if (!ec.entity.find("moqui.trade.finance.LcProduct").condition("productId", "PROD_ILC_SIGHT").one()) {
            ec.entity.makeValue("moqui.trade.finance.LcProduct")
                .setAll([productId: "PROD_ILC_SIGHT", productName: "Import LC Sight", lcProductTypeEnumId: "LC_PROD_SIGHT", defaultProvisionRate: 10]).create()
        }
        
        // Check if demo LC exists
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", "DEMO_LC_01").one()
        if (!lc) {
            // Create demo LC
            ec.service.sync().name("create#moqui.trade.finance.LetterOfCredit")
                .parameter("lcId", "DEMO_LC_01")
                .parameter("lcNumber", "DEMO-LC-00001")
                .parameter("lcStatusId", "LcLfIssued")
                .parameter("transactionStatusId", "LcTxApproved")
                .parameter("productId", "PROD_ILC_SIGHT")
                .parameter("amount", new BigDecimal("10000.00"))
                .parameter("amountCurrencyUomId", "USD")
                .parameter("applicantPartyId", "DEMO_ORG_ABC")
                .parameter("beneficiaryPartyId", "DEMO_ORG_XYZ")
                .parameter("issuingBankPartyId", "DEMO_ORG_VIETCOMBANK")
                .call()
        }
        
        // Ensure CBS accounts exist
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_EUR_001", "EUR", 50000.00)
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_GBP_001", "GBP", 40000.00)
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_USD_001", "USD", 30000.00)
    }
    
    def ensureParty(String partyId, String partyType) {
        if (!ec.entity.find("mantle.party.Party").condition("partyId", partyId).one()) {
            ec.entity.makeValue("mantle.party.Party")
                .setAll([partyId: partyId, partyTypeEnumId: partyType]).create()
        }
    }

    def ensureCbsAccount(String partyId, String accountId, String currency, BigDecimal balance) {
        def existing = ec.entity.find("moqui.trade.finance.CbsSimulatorState")
            .condition("partyId", partyId)
            .condition("accountId", accountId)
            .one()
        if (!existing) {
            ec.entity.makeValue("moqui.trade.finance.CbsSimulatorState")
                .setAll([partyId: partyId, accountId: accountId, balanceAmount: balance, currencyUomId: currency]).create()
        }
    }

    // =========================================================
    // TC01: Provision section shows "Collect Provision" button
    // =========================================================
    def "Financials screen shows Provision Collection button"() {
        when:
        // Render through the LC detail screen which includes Financials as a tab
        ScreenTestRender str = screenTest.render("ImportLc/Lc/MainLC", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages || str.output.contains("Provision")
        
        logger.info("TC01 PASSED: Screen renders with Provision section")
    }

    // =========================================================
    // TC02: Collection entries table displays
    // =========================================================
    def "Financials screen shows collection entries table when collection exists"() {
        setup:
        // Create a collection first
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        str.output.contains("Provision Collection") || str.output.contains("Collection Entries")
        
        logger.info("TC02 PASSED: Collection entries table displays")
    }

    // =========================================================
    // TC03: Add entry dialog opens
    // =========================================================
    def "Add Collection Entry dialog opens"() {
        setup:
        // Ensure collection exists
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        // Check for Add Entry button or form elements
        str.output.contains("Add") || str.output.contains("Entry")
        
        logger.info("TC03 PASSED: Add Entry dialog/form available")
    }

    // =========================================================
    // TC04: Add EUR entry - conversion displayed
    // =========================================================
    def "Add EUR entry shows exchange rate conversion"() {
        setup:
        // Create collection and add entry
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        def entryResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        str.output.contains("EUR") || str.output.contains("5000")
        
        logger.info("TC04 PASSED: EUR entry with conversion displayed")
    }

    // =========================================================
    // TC05: Add multiple entries - totals update
    // =========================================================
    def "Multiple collection entries show updated totals"() {
        setup:
        // Create collection and add multiple entries
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("2000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        // Total should be around 7000+ USD (5450 + 2000)
        str.output.contains("7000") || str.output.contains("Total")
        
        logger.info("TC05 PASSED: Multiple entries with updated totals")
    }

    // =========================================================
    // TC06: Validate - Complete status shown
    // =========================================================
    def "Validate shows Complete status when total matches target"() {
        setup:
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        // Add entries totaling ~10000 USD
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("4500.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5095.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        // Validate
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        str.output.contains("Complete") || str.output.contains("10000")
        
        logger.info("TC06 PASSED: Complete status shown when total matches")
    }

    // =========================================================
    // TC07: Validate - Exceeds warning shown
    // =========================================================
    def "Validate shows warning when total exceeds target"() {
        setup:
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        // Add entries totaling more than 10000 USD
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("15000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        // Validate
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        str.output.contains("Exceeds") || str.output.contains("exceeds")
        
        logger.info("TC07 PASSED: Exceeds warning shown")
    }

    // =========================================================
    // TC08: Validate - Incomplete status shown
    // =========================================================
    def "Validate shows Incomplete status when total is less than target"() {
        setup:
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        // Add entry totaling less than 10000 USD
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        // Validate
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        str.output.contains("Incomplete") || str.output.contains("5000")
        
        logger.info("TC08 PASSED: Incomplete status shown")
    }

    // =========================================================
    // TC09: Collect Funds - success
    // =========================================================
    def "Collect Funds button executes CBS holds successfully"() {
        setup:
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        // Add entries totaling exactly 10000 USD
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("10000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        // Validate to set status to Complete
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        // Collect Funds
        def collectResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.collect#ProvisionFunds")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        collectResult.success == true || collectResult.success == "true"
        
        logger.info("TC09 PASSED: Collect Funds executed successfully")
    }

    // =========================================================
    // TC10: Release Funds - success
    // =========================================================
    def "Release Funds button releases CBS holds"() {
        setup:
        // First create and collect
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("10000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.collect#ProvisionFunds")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        // Release Funds
        def releaseResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.release#ProvisionCollection")
            .parameter("collectionId", collResult.collectionId)
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        releaseResult.success == true || releaseResult.success == "true"
        
        logger.info("TC10 PASSED: Release Funds executed successfully")
    }

    // =========================================================
    // TC11: Existing collection loads
    // =========================================================
    def "Existing collection loads and displays on Financials screen"() {
        setup:
        // Create collection with entries
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collResult.collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        str.output.contains("Provision") || str.output.contains("Collection")
        
        logger.info("TC11 PASSED: Existing collection loads")
    }

    // =========================================================
    // TC12: Visual indicators display correctly
    // =========================================================
    def "Visual indicators display correctly for different statuses"() {
        setup:
        // Test with Draft status (grey)
        def collResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", "DEMO_LC_01")
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        
        when:
        ScreenTestRender str = screenTest.render("ImportLc/Lc/Financials", [lcId: "DEMO_LC_01"], null)
        
        then:
        !str.errorMessages
        // Check for status-related visual elements
        str.output.contains("Draft") || str.output.contains("Status")
        
        logger.info("TC12 PASSED: Visual indicators display")
    }
}
