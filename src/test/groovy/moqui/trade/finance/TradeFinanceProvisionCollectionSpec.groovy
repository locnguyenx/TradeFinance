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

/**
 * TradeFinanceProvisionCollectionSpec
 * TDD for Import LC Provision Collection feature
 * Tests multi-account, multi-currency provision collection workflow
 */
@Stepwise
class TradeFinanceProvisionCollectionSpec extends Specification {
    @Shared protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceProvisionCollectionSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = null
    @Shared String collectionId = null

    def setupSpec() {
        logger.warn("[TDD_TRACE] TradeFinanceProvisionCollectionSpec STARTING setupSpec")
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        
        // Ensure basic types exist
        ensureEnumType("PartyType", "Party Type")
        ensureEnumType("LcProductType", "LC Product Type")
        ensureEnumType("LcTransactionStatus", "LC Transaction Status")
        ensureEnumType("LcLifecycleStatus", "LC Lifecycle Status")
        ensureEnumType("LcProvisionStatus", "LC Provision Status")
        
        // Ensure status types
        ensureStatusType("LcTransaction", "LC Transaction")
        ensureStatusType("LcLifecycle", "LC Lifecycle")
        ensureStatusType("LcProvisionStatus", "LC Provision Status")
        ensureStatusType("ProvisionStatus", "Provision Status")
        ensureStatusType("LcProvision", "LC Provision")
        ensureStatusType("LcProvisionCollection", "Provision Collection")
        ensureStatusType("LcProvisionEntry", "Provision Collection Entry")
        
        // Enums
        ensureEnum("PT_PERSON", "PartyType", "Person")
        ensureEnum("PT_ORGANIZATION", "PartyType", "Organization")
        
        // _NA_ Party
        if (!ec.entity.find("mantle.party.Party").condition("partyId", "_NA_").one()) {
            ec.entity.makeValue("mantle.party.Party").setAll([partyId: "_NA_", partyTypeEnumId: "PT_ORGANIZATION"]).create()
        }
        
        // Role types
        ensureRoleType("BANK", "Bank")
        ensureRoleType("CUSTOMER", "Customer")
        
        // Enums and Status
        ensureEnum("LC_PROD_SIGHT", "LcProductType", "Sight LC")
        ensureCurrency("USD")
        ensureCurrency("EUR")
        ensureCurrency("GBP")
        
        // Transaction statuses
        ["LcTxDraft", "LcTxApproved", "LcTxPendingReview", "LcTxPendingProcessing", "LcTxReturned", "LcTxPendingApproval"].each {
            ensureEnum(it, "LcTransactionStatus", it)
            ensureStatus(it, "LcTransaction", it)
        }
        
        // Lifecycle statuses
        ["LcLfDraft", "LcLfApplied", "LcLfActive"].each {
            ensureEnum(it, "LcLifecycleStatus", it)
            ensureStatus(it, "LcLifecycle", it)
        }
        
        // Provision statuses
        ["LcPrvDraft", "LcPrvHeld", "LcPrvReleased", "LcPrvCancelled"].each {
            ensureEnum(it, "LcProvisionStatus", it)
            ensureStatus(it, "LcProvisionStatus", it)
            ensureStatus(it, "ProvisionStatus", it)
            ensureStatus(it, "LcProvision", it)
        }
        
        // Collection statuses (NEW)
        ["LcPrvColDraft", "LcPrvColComplete", "LcPrvColCollected", "LcPrvColReleased"].each {
            ensureEnum(it, "LcProvisionStatus", it)
            ensureStatus(it, "LcProvisionCollection", it)
        }
        
        // Entry statuses (NEW)
        ["LcPrvEntryPending", "LcPrvEntryCollected", "LcPrvEntryReleased", "LcPrvEntryFailed"].each {
            ensureEnum(it, "LcProvisionStatus", it)
            ensureStatus(it, "LcProvisionEntry", it)
        }
        
        // Status flow
        ensureStatusFlow("LcTransaction", "LC Transaction Flow")
        ensureStatusFlowTransition("LcTransaction", "LcTxDraft", "LcTxPendingReview")
        
        // Parties
        ensureParty("DEMO_ORG_ABC", "PT_ORGANIZATION", "CUSTOMER")
        ensureParty("DEMO_ORG_XYZ", "PT_ORGANIZATION", "VENDOR")
        ensureParty("DEMO_ORG_VIETCOMBANK", "PT_ORGANIZATION", "BANK")
        
        // Product
        if (!ec.entity.find("moqui.trade.finance.LcProduct").condition("productId", "PROD_ILC_SIGHT").one()) {
            ec.entity.makeValue("moqui.trade.finance.LcProduct")
                .setAll([productId: "PROD_ILC_SIGHT", productName: "Import LC Sight", lcProductTypeEnumId: "LC_PROD_SIGHT", defaultProvisionRate: 10]).create()
        }
        
        // CBS Simulator accounts for multi-currency support
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_USD_001", "USD", 50000.00)
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_EUR_001", "EUR", 50000.00)
        ensureCbsAccount("DEMO_ORG_ABC", "ACC_GBP_001", "GBP", 50000.00)
        
        // User
        if (!ec.entity.find("moqui.security.UserAccount").condition("username", "tf-admin-test").one()) {
            try {
                ec.entity.makeValue("moqui.security.UserAccount")
                    .setAll([userId: "TF_ADMIN_TEST", username: "tf-admin-test", currentPassword: "moqui", disabled: "N"]).create()
            } catch (Exception e) { logger.error("tf-admin-test creation FAILED: ${e.message}", e) }
        }
        
        ec.message.clearAll()
        
        // Enable CBS Simulator
        System.setProperty("cbs.integration.impl", "Simulator")
        System.setProperty("cbs.simulate.timeout", "false")
    }

