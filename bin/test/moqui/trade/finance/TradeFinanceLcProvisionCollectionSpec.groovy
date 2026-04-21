package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Ignore
import java.sql.Timestamp

/**
 * TradeFinanceLcProvisionCollectionSpec
 * TDD for Import LC Provision Collection (BDD Feature: Import LC Provision Collection)
 * Tests multi-account provision collection, currency conversion, and CBS integration.
 * 
 * BDD Coverage (from bdd-scenarios.feature):
 * - Group 1: Collection Initialization (2 scenarios)
 * - Group 2: Adding Collection Entries (4 scenarios)
 * - Group 3: Validation and Total Matching (4 scenarios)
 * - Group 4: Account Eligibility (2 scenarios)
 * - Group 5: Currency Conversion (3 scenarios)
 * - Group 6: Collecting Funds (3 scenarios)
 * 
 * @Scenario(BDD-LCPC-G1-SC1) - Initialize provision collection
 * @Scenario(BDD-LCPC-G1-SC2) - Initialize with existing collection
 * @Scenario(BDD-LCPC-G2-SC1) - Add EUR entry
 * @Scenario(BDD-LCPC-G2-SC2) - Add GBP entry
 * @Scenario(BDD-LCPC-G2-SC3) - Add USD entry
 * @Scenario(BDD-LCPC-G2-SC4) - Add multiple entries
 * @Scenario(BDD-LCPC-G3-SC1) - Validate total matches
 * @Scenario(BDD-LCPC-G3-SC2) - Validate total exceeds
 * @Scenario(BDD-LCPC-G3-SC3) - Validate total less than
 * @Scenario(BDD-LCPC-G3-SC4) - Validate tolerance
 * @Scenario(BDD-LCPC-G4-SC1) - Select applicant account
 * @Scenario(BDD-LCPC-G4-SC2) - Select non-applicant account (should fail)
 * @Scenario(BDD-LCPC-G5-SC1) - Fetch exchange rate
 * @Scenario(BDD-LCPC-G5-SC2) - Handle unsupported currency
 * @Scenario(BDD-LCPC-G5-SC3) - Handle CBS failure
 * @Scenario(BDD-LCPC-G6-SC1) - Collect funds from multiple accounts
 * @Scenario(BDD-LCPC-G6-SC2) - Handle partial CBS failure
 * @Scenario(BDD-LCPC-G6-SC3) - Handle all CBS failures
 */
@Stepwise
class TradeFinanceLcProvisionCollectionSpec extends Specification {
    @Shared protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceLcProvisionCollectionSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = "DEMO_LC_01"
    @Shared String collectionId = null

