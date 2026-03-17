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
 * TradeFinanceProvisionChargeSpec
 * TDD for R8.11: Manage LC Provision & Charge.
 * Verifies product-driven calculation, manual adjustments, and CBS integration.
 * 
 * BDD R8.3-UC5 Coverage (Provision & Charge Assessment):
 * - R8.3-UC5-SC1: Calculate provision and charges (Happy Path)
 * - R8.3-UC5-SC2: View charge breakdown
 * - R8.3-UC5-SC3: CBS connection error handling
 * 
 * @Scenario(BDD-R8.3-UC5-SC1) - COVERED
 * @Scenario(BDD-R8.3-UC5-SC2) - COVERED
 * @Scenario(BDD-R8.3-UC5-SC3) - COVERED
 */
@Stepwise
class TradeFinanceProvisionChargeSpec extends Specification {
    @Shared protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceProvisionChargeSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = null

    def setupSpec() {
        logger.warn("[TDD_TRACE] TradeFinanceProvisionChargeSpec STARTING setupSpec")
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        
        // --- TYPE SETUP ---
        ensureEnumType("PartyType", "Party Type")
        ensureEnumType("LcProductType", "LC Product Type")
        ensureEnumType("LcTransactionStatus", "LC Transaction Status")
        ensureEnumType("LcLifecycleStatus", "LC Lifecycle Status")
        ensureEnumType("LcChargeType", "LC Charge Type")
        ensureEnumType("ItemType", "Item Type")
        ensureEnumType("RequestType", "Request Type")
        
        ensureStatusType("LcTransaction", "LC Transaction")
        ensureStatusType("LcLifecycle", "LC Lifecycle")
        ensureStatusType("Request", "Request")
        ensureStatusType("RequestStatus", "Request Status")
        ensureStatusType("LcProvisionStatus", "LC Provision Status")

        // --- RESILIENT SETUP ---
        ensureEnum("PT_PERSON", "PartyType", "Person")
        ensureEnum("PT_ORGANIZATION", "PartyType", "Organization")
        
        // --- CRITICAL: Ensure _NA_ Party exists as it's often the default ownerPartyId ---
        if (!ec.entity.find("mantle.party.Party").condition("partyId", "_NA_").one()) {
            logger.info("CREATING _NA_ Party")
            ec.entity.makeValue("mantle.party.Party").setAll([partyId: "_NA_", partyTypeEnumId: "PT_ORGANIZATION"]).create()
        }
        
        ensureRoleType("BANK", "Bank")
        ensureRoleType("CUSTOMER", "Customer")
        ensureRoleType("VENDOR", "Vendor")
        ensureEnum("LC_PROD_SIGHT", "LcProductType", "Sight LC")
        ensureEnum("ItemCommission", "ItemType", "Commission")
        ensureEnum("ItemMiscCharge", "ItemType", "Misc Charge")
        ensureEnum("RqtLcIssuance", "RequestType", "LC Issuance")
        ensureEnum("LC_CHG_ISSUANCE", "LcChargeType", "Issuance Commission")
        ensureEnum("LC_CHG_SWIFT", "LcChargeType", "SWIFT Fee")
        
        ensureCurrency("USD")
        
        // Brute-force ensures to satisfy conflicting DB constraints
        ["LcTxDraft", "LcTxApproved", "LcTxPendingReview", "LcTxPendingProcessing", "LcTxReturned", "LcTxPendingApproval"].each {
            ensureEnum(it, "LcTransactionStatus", it)
            ensureStatus(it, "LcTransaction", it)
        }
        ["LcLfDraft", "LcLfApplied", "LcLfActive"].each {
            ensureEnum(it, "LcLifecycleStatus", it)
            ensureStatus(it, "LcLifecycle", it)
        }
        ensureStatusType("LcProvisionStatus", "LC Provision Status")
        ensureStatusType("ProvisionStatus", "Provision Status")
        ensureStatusType("LcProvision", "LC Provision")
        
        ["LcPrvDraft", "LcPrvHeld", "LcPrvReleased", "LcPrvCancelled"].each {
            ensureEnum(it, "LcProvisionStatus", it)
            ensureStatus(it, "LcProvisionStatus", it)
            ensureStatus(it, "ProvisionStatus", it)
            ensureStatus(it, "LcProvision", it)
        }
        ["ReqDraft", "ReqSubmitted", "ReqCompleted"].each {
            ensureEnum(it, "RequestType", it)
            ensureStatus(it, "Request", it)
            ensureStatus(it, "RequestStatus", it)
        }
        
        // --- WORKFLOW SETUP ---
        ensureStatusFlow("LcTransaction", "LC Transaction Flow")
        ensureStatusFlowTransition("LcTransaction", "LcTxDraft", "LcTxPendingReview")
        ensureStatusFlowTransition("LcTransaction", "LcTxPendingReview", "LcTxPendingProcessing")
        ensureStatusFlowTransition("LcTransaction", "LcTxPendingProcessing", "LcTxPendingApproval")
        ensureStatusFlowTransition("LcTransaction", "LcTxPendingApproval", "LcTxApproved")
        ensureStatusFlowTransition("LcTransaction", "LcTxPendingReview", "LcTxReturned")
        ensureStatusFlowTransition("LcTransaction", "LcTxReturned", "LcTxPendingReview")
        
        ensureParty("DEMO_ORG_ABC", "PT_ORGANIZATION", "CUSTOMER")
        ensureParty("DEMO_ORG_XYZ", "PT_ORGANIZATION", "VENDOR")
        ensureParty("DEMO_ORG_VIETCOMBANK", "PT_ORGANIZATION", "BANK")

        if (!ec.entity.find("moqui.trade.finance.LcProduct").condition("productId", "PROD_ILC_SIGHT").one()) {
            ec.entity.makeValue("moqui.trade.finance.LcProduct")
                .setAll([productId: "PROD_ILC_SIGHT", productName: "Import LC Sight", lcProductTypeEnumId: "LC_PROD_SIGHT", defaultProvisionRate: 10]).create()
        }

        // Ensure User
        if (!ec.entity.find("moqui.security.UserAccount").condition("username", "tf-admin-test").one()) {
            try {
                logger.info("CREATING tf-admin-test USER...")
                ec.entity.makeValue("moqui.security.UserAccount")
                    .setAll([userId: "TF_ADMIN_TEST", username: "tf-admin-test", currentPassword: "moqui", disabled: "N"]).create()
                logger.info("tf-admin-test USER CREATED.")
            } catch (Exception e) { logger.error("tf-admin-test creation FAILED: ${e.message}", e) }
        }

        ec.message.clearAll()
        
        // Enable stateful CBS Simulator
        System.setProperty("cbs.integration.impl", "Simulator")
        System.setProperty("cbs.simulate.timeout", "false")
        
        // Ensure test data exists for product charges
        try {
            logger.info("Ensuring LcProductCharge...")
            ec.entity.makeValue("moqui.trade.finance.LcProductCharge")
                .setAll([productId: "PROD_ILC_SIGHT", chargeTypeEnumId: "LC_CHG_ISSUANCE", 
                        itemTypeEnumId: "ItemCommission", defaultAmount: 150.00, defaultCurrencyUomId: "USD"]).createOrUpdate()
            ec.entity.makeValue("moqui.trade.finance.LcProductCharge")
                .setAll([productId: "PROD_ILC_SIGHT", chargeTypeEnumId: "LC_CHG_SWIFT", 
                        itemTypeEnumId: "ItemMiscCharge", defaultAmount: 75.00, defaultCurrencyUomId: "USD"]).createOrUpdate()
            logger.info("LcProductCharge ensured.")
        } catch (Exception e) {
            logger.error("FAILED to ensure LcProductCharge: ${e.message}", e)
            throw e
        }
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

    def static ensureStatusFlow(String id, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusFlow").condition("statusFlowId", id).one()) {
                logger.info("CREATING StatusFlow: ${id}")
                ec.entity.makeValue("moqui.basic.StatusFlow").setAll([statusFlowId: id, statusFlowName: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatusFlow", e)
            logger.error("FAILED to ensure status flow ${id}: ${e.message}", e)
        }
    }

    def static ensureStatusFlowTransition(String flowId, String from, String to) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusFlowTransition")
                    .condition("statusFlowId", flowId).condition("statusId", from).condition("toStatusId", to).one()) {
                logger.info("CREATING StatusFlowTransition: ${flowId} ${from} -> ${to}")
                if (!ec.entity.find("moqui.basic.StatusFlow").condition("statusFlowId", flowId).one()) {
                    ec.entity.makeValue("moqui.basic.StatusFlow").setAll([statusFlowId: flowId, statusFlowName: flowId]).create()
                }
                ec.entity.makeValue("moqui.basic.StatusFlowTransition")
                    .setAll([statusFlowId: flowId, statusId: from, toStatusId: to, transitionName: ("${from} to ${to}")]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatusFlowTransition", e)
            logger.error("FAILED to ensure status transition ${flowId} ${from}->${to}: ${e.message}", e)
        }
    }

    def static ensureStatus(String id, String type, String desc) {
        ExecutionContext ec = Moqui.getExecutionContext()
        boolean suspendedTransaction = false
        try {
            suspendedTransaction = ec.transaction.begin(null)
            if (!ec.entity.find("moqui.basic.StatusItem").condition("statusId", id).one()) {
                logger.info("CREATING Status: ${id} (${type})")
                if (!ec.entity.find("moqui.basic.StatusType").condition("statusTypeId", type).one()) {
                    logger.info("CREATING StatusType for Status: ${type}")
                    ec.entity.makeValue("moqui.basic.StatusType").setAll([statusTypeId: type, description: type]).create()
                }
                ec.entity.makeValue("moqui.basic.StatusItem").setAll([statusId: id, statusTypeId: type, description: desc]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureStatus", e)
            logger.error("FAILED to ensure status ${id}: ${e.message}", e)
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
                logger.info("CREATING Party: ${id}")
                ec.entity.makeValue("mantle.party.Party").setAll([partyId: id, partyTypeEnumId: type]).create()
            }
            if (!ec.entity.find("mantle.party.PartyRole").condition("partyId", id).condition("roleTypeId", role).one()) {
                logger.info("CREATING PartyRole: ${id} / ${role}")
                ec.entity.makeValue("mantle.party.PartyRole").setAll([partyId: id, roleTypeId: role]).create()
            }
            ec.transaction.commit()
        } catch (Exception e) {
            if (suspendedTransaction) ec.transaction.rollback("Error in ensureParty", e)
            logger.error("FAILED to ensure party ${id}: ${e.message}", e)
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

    def "RED: calculate charges from product config (R8.11-UC1)"() {
        given: "A new LC in Draft status created via service"
        String lcNum = "TDD-R811-" + (System.currentTimeMillis() % 10000)
        
        // Use the actual creation service to test the integration hook
        Map createResult = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
                .parameters([
                    lcNumber: lcNum, productId: "PROD_ILC_SIGHT",
                    lcStatusId: "LcLfDraft", transactionStatusId: "LcTxDraft",
                    applicantPartyId: "DEMO_ORG_ABC", beneficiaryPartyId: "DEMO_ORG_XYZ",
                    issuingBankPartyId: "DEMO_ORG_VIETCOMBANK", amount: 100000.00, amountCurrencyUomId: "USD"
                ]).call()
        lcId = createResult.lcId

        logger.info("CREATED LC via service: ${lcId}")

        when: "We trigger the unified financial assessment"
        // This should actually be redundant if create#LetterOfCredit calls it, 
        // but we call it explicitly to verify the service logic independently
        ec.service.sync().name("moqui.trade.finance.FinancialServices.calculate#LcChargesAndProvisions")
                .parameter("lcId", lcId).call()

        then: "Standard charges are created based on the product configuration"
        List<EntityValue> charges = ec.entity.find("moqui.trade.finance.LcCharge").condition("lcId", lcId).list()
        charges.size() >= 2
        charges.any { it.chargeTypeEnumId == 'LC_CHG_ISSUANCE' && it.chargeAmount == 150.00 }
        charges.any { it.chargeTypeEnumId == 'LC_CHG_SWIFT' && it.chargeAmount == 75.00 }

        and: "Provision record is automatically calculated and created with correct rate"
        EntityValue provision = ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", lcId).one()
        provision != null
        provision.provisionAmount == 10000.00 // 10% of 100,000.00
        provision.provisionRate == 10 // This is expected to FAIL in RED phase
        provision.provisionStatusId == 'LcPrvDraft'
    }

    def "RED: manual adjustment persistence (R8.11-UC1)"() {
        given: "Existing charges from calculation"
        EntityValue charge = ec.entity.find("moqui.trade.finance.LcCharge")
                .condition("lcId", lcId).condition("chargeTypeEnumId", "LC_CHG_SWIFT").one()
        
        when: "A user manually adjusts the charge amount"
        charge.chargeAmount = 99.99
        charge.store()
        
        and: "The calculation service is called again"
        ec.service.sync().name("moqui.trade.finance.FinancialServices.calculate#LcCharges")
                .parameters([lcId: lcId]).call()
        
        then: "The adjusted amount should NOT be overwritten by default config"
        charge.refresh()
        charge.chargeAmount == 99.99
    }

    def "RED: CBS failure - insufficient funds (R8.11-UC2/Edge Case)"() {
        // Mock account setup for applicant
        EntityValue state = ec.entity.find("moqui.trade.finance.CbsSimulatorState")
            .condition("partyId", "DEMO_ORG_ABC").one()
        if (state == null) state = ec.entity.makeValue("moqui.trade.finance.CbsSimulatorState")
        state.setAll([partyId: "DEMO_ORG_ABC", balanceAmount: 0.00, holdAmount: 0.00, currencyUomId: "USD"])
        state.store()

        // Submit first
        // Need expiryDate for strict validation during submission
        Timestamp futureDate = new Timestamp(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)) // 30 days
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.update#LetterOfCredit")
                .parameters([lcId: lcId, expiryDate: futureDate]).call()
        
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.submit#LetterOfCredit")
                .parameters([lcId: lcId]).call()
        
        // Supervisor approval to move to Pending Processing
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcBySupervisor")
                .parameters([lcId: lcId]).call()
        
        when: "We attempt to approve the LC issuance (triggering provision hold)"
        // Using approve#LcByTradeOperator as it handles provision hold logic
        Map approveResult = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeOperator")
                .parameters([lcId: lcId]).call()

        then: "Approval should fail due to insufficient funds"
        ec.message.hasError()
        ec.message.getErrorsString().contains("Insufficient funds")
        
        cleanup:
        ec.message.clearErrors()
    }

    def "RED: CBS timeout handling (Edge Case)"() {
        given: "CBS Integration configured to timeout"
        System.setProperty("cbs.simulate.timeout", "true")
        
        // Setup: Must be in Pending Processing for Trade Operator to approve
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcBySupervisor")
                .parameters([lcId: lcId]).call()

        when: "Approval is attempted"
        try {
            ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeOperator")
                    .parameters([lcId: lcId]).call()
        } catch (Exception e) {
            ec.logger.info("Caught expected timeout exception: ${e.message}")
        }

        then: "System rolls back status but keeps transaction in Pending Processing (not Review)"
        // Note: comments will not be updated because the main transaction rolled back and we didn't force a new one for timeouts
        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).useCache(false).one()
        lc.transactionStatusId == 'LcTxPendingProcessing'

        cleanup:
        System.clearProperty("cbs.simulate.timeout")
        ec.message.clearErrors()
    }
}