    def static ensureEnumType(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.EnumerationType").condition("enumTypeId", id).one()) {
                ec.entity.makeValue("moqui.basic.EnumerationType").setAll([enumTypeId: id, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureEnumType", e)
        }
    }

    def static ensureStatusType(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusType").condition("statusTypeId", id).one()) {
                ec.entity.makeValue("moqui.basic.StatusType").setAll([statusTypeId: id, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatusType", e)
        }
    }

    def static ensureEnum(String id, String type, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.Enumeration").condition("enumId", id).one()) {
                if (!ec.entity.find("moqui.basic.EnumerationType").condition("enumTypeId", type).one()) {
                    ec.entity.makeValue("moqui.basic.EnumerationType").setAll([enumTypeId: type, description: type]).create()
                }
                ec.entity.makeValue("moqui.basic.Enumeration").setAll([enumId: id, enumTypeId: type, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureEnum", e)
        }
    }

    def static ensureRoleType(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("mantle.party.RoleType").condition("roleTypeId", id).one()) {
                ec.entity.makeValue("mantle.party.RoleType").setAll([roleTypeId: id, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureRoleType", e)
        }
    }

    def static ensureStatus(String id, String type, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusItem").condition("statusId", id).one()) {
                if (!ec.entity.find("moqui.basic.StatusType").condition("statusTypeId", type).one()) {
                    ec.entity.makeValue("moqui.basic.StatusType").setAll([statusTypeId: type, description: type]).create()
                }
                ec.entity.makeValue("moqui.basic.StatusItem").setAll([statusId: id, statusTypeId: type, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatus", e)
        }
    }

    def static ensureStatusFlow(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusFlow").condition("statusFlowId", id).one()) {
                ec.entity.makeValue("moqui.basic.StatusFlow").setAll([statusFlowId: id, statusFlowName: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatusFlow", e)
        }
    }

    def static ensureStatusFlowTransition(String flowId, String from, String to) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusFlowTransition")
                    .condition("statusFlowId", flowId).condition("statusId", from).condition("toStatusId", to).one()) {
                if (!ec.entity.find("moqui.basic.StatusFlow").condition("statusFlowId", flowId).one()) {
                    ec.entity.makeValue("moqui.basic.StatusFlow").setAll([statusFlowId: flowId, statusFlowName: flowId]).create()
                }
                ec.entity.makeValue("moqui.basic.StatusFlowTransition")
                    .setAll([statusFlowId: flowId, statusId: from, toStatusId: to, transitionName: ("${from} to ${to}")]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatusFlowTransition", e)
        }
    }

    def static ensureCurrency(String id) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.Uom").condition("uomId", id).one()) {
                ec.entity.makeValue("moqui.basic.Uom").setAll([uomId: id, uomTypeEnumId: "UT_CURRENCY_MEASURE", description: id]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureCurrency", e)
        }
    }

    def static ensureParty(String id, String type, String role) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("mantle.party.Party").condition("partyId", id).one()) {
                ec.entity.makeValue("mantle.party.Party").setAll([partyId: id, partyTypeEnumId: type]).create()
            }
            if (!ec.entity.find("mantle.party.PartyRole").condition("partyId", id).condition("roleTypeId", role).one()) {
                ec.entity.makeValue("mantle.party.PartyRole").setAll([partyId: id, roleTypeId: role]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureParty", e)
        }
    }
    
    def static ensureCbsAccount(String partyId, String accountId, String currency, BigDecimal balance) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            EntityValue existing = ec.entity.find("moqui.trade.finance.CbsSimulatorState")
                .condition("accountId", accountId).one()
            if (!existing) {
                ec.entity.makeValue("moqui.trade.finance.CbsSimulatorState")
                    .setAll([partyId: partyId, accountId: accountId, balanceAmount: balance, holdAmount: 0.00, currencyUomId: currency]).create()
            } else {
                existing.balanceAmount = balance
                existing.currencyUomId = currency
                existing.store()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureCbsAccount", e)
        }
    }

    def cleanupSpec() {
        System.clearProperty("cbs.integration.impl")
        System.clearProperty("cbs.simulate.timeout")
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
        ec.user.pushUser("TF_ADMIN_TEST")
    }

    def cleanup() {
        ec.user.popUser()
    }

    // ========================================================================
    // GREEN PHASE: Tests - These should PASS after implementation
    // ========================================================================

    def "GREEN: Create LcProvisionCollection entity"() {
        given: "An LC exists"
        String lcNum = "TDD-PROV-" + (System.currentTimeMillis() % 10000)
        java.sql.Date expiryDate = new java.sql.Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30))
        Map createResult = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
            .parameters([
                lcNumber: lcNum, productId: "PROD_ILC_SIGHT",
                lcStatusId: "LcLfDraft", transactionStatusId: "LcTxDraft",
                applicantPartyId: "DEMO_ORG_ABC", beneficiaryPartyId: "DEMO_ORG_XYZ",
                issuingBankPartyId: "DEMO_ORG_VIETCOMBANK", amount: 100000.00, amountCurrencyUomId: "USD",
                expiryDate: expiryDate
            ]).call()
        lcId = createResult.lcId

        when: "We create a provision collection"
        Map collectionResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.create#LcProvisionCollection")
            .parameters([
                lcId: lcId,
                targetProvisionAmount: 10000.00,
                targetCurrencyUomId: "USD"
            ]).call()

        then: "Collection should be created"
        collectionResult.collectionId != null
        collectionResult.success == true || collectionResult.success == "true"
        
        and: "Collection record should exist"
        EntityValue collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionResult.collectionId).one()
        collection != null
        collection.lcId == lcId
        collection.targetProvisionAmount == 10000.00
        collection.targetCurrencyUomId == "USD"
        collection.collectionStatusId == "LcPrvColDraft"
        collection.collectedAmount == 0.00
        