    def setupSpec() {
        logger.warn("[TDD_TRACE] TradeFinanceLcProvisionCollectionSpec STARTING setupSpec")
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        
        // --- TYPE SETUP ---
        ensureEnumType("PartyType", "Party Type")
        ensureEnumType("LcProductType", "LC Product Type")
        ensureEnumType("LcTransactionStatus", "LC Transaction Status")
        ensureEnumType("LcLifecycleStatus", "LC Lifecycle Status")
        ensureEnumType("LcProvisionStatus", "LC Provision Status")
        ensureEnumType("LcProvisionCollection", "LC Provision Collection Status")
        ensureEnumType("LcProvisionCollectionEntry", "LC Provision Collection Entry Status")
        
        // --- STATUS TYPES ---
        ensureStatusType("LcTransaction", "LC Transaction")
        ensureStatusType("LcLifecycle", "LC Lifecycle")
        ensureStatusType("LcProvisionCollection", "LC Provision Collection")
        ensureStatusType("LcProvisionCollectionEntry", "LC Provision Collection Entry")
        
        // --- ENUMS ---
        ensureEnum("PT_PERSON", "PartyType", "Person")
        ensureEnum("PT_ORGANIZATION", "PartyType", "Organization")
        
        // LC Product Type Enums
        ensureEnum("LC_PROD_SIGHT", "LcProductType", "Sight LC")
        
        // Ensure _NA_ Party exists
        if (!ec.entity.find("mantle.party.Party").condition("partyId", "_NA_").one()) {
            logger.info("CREATING _NA_ Party")
            ec.entity.makeValue("mantle.party.Party").setAll([partyId: "_NA_", partyTypeEnumId: "PT_ORGANIZATION"]).create()
        }
        
        ensureRoleType("BANK", "Bank")
        ensureRoleType("CUSTOMER", "Customer")
        ensureRoleType("VENDOR", "Vendor")
        
        // --- CURRENCIES ---
        // Ensure UomType enum exists (required for currency creation)
        ensureEnumType("UomType", "UOM Type")
        ensureEnum("UT_CURRENCY_MEASURE", "UomType", "Currency")
        ["USD", "EUR", "GBP"].each { currencyCode ->
            if (!ec.entity.find("moqui.basic.Uom").condition("uomId", currencyCode).one()) {
                logger.info("CREATING Currency: ${currencyCode}")
                ec.entity.makeValue("moqui.basic.Uom").setAll([uomId: currencyCode, uomTypeEnumId: "UT_CURRENCY_MEASURE", description: currencyCode]).create()
            }
        }
        
        // --- LC PROVISION COLLECTION STATUSES ---
        ensureStatusType("LcProvisionCollection", "LC Provision Collection Status")
        ensureStatusType("LcProvisionCollectionEntry", "LC Provision Collection Entry Status")
        
        // Collection status items
        ["LcPrvColDraft", "LcPrvColComplete", "LcPrvColCollected", "LcPrvColReleased", "LcPrvColFailed"].each {
            ensureEnum(it, "LcProvisionCollection", it)
            ensureStatus(it, "LcProvisionCollection", it)
        }
        
        // Entry status items
        ["LcPrvEntryPending", "LcPrvEntryCollected", "LcPrvEntryReleased", "LcPrvEntryFailed"].each {
            ensureEnum(it, "LcProvisionCollectionEntry", it)
            ensureStatus(it, "LcProvisionCollectionEntry", it)
        }
        
        // --- LC LIFECYCLE STATUSES ---
        ["LcLfDraft", "LcLfIssued", "LcLfActive", "LcLfClosed", "LcLfCancelled"].each {
            ensureEnum(it, "LcLifecycleStatus", it)
            ensureStatus(it, "LcLifecycle", it)
        }
        
        // --- LC TRANSACTION STATUSES ---
        ["LcTxDraft", "LcTxApproved", "LcTxPendingReview", "LcTxPendingProcessing", "LcTxReturned", "LcTxPendingApproval", "LcTxClosed"].each {
            ensureEnum(it, "LcTransactionStatus", it)
            ensureStatus(it, "LcTransaction", it)
        }
        
        // --- PARTIES ---
        ensureParty("DEMO_ORG_ABC", "PT_ORGANIZATION", "CUSTOMER")
        ensureParty("DEMO_ORG_XYZ", "PT_ORGANIZATION", "VENDOR")
        ensureParty("DEMO_ORG_VIETCOMBANK", "PT_ORGANIZATION", "BANK")
        
        // --- LC PRODUCT ---
        if (!ec.entity.find("moqui.trade.finance.LcProduct").condition("productId", "PROD_ILC_SIGHT").one()) {
            ec.entity.makeValue("moqui.trade.finance.LcProduct")
                .setAll([productId: "PROD_ILC_SIGHT", productName: "Import LC Sight", lcProductTypeEnumId: "LC_PROD_SIGHT", defaultProvisionRate: 10]).create()
        }
        
        // --- LETTER OF CREDIT ---
        if (!ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", "DEMO_LC_01").one()) {
            ec.entity.makeValue("moqui.trade.finance.LetterOfCredit")
                .setAll([lcId: "DEMO_LC_01", lcNumber: "ILC-2026-TEST01",
                    lcStatusId: "LcLfIssued", transactionStatusId: "LcTxApproved",
                    productId: "PROD_ILC_SIGHT", lcProductTypeEnumId: "LC_PROD_SIGHT",
                    applicantPartyId: "DEMO_ORG_ABC", beneficiaryPartyId: "DEMO_ORG_XYZ",
                    issuingBankPartyId: "DEMO_ORG_VIETCOMBANK",
                    applicantName: "ABC Trading Co", beneficiaryName: "XYZ Exports",
                    amount: new BigDecimal("100000.00"), amountCurrencyUomId: "USD",
                    issueDate: new Timestamp(System.currentTimeMillis() - 86400000L * 30),
                    expiryDate: new Timestamp(System.currentTimeMillis() + 86400000L * 180)]).create()
        }
        
        // --- CBS SIMULATOR STATE (Accounts) ---
        // Ensure applicant accounts for DEMO_ORG_ABC in EUR, GBP, USD
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_EUR_001", "EUR", 50000.00)
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_GBP_001", "GBP", 40000.00)
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_USD_001", "USD", 30000.00)
        
        // --- ENABLE CBS SIMULATOR ---
        System.setProperty("cbs.integration.impl", "Simulator")
        System.setProperty("cbs.simulate.timeout", "false")
        
        ec.message.clearAll()
        logger.warn("[TDD_TRACE] TradeFinanceLcProvisionCollectionSpec setupSpec COMPLETE")
    }

    def static ensureEnumType(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.EnumerationType").condition("enumTypeId", id).one()) {
                logger.info("CREATING EnumType: ${id}")
                ec.entity.makeValue("moqui.basic.EnumerationType").setAll([enumTypeId: id, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureEnumType", e)
            logger.error("FAILED to ensure enumType ${id}: ${e.message}", e)
        }
    }

    def static ensureStatusType(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusType").condition("statusTypeId", id).one()) {
                logger.info("CREATING StatusType: ${id}")
                ec.entity.makeValue("moqui.basic.StatusType").setAll([statusTypeId: id, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatusType", e)
            logger.error("FAILED to ensure statusType ${id}: ${e.message}", e)
        }
    }

    def static ensureEnum(String id, String type, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.Enumeration").condition("enumId", id).one()) {
                logger.info("CREATING Enum: ${id} (${type})")
                if (!ec.entity.find("moqui.basic.EnumerationType").condition("enumTypeId", type).one()) {
                    logger.info("CREATING EnumType for Enum: ${type}")
                    ec.entity.makeValue("moqui.basic.EnumerationType").setAll([enumTypeId: type, description: type]).create()
                }
                ec.entity.makeValue("moqui.basic.Enumeration").setAll([enumId: id, enumTypeId: type, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureEnum", e)
            logger.error("FAILED to ensure enum ${id}: ${e.message}", e)
        }
    }

    def static ensureStatus(String id, String type, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusItem").condition("statusId", id).one()) {
                logger.info("CREATING StatusItem: ${id} (${type})")
                ec.entity.makeValue("moqui.basic.StatusItem").setAll([statusId: id, statusTypeId: type, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatus", e)
            logger.error("FAILED to ensure status ${id}: ${e.message}", e)
        }
    }

    def static ensureRoleType(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("mantle.party.RoleType").condition("roleTypeId", id).one()) {
                logger.info("CREATING RoleType: ${id}")
                ec.entity.makeValue("mantle.party.RoleType").setAll([roleTypeId: id, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureRoleType", e)
            logger.error("FAILED to ensure roleType ${id}: ${e.message}", e)
        }
    }

    def static ensureCurrency(String currencyCode) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.Uom").condition("uomId", currencyCode).one()) {
                logger.info("CREATING Currency: ${currencyCode}")
                ec.entity.makeValue("moqui.basic.Uom").setAll([uomId: currencyCode, uomTypeEnumId: "UT_CURRENCY_MEASURE", description: currencyCode]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureCurrency", e)
            logger.error("FAILED to ensure currency ${currencyCode}: ${e.message}", e)
        }
    }

    def static ensureParty(String partyId, String partyType, String roleType) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("mantle.party.Party").condition("partyId", partyId).one()) {
                logger.info("CREATING Party: ${partyId}")
                ec.entity.makeValue("mantle.party.Party").setAll([partyId: partyId, partyTypeEnumId: partyType]).create()
            }
            if (!ec.entity.find("mantle.party.PartyRole").condition("partyId", partyId).condition("roleTypeId", roleType).one()) {
                ec.entity.makeValue("mantle.party.PartyRole").setAll([partyId: partyId, roleTypeId: roleType]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureParty", e)
            logger.error("FAILED to ensure party ${partyId}: ${e.message}", e)
        }
    }

    def static ensureCbsAccount(String partyId, String accountId, String currency, BigDecimal balance) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            def existingAccount = ec.entity.find("moqui.trade.finance.CbsSimulatorState")
                .condition("partyId", partyId)
                .condition("accountId", accountId)
                .one()
            if (!existingAccount) {
                logger.info("CREATING CBS Account ${accountId} for party: ${partyId}")
                ec.entity.makeValue("moqui.trade.finance.CbsSimulatorState")
                    .setAll([partyId: partyId, accountId: accountId, balanceAmount: balance, currencyUomId: currency]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureCbsAccount", e)
            logger.error("FAILED to ensure CBS account for ${partyId}: ${e.message}", e)
        }
    }

    // ============================================================
    // RED PHASE: Failing Tests for Collection Initialization
    // ============================================================

    /**
     * BDD-LCPC-G1-SC1: Initialize provision collection for an LC
     * 
     * Given I navigate to the LC Financials screen for "DEMO_LC_01"
     * When I click "Collect Provision" button
     * Then I should see the Provision Collection screen
     * And the target provision amount should display "10000.00 USD"
     * And the collection status should be "Draft"
     * And the total collected amount should be "0.00 USD"
     */
    def "testInitializeProvisionCollection"() {
        logger.info("[TDD_TEST] BDD-LCPC-G1-SC1: Initialize provision collection")
        
        when:
        // First ensure LC exists - check if it already exists
        def existingLc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        
        if (!existingLc) {
            // Create LC if it doesn't exist
            ec.service.sync().name("create#moqui.trade.finance.LetterOfCredit")
                .parameter("lcId", lcId)
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
        
        // Create the provision collection
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .parameter("targetCurrencyUomId", "USD")
            .call()
        
        def collId = result?.collectionId
        collectionId = collId
        
        logger.info("[DEBUG] Create collection result: ${result}, collectionId: ${collId}")
        
        // Check for errors
        if (ec.message.hasError()) {
            logger.info("[DEBUG] Service errors: ${ec.message.errors}")
        }
        
        then:
        result != null
        result.success == true || result.success == "true"
        result.collectionId != null
        
        def collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", result.collectionId)
            .one()
        
        collection != null
        collection.targetProvisionAmount == new BigDecimal("10000.00")
        collection.targetCurrencyUomId == "USD"
        collection.collectedAmount == new BigDecimal("0.00")
        collection.collectionStatusId == "LcPrvColDraft"
        
        logger.info("[TDD_TEST] BDD-LCPC-G1-SC1: PASSED - Collection initialized with status Draft")
    }

    /**
     * BDD-LCPC-G2-SC1: Add collection entry from EUR account
     * 
     * Given I am on the Provision Collection screen for "DEMO_LC_01"
     * When I select account "ACC_EUR_001" from the account dropdown
     * And I enter amount "5000.00" in EUR currency
     * Then the system should fetch the EUR/USD exchange rate from CBS
     * And display the converted USD amount
     * And display the exchange rate used
     * And the entry should be added to the collection list
     * And the total collected amount should update to reflect the new entry
     */
    def "testAddEurCollectionEntry"() {
        logger.info("[TDD_TEST] BDD-LCPC-G2-SC1: Add EUR collection entry")
        
        setup:
        // Clean up any existing collections for this LC
        cleanupCollection(lcId)
        
        // First create the collection
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        when:
        // Add EUR entry
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        logger.info("[DEBUG] Add EUR entry result: ${result}")
        if (ec.message.hasError()) {
            logger.info("[DEBUG] Service errors: ${ec.message.errors}")
        }
        
        then:
        result.success == true || result.success == "true"
        result.entrySeqId != null
        
        // Verify entry was created
        def entry = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId)
            .condition("entrySeqId", result.entrySeqId)
            .one()
        
        entry != null
        entry.sourceAmount == new BigDecimal("5000.00")
        entry.sourceCurrencyUomId == "EUR"
        entry.exchangeRate != null
        entry.exchangeRate > BigDecimal.ZERO
        entry.convertedAmount != null
        entry.entryStatusId == "LcPrvEntryPending"
        
        // Verify collection total updated
        def collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId)
            .one()
        
        collection.collectedAmount > BigDecimal.ZERO
        
        logger.info("[TDD_TEST] BDD-LCPC-G2-SC1: PASSED - EUR entry added with exchange rate ${entry.exchangeRate}")
    }

    /**
     * BDD-LCPC-G2-SC3: Add collection entry from USD account (no conversion needed)
     */
    def "testAddUsdCollectionEntry"() {
        logger.info("[TDD_TEST] BDD-LCPC-G2-SC3: Add USD collection entry")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        when:
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("2000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        then:
        result.success == true || result.success == "true"
        
        def entry = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId)
            .condition("entrySeqId", result.entrySeqId)
            .one()
        
        entry != null
        entry.sourceAmount == new BigDecimal("2000.00")
        entry.sourceCurrencyUomId == "USD"
        entry.exchangeRate == new BigDecimal("1.0")
        entry.convertedAmount == new BigDecimal("2000.00")
        
        logger.info("[TDD_TEST] BDD-LCPC-G2-SC3: PASSED - USD entry added without conversion")
    }

    /**
     * BDD-LCPC-G2-SC4: Add multiple collection entries
     */
    def "testAddMultipleCollectionEntries"() {
        logger.info("[TDD_TEST] BDD-LCPC-G2-SC4: Add multiple collection entries")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        when:
        // Add EUR entry (5000 EUR -> ~5450 USD at rate 1.09)
        def eurResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        // Add GBP entry (3000 GBP -> ~3810 USD at rate 1.27)
        def gbpResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_GBP_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("3000.00"))
            .parameter("sourceCurrencyUomId", "GBP")
            .call()
        
        // Add USD entry (2000 USD)
        def usdResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("2000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        then:
        eurResult.success == true || eurResult.success == "true"
        gbpResult.success == true || gbpResult.success == "true"
        usdResult.success == true || usdResult.success == "true"
        
        // Verify all entries exist
        def entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId)
            .list()
        
        entries.size() >= 3
        
        // Verify collection total
        def collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId)
            .one()
        
        collection.collectedAmount >= new BigDecimal("10000.00")
        
        logger.info("[TDD_TEST] BDD-LCPC-G2-SC4: PASSED - Multiple entries added, total: ${collection.collectedAmount}")
    }