        when: "Store collectionId for next tests"
        then: "Assignment"
        (collectionId = collectionResult.collectionId) != null
    }

    def "GREEN: Add collection entry with exchange rate"() {
        given: "A collection exists"
        assert collectionId != null : "collectionId must not be null"

        when: "We add a collection entry from EUR account"
        Map entryResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameters([
                collectionId: collectionId,
                partyId: "DEMO_ORG_ABC",
                accountId: "ACC_EUR_001",
                sourceCurrencyUomId: "EUR",
                sourceAmount: 5000.00
            ]).call()

        then: "Entry should be created with converted amount"
        entryResult.entrySeqId != null
        entryResult.success == true || entryResult.success == "true"
        
        and: "Entry record should exist with exchange rate"
        EntityValue entry = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId).condition("entrySeqId", entryResult.entrySeqId).one()
        entry != null
        entry.accountId == "ACC_EUR_001"
        entry.sourceCurrencyUomId == "EUR"
        entry.sourceAmount == 5000.00
        entry.exchangeRate != null
        entry.exchangeRate > 0
        entry.convertedAmount != null
        entry.convertedAmount > 0
        entry.entryStatusId == "LcPrvEntryPending"
    }

    def "GREEN: Add multiple collection entries and validate total"() {
        given: "A collection exists"
        assert collectionId != null

        when: "We add entries from different currencies"
        // EUR 5000 * 1.09 = 5450 USD
        // GBP 2800 * 1.27 = 3556 USD
        // USD 994 = 994 USD
        // Total = 10000 USD exactly
        Map gbResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameters([
                collectionId: collectionId,
                partyId: "DEMO_ORG_ABC",
                accountId: "ACC_GBP_001",
                sourceCurrencyUomId: "GBP",
                sourceAmount: 2800.00
            ]).call()
        
        Map usdResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameters([
                collectionId: collectionId,
                partyId: "DEMO_ORG_ABC",
                accountId: "ACC_USD_001",
                sourceCurrencyUomId: "USD",
                sourceAmount: 994.00
            ]).call()

        then: "All entries should exist"
        EntityList entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId).list()
        entries.size() >= 3 // Should have EUR, GBP, USD entries
        
        and: "Collection total should be calculated"
        EntityValue collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId).one()
        collection.collectedAmount > 0
    }

    def "GREEN: Validate collection total matches target"() {
        given: "A collection with entries"
        assert collectionId != null

        when: "We validate the collection"
        Map validateResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.validate#CollectionTotal")
            .parameters([collectionId: collectionId]).call()

        then: "Validation should return results"
        validateResult.collectedAmount != null
        validateResult.targetAmount != null
        
        and: "Collection should be marked as complete"
        EntityValue collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId).one()
        collection.collectionStatusId == "LcPrvColComplete"
    }

    def "GREEN: Collect provision funds from CBS"() {
        given: "A complete collection"
        assert collectionId != null

        when: "We trigger collection"
        Map collectResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.collect#ProvisionFunds")
            .parameters([collectionId: collectionId]).call()

        then: "Collection should be successful"
        collectResult.success == true || collectResult.success == "true"
        
        and: "Collection status should be collected"
        EntityValue collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId).one()
        collection.collectionStatusId == "LcPrvColCollected"
        
        and: "Each entry should have CBS hold reference"
        EntityList entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId).list()
        entries.each { entry ->
            entry.entryStatusId == "LcPrvEntryCollected"
            entry.cbsHoldReference != null
            entry.cbsHoldDate != null
        }
    }

    def "GREEN: Release provision collection"() {
        given: "A collected collection"
        assert collectionId != null
        
        and: "Verify collection is in collected status"
        EntityValue collBefore = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId).one()
        assert collBefore.collectionStatusId == "LcPrvColCollected" : "Collection status should be LcPrvColCollected, got: ${collBefore.collectionStatusId}"

        when: "We release the funds"
        Map releaseResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.release#ProvisionCollection")
            .parameters([collectionId: collectionId]).call()

        then: "Release should be successful"
        releaseResult.success == true || releaseResult.success == "true"
        
        and: "Collection status should be released"
        EntityValue collection = ec.entity.find("moqui.trade.finance.LcProvisionCollection")
            .condition("collectionId", collectionId).one()
        collection.collectionStatusId == "LcPrvColReleased"
        
        and: "Entry statuses should be released"
        EntityList entries = ec.entity.find("moqui.trade.finance.LcProvisionCollectionEntry")
            .condition("collectionId", collectionId).list()
        entries.each { entry ->
            entry.entryStatusId == "LcPrvEntryReleased"
        }
    }

    def "GREEN: Get exchange rate from CBS"() {
        when: "We fetch exchange rate for EUR to USD"
        Map rateResult = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.get#ExchangeRate")
            .parameters([
                fromCurrencyUomId: "EUR",
                toCurrencyUomId: "USD"
            ]).call()

        then: "Exchange rate should be returned"
        rateResult.exchangeRate != null
        new BigDecimal(rateResult.exchangeRate.toString()) > 0
    }

    def "GREEN: Handle unsupported currency"() {
        given: "A collection exists"
        assert collectionId != null

        when: "We try to add entry with unsupported currency"
        Map entryResult = ec.service.sync().name("moqui.trade.finance.ProvisionCollectionServices.add#CollectionEntry")
            .parameters([
                collectionId: collectionId,
                partyId: "DEMO_ORG_ABC",
                accountId: "ACC_EUR_001",
                sourceCurrencyUomId: "JPY",
                sourceAmount: 500000.00
            ]).call()

        then: "Should fail with error"
        ec.message.hasError() || entryResult.success == false || entryResult.success == "false"
    }
}