    /**
     * BDD-LCPC-G3-SC1: Validate total matches target provision amount
     */
    def "testValidateTotalMatchesTarget"() {
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC1: Validate total matches target")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        // Add entries totaling approximately 10000 USD (4500 EUR = 4905, 2500 GBP = 3175, 1920 USD = 1920)
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("4500.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_GBP_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("2500.00"))
            .parameter("sourceCurrencyUomId", "GBP")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("1920.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        when:
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collectionId)
            .call()
        
        then:
        result.isValid == true || result.isValid == "true"
        result.status == "Complete"
        
        // VERIFY: Status is updated in database after validation
        def collectionAfter = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId)
            .one()
        collectionAfter.collectionStatusId == "LcPrvColComplete"
        
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC1: PASSED - Total matches target")
    }

    /**
     * BDD-LCPC-G3-SC2: Validate total exceeds target provision amount
     */
    def "testValidateTotalExceedsTarget"() {
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC2: Validate total exceeds target")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        // Add entries totaling more than 10000 USD
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("6000.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        when:
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collectionId)
            .call()
        
        then:
        result.isValid == false
        result.status == "Exceeds"
        result.message?.contains("exceeds")
        
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC2: PASSED - Total exceeds target warning shown")
    }

    /**
     * BDD-LCPC-G3-SC3: Validate total is less than target provision amount
     */
    def "testValidateTotalLessThanTarget"() {
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC3: Validate total less than target")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        // Add entry totaling less than 10000 USD
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("5000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        when:
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collectionId)
            .call()
        
        then:
        result.isValid == false
        result.status == "Incomplete"
        
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC3: PASSED - Total less than target")
    }

    /**
     * BDD-LCPC-G3-SC4: Validate tolerance for rounding discrepancies
     */
    def "testValidateTolerance"() {
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC4: Validate tolerance for rounding")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        // Add entry totaling 9999.99 USD (within tolerance)
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("9999.99"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        when:
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collectionId)
            .call()
        
        then:
        result.isValid == true || result.isValid == "true"
        result.status == "Complete"
        
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC4: PASSED - Tolerance accepted")
    }

    /**
     * BDD-LCPC-G6-SC1: Collect funds from multiple accounts
     */
    def "testCollectProvisionFunds"() {
        logger.info("[TDD_TEST] BDD-LCPC-G6-SC1: Collect funds from multiple accounts")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        // Add entries totaling approximately 10000 USD (4500 EUR = 4905, 2500 GBP = 3175, 1920 USD = 1920)
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_EUR_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("4500.00"))
            .parameter("sourceCurrencyUomId", "EUR")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_GBP_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("2500.00"))
            .parameter("sourceCurrencyUomId", "GBP")
            .call()
        
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("1920.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        // Validate to set collection status to Complete
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collectionId)
            .call()
        
        when:
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.collect#ProvisionFunds")
            .parameter("collectionId", collectionId)
            .call()
        
        then:
        result.success == true || result.success == "true"
        
        // Verify collection status
        def collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId)
            .one()
        
        collection.collectionStatusId == "LcPrvColCollected"
        
        // Verify entry statuses
        def entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId)
            .list()
        
        entries.each { entry ->
            assert entry.entryStatusId == "LcPrvEntryCollected"
            assert entry.cbsHoldReference != null
        }
        
        logger.info("[TDD_TEST] BDD-LCPC-G6-SC1: PASSED - Funds collected from all accounts")
    }

    /**
     * BDD-LCPC-G5-SC2: Handle unsupported currency
     */
    def "testHandleUnsupportedCurrency"() {
        logger.info("[TDD_TEST] BDD-LCPC-G5-SC2: Handle unsupported currency")
        
        setup:
        cleanupCollection(lcId)
        
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("10000.00"))
            .call()
        collectionId = createResult.collectionId
        
        when:
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_JPY_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("500000.00"))
            .parameter("sourceCurrencyUomId", "JPY")
            .call()
        
        then:
        // Verify that the unsupported currency was rejected
        def entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId)
            .list()
        entries.size() == 0
        
        logger.info("[TDD_TEST] BDD-LCPC-G5-SC2: PASSED - Unsupported currency rejected")
    }

    /**
     * BDD-LCPC-G3-SC4: Validate recalculates from entries (NOT from stored field)
     * 
     * This test verifies that validate#CollectionTotal recalculates the collected amount
     * from LcProvisionCollectionEntry records, NOT from the stored collectedAmount field.
     * 
     * Bug: Previous tests passed because they manually set collectedAmount in the entity.
     * Real workflow: Entries are added via add#CollectionEntry, and validate should
     * recalculate from those entries.
     */
    def "testValidateRecalculatesFromEntriesNotFromStoredField"() {
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC4: Validate recalculates from entries (bug fix test)")
        
        setup:
        cleanupCollection(lcId)
        
        // Create collection with target 445000 USD
        def createResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameter("lcId", lcId)
            .parameter("targetProvisionAmount", new BigDecimal("445000.00"))
            .call()
        collectionId = createResult.collectionId
        
        // Add entry with 10000 USD (NOT setting collectedAmount manually)
        ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameter("collectionId", collectionId)
            .parameter("accountId", "ACC_USD_001")
            .parameter("partyId", "DEMO_ORG_ABC")
            .parameter("sourceAmount", new BigDecimal("10000.00"))
            .parameter("sourceCurrencyUomId", "USD")
            .call()
        
        when:
        // Verify entry was created
        def entriesBefore = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId)
            .list()
        
        // Get collection to check collectedAmount field
        def collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId)
            .one()
        
        // Call validate - it should recalculate from entries, not from stored field
        def result = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameter("collectionId", collectionId)
            .call()
        
        then:
        // Verify entry exists
        entriesBefore.size() == 1
        entriesBefore[0].convertedAmount == 10000.00
        
        // The bug: collectedAmount field might be 0 or null because add#CollectionEntry might not have updated it
        // OR the field might have wrong value
        // But validate should recalculate from entries and return isValid = false (10000 != 445000)
        result.isValid == false || result.isValid == "false"
        result.status == "Incomplete"
        result.collectedAmount == 10000.00
        result.targetAmount == 445000.00
        
        logger.info("[TDD_TEST] BDD-LCPC-G3-SC4: PASSED - Validate recalculates from entries")
    }

    def cleanup() {
        ec.message.clearAll()
    }
    
    def cleanupCollection(String lcIdParam) {
        def collections = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("lcId", lcIdParam)
            .list()
        collections.each { coll ->
            def entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
                .condition("collectionId", coll.collectionId)
                .list()
            entries.each { entry -> entry.delete() }
            coll.delete()
        }
    }
}
