/*
 * TradeFinanceServicesSpec.groovy
 *
 * Spock unit tests for Trade Finance service methods defined in
 * TradeFinanceServices.xml. Uses pre-loaded demo data from
 * TradeFinanceDemoData.xml for data validation and state-dependent tests.
 *
 * Follows the mantle-usl OrderToCashBasicFlow pattern:
 *   - @Shared ExecutionContext with loginUser in setupSpec
 *   - tempSetSequencedIdPrimary for predictable IDs (new records only)
 *   - ec.entity.makeDataLoader().xmlText().check() for data validation
 *   - @Shared state across ordered test methods
 *
 * Security (TradeFinanceSecurityData.xml):
 *   - tf-admin (TF_ADMIN): Full access, used as primary test user
 *   - tf-maker (TF_MAKER): Create/submit, used in role-based tests
 *   - tf-checker (TF_CHECKER): Approve/reject, used in role-based tests
 *   - tf-viewer (TF_VIEWER): Read-only, used in role-based tests
 *
 * Demo data dependencies (TradeFinanceDemoData.xml):
 *   - DEMO_LC_01: Closed/Closed (Sight, fully settled, 2 drawings)
 *   - DEMO_LC_02: Advised/Closed (Usance, 1 drawing under review)
 *   - DEMO_LC_03: Amended/Closed (Negotiation, 1 amendment)
 *   - DEMO_LC_04: Applied/Submitted (Sight, pending approval)
 *   - DEMO_LC_05: Draft/Draft (Sight, initial draft — used for transitions)
 *   - DEMO_LC_06: Expired/Closed (Usance, expired)
 *   - DEMO_LC_07: Issued/Closed (Standby, active guarantee)
 *   - DEMO_LC_08: Applied/Submitted (transferable, pending)
 *   - DEMO_LC_09: Draft/Draft (rejected & reopened — verify history)
 *   - DEMO_LC_10: Negotiated/Approved (2 drawings, 1 discrepancy)
 */


package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityList
import org.moqui.entity.EntityCondition
import org.moqui.entity.EntityValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.sql.Timestamp

@Stepwise
class TradeFinanceServicesSpec extends Specification {
    @Shared protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceServicesSpec.class)
    @Shared ExecutionContext ec
    // Shared state for newly-created LC records
    @Shared String newLcId = null
    @Shared String newShipDate = "2027-01-01"
    @Shared String newRequestId = null
    @Shared String deleteLcId = null
    @Shared long effectiveTime = System.currentTimeMillis()
    @Shared long totalFieldsChecked = 0

    def setupSpec() {
        logger.warn("[TRACE] TradeFinanceServicesSpec STARTING setupSpec")
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        ec.user.loginUser("tf-admin", "moqui")

        // Find current max numeric IDs to avoid collisions in suite runs
        def numericLcs = ec.entity.find("moqui.trade.finance.LetterOfCredit").list().findAll { it.lcId.isNumber() }.collect { it.lcId.toLong() }
        def maxLc = numericLcs ? (long) numericLcs.max() : 0L
        def numericReqs = ec.entity.find("mantle.request.Request").list().findAll { it.requestId.isNumber() }.collect { it.requestId.toLong() }
        def maxReq = numericReqs ? (long) numericReqs.max() : 0L
        def numericHistory = ec.entity.find("moqui.trade.finance.LcHistory").list().findAll { it.lcHistoryId.isNumber() }.collect { it.lcHistoryId.toLong() }
        def maxHistory = numericHistory ? (long) numericHistory.max() : 0L

        long startId = [maxLc, maxReq, maxHistory].max() + 10000L
        logger.info("Setting ServicesSpec sequencer startId to ${startId} (Max LC: ${maxLc}, Max Req: ${maxReq}, Max History: ${maxHistory})")

        // Set temporary sequenced IDs for predictable IDs in this spec
        ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LetterOfCredit", startId, 10)
        ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LcHistory", startId, 50)
        ec.entity.tempSetSequencedIdPrimary("mantle.request.Request", startId, 10)
    }

    def cleanupSpec() {
        // Reset sequenced IDs
        ec.entity.tempResetSequencedIdPrimary("moqui.trade.finance.LetterOfCredit")
        ec.entity.tempResetSequencedIdPrimary("moqui.trade.finance.LcHistory")
        ec.entity.tempResetSequencedIdPrimary("mantle.request.Request")
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    // =========================================================
    // 1. Demo Data Validation Tests
    //    Verify pre-loaded demo data is present and correct.
    // =========================================================

    def "validate demo LC_01 (Closed Sight LC) data"() {
        when:
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <moqui.trade.finance.LetterOfCredit lcId="DEMO_LC_01" lcNumber="ILC-2026-0001"
                lcStatusId="LcLfClosed" transactionStatusId="LcTxClosed"
                productId="PROD_ILC_SIGHT" lcProductTypeEnumId="LC_PROD_SIGHT" requestId="DEMO_REQ_LC01"
                applicantPartyId="DEMO_ORG_ABC" beneficiaryPartyId="DEMO_ORG_XYZ"
                issuingBankPartyId="DEMO_ORG_VIETCOMBANK" advisingBankPartyId="DEMO_ORG_DBS"
                applicantName="ABC Trading Co" beneficiaryName="XYZ Exports Pte Ltd"
                amount="500000" amountCurrencyUomId="USD" amountTolerance_39A="5/5"
                formOfCredit_40A="LC_FORM_IRREVOCABLE" applicableRules_40E="LC_RULE_UCP600"
                issueDate="2026-01-15" expiryDate="2026-07-15"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked
        logger.info("DEMO_LC_01 data check: ${fieldsChecked} fields, errors: ${dataCheckErrors}")

        then:
        dataCheckErrors.size() == 0
    }

    def "validate demo LC_02 (Advised Usance LC) data"() {
        when:
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <moqui.trade.finance.LetterOfCredit lcId="DEMO_LC_02" lcNumber="ILC-2026-0002"
                lcStatusId="LcLfAdvised" transactionStatusId="LcTxClosed"
                applicantPartyId="DEMO_ORG_SUNRISE" beneficiaryPartyId="DEMO_ORG_THAI_STEEL"
                issuingBankPartyId="DEMO_ORG_BIDV" advisingBankPartyId="DEMO_ORG_BBL"
                productId="PROD_ILC_USANCE" lcProductTypeEnumId="LC_PROD_USANCE" amount="1200000" amountCurrencyUomId="USD"
                draftsAt_42C="90 DAYS AFTER SIGHT"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked
        logger.info("DEMO_LC_02 data check: ${fieldsChecked} fields, errors: ${dataCheckErrors}")

        then:
        dataCheckErrors.size() == 0
    }

    def "validate demo LC_05 (Draft LC) data"() {
        when:
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <moqui.trade.finance.LetterOfCredit lcId="DEMO_LC_05" lcNumber="ILC-2026-0005"
                lcStatusId="LcLfDraft" transactionStatusId="LcTxDraft"
                productId="PROD_ILC_SIGHT" lcProductTypeEnumId="LC_PROD_SIGHT"
                applicantPartyId="DEMO_ORG_GREEN" beneficiaryPartyId="DEMO_ORG_BR_AGRO"
                applicantName="Green Agriculture Co" amount="320000" amountCurrencyUomId="USD"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked
        logger.info("DEMO_LC_05 data check: ${fieldsChecked} fields, errors: ${dataCheckErrors}")

        then:
        dataCheckErrors.size() == 0
    }

    def "validate demo LC_07 (Standby LC) data"() {
        when:
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <moqui.trade.finance.LetterOfCredit lcId="DEMO_LC_07" lcNumber="ILC-2026-0007"
                lcStatusId="LcLfIssued" transactionStatusId="LcTxClosed"
                applicantPartyId="DEMO_ORG_ELECTRONICS" beneficiaryPartyId="DEMO_ORG_JP_TECH"
                issuingBankPartyId="DEMO_ORG_VIETCOMBANK" advisingBankPartyId="DEMO_ORG_MUFG"
                productId="PROD_ILC_STANDBY" lcProductTypeEnumId="LC_PROD_STANDBY" amount="3000000" amountCurrencyUomId="USD"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked

        then:
        dataCheckErrors.size() == 0
    }

    def "validate demo LC_10 (Negotiated LC) data"() {
        when:
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <moqui.trade.finance.LetterOfCredit lcId="DEMO_LC_10" lcNumber="ILC-2026-0010"
                lcStatusId="LcLfNegotiated" transactionStatusId="LcTxApproved"
                applicantPartyId="DEMO_ORG_STEEL" beneficiaryPartyId="DEMO_ORG_AU_STEEL"
                issuingBankPartyId="DEMO_ORG_VPBANK" advisingBankPartyId="DEMO_ORG_ANZ"
                productId="PROD_ILC_NEGOTIATION" lcProductTypeEnumId="LC_PROD_NEGOTIATION" amount="1800000" amountCurrencyUomId="USD"
                confirmationInstructions_49="LC_CONF_CONFIRM"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked

        then:
        dataCheckErrors.size() == 0
    }

    def "validate demo related entities: drawings, amendments, charges"() {
        when:
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <!-- LC_01 Drawing 1: paid -->
            <moqui.trade.finance.LcDrawing lcId="DEMO_LC_01" drawingSeqId="01"
                drawingAmount="250000" drawingCurrencyUomId="USD" drawingStatusId="LcDrPaid"
                paymentAmount="250000" paymentReference="SWIFT-REF-001"/>
            <!-- LC_01 Drawing 2: paid -->
            <moqui.trade.finance.LcDrawing lcId="DEMO_LC_01" drawingSeqId="02"
                drawingAmount="250000" drawingCurrencyUomId="USD" drawingStatusId="LcDrPaid"
                paymentReference="SWIFT-REF-002"/>
            <!-- LC_01 Amendment: approved amount increase -->
            <moqui.trade.finance.LcAmendment lcId="DEMO_LC_01" amendmentSeqId="01"
                amendmentNumber="1" amendmentStatusId="LcTxApproved"/>
            <!-- LC_01 Charges: issuance + SWIFT -->
            <moqui.trade.finance.LcCharge lcId="DEMO_LC_01" chargeSeqId="01"
                chargeTypeEnumId="LC_CHG_ISSUANCE" chargeAmount="2500" chargeCurrencyUomId="USD"/>
            <moqui.trade.finance.LcCharge lcId="DEMO_LC_01" chargeSeqId="02"
                chargeTypeEnumId="LC_CHG_SWIFT" chargeAmount="150" chargeCurrencyUomId="USD"/>
            <!-- LC_10 Discrepancy: waived -->
            <moqui.trade.finance.LcDiscrepancy lcId="DEMO_LC_10" drawingSeqId="01" discrepancySeqId="01"
                discrepancyTypeEnumId="LC_DISC_AMOUNT" resolutionEnumId="LC_DISRES_WAIVED"/>
            <!-- LC_02 Provision: active -->
            <moqui.trade.finance.LcProvision lcId="DEMO_LC_02" provisionSeqId="01"
                provisionAmount="120000" provisionRate="10" provisionStatusId="LcPrvActive"/>
            <!-- LC_01 Provision: released -->
            <moqui.trade.finance.LcProvision lcId="DEMO_LC_01" provisionSeqId="01"
                provisionAmount="50000" provisionStatusId="LcPrvReleased"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked
        logger.info("Related entities data check: ${fieldsChecked} fields, errors: ${dataCheckErrors}")

        then:
        dataCheckErrors.size() == 0
    }

    def "validate demo LC_01 history audit trail"() {
        when:
        // LC_01 has 6 history entries tracking full lifecycle
        EntityList historyList = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", "DEMO_LC_01").orderBy("changeDate").list()

        then:
        historyList.size() >= 6
        // Use find to check for mandatory transitions in the audit trail
        historyList.find { it.newValue == "LcLfDraft" && it.fieldName == "lcStatusId" } != null
        historyList.find { it.newValue == "LcTxSubmitted" && it.fieldName == "transactionStatusId" } != null
        historyList.find { it.newValue == "LcTxApproved" && it.fieldName == "transactionStatusId" } != null
        historyList.find { it.newValue == "LcLfApplied" && it.fieldName == "lcStatusId" } != null
        historyList.find { it.newValue == "LcLfIssued" && it.fieldName == "lcStatusId" } != null
        historyList.find { it.newValue == "LcLfClosed" && it.fieldName == "lcStatusId" } != null
    }

    def "validate demo LC_09 rejection and reopen history trail"() {
        when:
        // LC_09 has 4 history entries: created, submitted, rejected, reopened
        // Use lcHistoryId for deterministic ordering in tests
        EntityList historyList = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", "DEMO_LC_09").orderBy("lcHistoryId").list()

        then:
        historyList.size() >= 4
        // Verify the created entry
        historyList.any { it.newValue == "LcLfDraft" }
        // Verify the submitted entry
        historyList.any { it.newValue == "LcTxSubmitted" }
        // Verify the rejection entry
        historyList.any { it.fieldName == "transactionStatusId" && it.oldValue == "LcTxSubmitted" && it.newValue == "LcTxRejected" }
        // Verify the reopen entry
        historyList.any { it.oldValue == "LcTxRejected" && it.newValue == "LcTxDraft" }
    }

    def "validate demo drawing documents for LC_01 Drawing 01"() {
        when:
        // LC_01 Drawing 01 has 5 documents: BL, invoice, packing list, CoO, insurance
        EntityList docList = ec.entity.find("moqui.trade.finance.LcDrawingDocument")
                .condition("lcId", "DEMO_LC_01").condition("drawingSeqId", "01")
                .orderBy("documentSeqId").list()

        then:
        docList.size() == 5
        docList[0].documentTypeEnumId == "LC_DOC_BILL_LADING"
        docList[1].documentTypeEnumId == "LC_DOC_COMM_INVOICE"
        docList[2].documentTypeEnumId == "LC_DOC_PACKING_LIST"
        docList[3].documentTypeEnumId == "LC_DOC_CERT_ORIGIN"
        docList[4].documentTypeEnumId == "LC_DOC_INSURANCE"
    }

    // =========================================================
    // 2. Validation Service Tests
    // =========================================================

    def "validate LC with valid SWIFT Character Set X fields"() {
        when:
        ec.message.clearErrors()
        // Use demo data values that should pass SWIFT validation
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.validate#LetterOfCredit")
                .parameters([lcNumber: "ILC-2026-0001", applicantName: "ABC Trading Co",
                             beneficiaryName: "XYZ Exports Pte Ltd"]).call()

        then:
        !ec.message.hasError()
    }

    def "validate LC fails for invalid SWIFT characters"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.validate#LetterOfCredit")
                .parameters([lcNumber: "ILC001", applicantName: "Invalid@Name#Corp"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "validate LC fails when LC Number exceeds 16 characters"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.validate#LetterOfCredit")
                .parameters([lcNumber: "12345678901234567"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "validate LC fails when Applicant Name exceeds 140 characters"() {
        when:
        ec.message.clearErrors()
        String longName = "A" * 141
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.validate#LetterOfCredit")
                .parameters([lcNumber: "ILC001", applicantName: longName]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "validate LC fails when Expiry Date is before Issue Date"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.validate#LetterOfCredit")
                .parameters([lcNumber: "ILC001", issueDate: "2026-06-01", expiryDate: "2026-05-01"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "validate LC fails for invalid Amount Tolerance format"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.validate#LetterOfCredit")
                .parameters([lcNumber: "ILC001", amountTolerance_39A: "ABC"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "validate LC passes for valid Amount Tolerance format"() {
        when:
        ec.message.clearErrors()
        // Use same tolerance format as DEMO_LC_01
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.validate#LetterOfCredit")
                .parameters([lcNumber: "ILC001", amountTolerance_39A: "5/5"]).call()

        then:
        !ec.message.hasError()
    }

    // =========================================================
    // 3. Create LC Service Tests
    // =========================================================

    def "create LC sets default statuses and returns lcId and requestId"() {
        when:
        ec.message.clearErrors()
        Map result = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
                .parameters([lcNumber: "TF-TEST-001", productId: "PROD_ILC_SIGHT",
                             applicantPartyId: "DEMO_ORG_ABC", beneficiaryPartyId: "DEMO_ORG_XYZ",
                             issuingBankPartyId: "DEMO_ORG_VIETCOMBANK", advisingBankPartyId: "DEMO_ORG_DBS",
                             applicantName: "Test Applicant Corp", beneficiaryName: "Test Beneficiary Ltd", 
                             amount: 100000.00, amountCurrencyUomId: "USD"]).call()
        // Store shared state for subsequent tests
        newLcId = result.lcId
        newRequestId = result.requestId

        // Verify entity data using makeDataLoader pattern
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <moqui.trade.finance.LetterOfCredit lcId="${newLcId}" lcNumber="TF-TEST-001"
                applicantPartyId="DEMO_ORG_ABC" beneficiaryPartyId="DEMO_ORG_XYZ"
                lcStatusId="LcLfDraft" transactionStatusId="LcTxDraft"
                productId="PROD_ILC_SIGHT" lcProductTypeEnumId="LC_PROD_SIGHT"
                requestId="${newRequestId}" amount="100000"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked
        logger.info("create LC data check: ${fieldsChecked} fields checked, errors: ${dataCheckErrors}")

        then:
        newLcId != null
        newRequestId != null
        dataCheckErrors.size() == 0
    }

    def "create LC creates linked Mantle Request in Draft status"() {
        when:
        // Verify the Request entity created in the previous test
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <mantle.request.Request requestId="${newRequestId}" statusId="ReqDraft"
                requestTypeEnumId="RqtLcIssuance"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked

        // Also verify automated charges were created
        EntityList charges = ec.entity.find("moqui.trade.finance.LcCharge")
                .condition("lcId", newLcId).list()

        then:
        dataCheckErrors.size() == 0
        charges.size() > 0
        charges.any { it.chargeTypeEnumId == 'LC_CHG_ISSUANCE' }
    }

    def "create LC records initial history entry"() {
        when:
        EntityList historyList = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", newLcId)
                .condition("changeType", "StatusChange")
                .condition("fieldName", "lcStatusId")
                .condition("newValue", "LcLfDraft").list()

        then:
        historyList.size() >= 1
        historyList.first.comments != null
    }

    // =========================================================
    // 4. Update LC Service Tests (using newly created LC)
    // =========================================================

    def "update newly created LC modifies fields and records history"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.update#LetterOfCredit")
                .parameters([lcId: newLcId, applicantName: "Updated Applicant Corp",
                             comments: "Updated for testing"]).call()

        // Verify updated field
        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", newLcId).one()
        // Verify history was recorded
        EntityList historyList = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", newLcId)
                .condition("changeType", "Update").list()

        then:
        !ec.message.hasError()
        lc.applicantName == "Updated Applicant Corp"
        historyList.size() >= 1
    }

    def "update LC fails for non-existent lcId"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.update#LetterOfCredit")
                .parameters([lcId: "NON_EXISTENT_ID", applicantName: "Test"]).call()

        then:
        if (!ec.message.hasError()) logger.info("Expected error not found. Messages: ${ec.message.getMessages()}, Errors: ${ec.message.getErrors()}")
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    // =========================================================
    // 5. Transaction Status Transition Tests
    //    (using newly created LC from section 3)
    // =========================================================

    def "transition Transaction Status Draft to Submitted (new LC)"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: newLcId, toStatusId: "LcTxSubmitted",
                             comments: "Submitted for approval"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", newLcId).one()

        then:
        !ec.message.hasError()
        lc.transactionStatusId == "LcTxSubmitted"
    }

    def "transition Transaction Status Submitted to Approved (new LC)"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: newLcId, toStatusId: "LcTxApproved",
                             comments: "Approved by manager"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", newLcId).one()

        then:
        !ec.message.hasError()
        lc.transactionStatusId == "LcTxApproved"
    }

    def "transition records history with old and new values"() {
        when:
        // Check history for the Submitted -> Approved transition on new LC
        EntityList historyList = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", newLcId)
                .condition("fieldName", "transactionStatusId")
                .condition("newValue", "LcTxApproved").list()

        then:
        historyList.size() >= 1
        historyList.first.oldValue == "LcTxSubmitted"
        historyList.first.changeType == "StatusChange"
    }

    def "transition Transaction Status Approved to Closed (new LC)"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: newLcId, toStatusId: "LcTxClosed"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", newLcId).one()

        then:
        !ec.message.hasError()
        lc.transactionStatusId == "LcTxClosed"
    }

    // =========================================================
    // 6. LC Lifecycle Status Transition Tests
    //    (using newly created LC from section 3)
    // =========================================================

    def "transition LC Lifecycle Draft to Applied (new LC)"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: newLcId, toStatusId: "LcLfApplied",
                             comments: "Application submitted"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", newLcId).one()

        then:
        !ec.message.hasError()
        lc.lcStatusId == "LcLfApplied"
    }

    def "transition LC Lifecycle Applied to Issued (new LC)"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: newLcId, toStatusId: "LcLfIssued",
                             comments: "LC issued by bank"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", newLcId).one()

        then:
        !ec.message.hasError()
        lc.lcStatusId == "LcLfIssued"
    }

    def "lifecycle transition records history"() {
        when:
        EntityList historyList = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", newLcId)
                .condition("fieldName", "lcStatusId")
                .condition("newValue", "LcLfIssued").list()

        then:
        historyList.size() >= 1
        historyList.first.oldValue == "LcLfApplied"
    }

    // =========================================================
    // 7. Invalid Transition Tests (using demo data)
    // =========================================================

    def "invalid LC Lifecycle transition is rejected (demo LC_05, Draft to Issued)"() {
        when:
        ec.message.clearErrors()
        // DEMO_LC_05 is in Draft. Attempt invalid: Draft -> Issued (skipping Applied)
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: "DEMO_LC_05", toStatusId: "LcLfIssued"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "invalid Transaction Status transition is rejected (demo LC_05, Draft to Approved)"() {
        when:
        ec.message.clearErrors()
        // DEMO_LC_05 is in Draft. Attempt invalid: Draft -> Approved (skipping Submitted)
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: "DEMO_LC_05", toStatusId: "LcTxApproved"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "transition fails for non-existent LC"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: "NON_EXISTENT", toStatusId: "LcLfApplied"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    // =========================================================
    // 8. Rejection and Reopen Flow Tests (using demo LC_09)
    //    DEMO_LC_09 is already in Draft/Draft (reopened).
    //    We re-test the Submit -> Reject -> Reopen cycle.
    // =========================================================

    def "submit demo LC_09 for rejection test"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: "DEMO_LC_09", toStatusId: "LcTxSubmitted",
                             comments: "Re-submitted after corrections"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", "DEMO_LC_09").one()

        then:
        !ec.message.hasError()
        lc.transactionStatusId == "LcTxSubmitted"
    }

    def "reject demo LC_09"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: "DEMO_LC_09", toStatusId: "LcTxRejected",
                             comments: "Still needs beneficiary correction"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", "DEMO_LC_09").one()

        then:
        !ec.message.hasError()
        lc.transactionStatusId == "LcTxRejected"
    }

    def "reopen demo LC_09 back to Draft"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: "DEMO_LC_09", toStatusId: "LcTxDraft",
                             comments: "Reopened again for correction"]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", "DEMO_LC_09").one()

        then:
        !ec.message.hasError()
        lc.transactionStatusId == "LcTxDraft"
    }

    // =========================================================
    // 9. Delete LC Service Tests
    // =========================================================

    def "create LC for delete test"() {
        when:
        ec.message.clearErrors()
        Map result = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
                .parameters([lcNumber: "TF-TEST-DELETE", applicantName: "Delete Test Corp"]).call()
        deleteLcId = result.lcId

        then:
        deleteLcId != null
    }

    def "delete LC succeeds for Draft status"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.delete#LetterOfCredit")
                .parameters([lcId: deleteLcId]).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", deleteLcId).one()

        then:
        !ec.message.hasError()
        lc == null
    }

    def "delete LC fails for non-Draft status (demo LC_04 Applied)"() {
        when:
        ec.message.clearErrors()
        // DEMO_LC_04 is Applied/Submitted — cannot be deleted
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.delete#LetterOfCredit")
                .parameters([lcId: "DEMO_LC_04"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "delete LC fails for Closed status (demo LC_01)"() {
        when:
        ec.message.clearErrors()
        // DEMO_LC_01 is Closed/Closed — cannot be deleted
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.delete#LetterOfCredit")
                .parameters([lcId: "DEMO_LC_01"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    // =========================================================
    // 10. Full Lifecycle Data Validation (Summary Check)
    // =========================================================

    def "validate full lifecycle for new test LC"() {
        when:
        // The new LC (newLcId) should be: Issued lifecycle, Closed transaction
        List<String> dataCheckErrors = []
        long fieldsChecked = ec.entity.makeDataLoader().xmlText("""<entity-facade-xml>
            <moqui.trade.finance.LetterOfCredit lcId="${newLcId}" lcNumber="TF-TEST-001"
                applicantName="Updated Applicant Corp" beneficiaryName="Test Beneficiary Ltd"
                lcStatusId="LcLfIssued" transactionStatusId="LcTxClosed"
                productId="PROD_ILC_SIGHT" lcProductTypeEnumId="LC_PROD_SIGHT"
                requestId="${newRequestId}" amount="100000"/>
        </entity-facade-xml>""").check(dataCheckErrors)
        totalFieldsChecked += fieldsChecked
        logger.info("Full lifecycle data check: ${fieldsChecked} fields, errors: ${dataCheckErrors}")

        // Verify complete history trail
        EntityList historyList = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", newLcId).orderBy("-changeDate").list()
        logger.info("Total history entries for new LC ${newLcId}: ${historyList.size()}")

        then:
        dataCheckErrors.size() == 0
        // At minimum: create + TxSubmitted + TxApproved + TxClosed + LfApplied + LfIssued = 6
        historyList.size() >= 6
    }

    def "validate all 10 demo LCs exist"() {
        when:
        EntityList allDemoLcs = ec.entity.find("moqui.trade.finance.LetterOfCredit")
                .condition("lcId", EntityCondition.LIKE, "DEMO_LC_%").list()

        then:
        allDemoLcs.size() == 10
    }

    def "block issuance if mandatory charge is missing (BR3)"() {
        setup: "Setup an LC with a missing mandatory charge"
        // 1. Create a new LC for a product that has mandatory charges (e.g. PROD_ILC_SIGHT has ISSUANCE as mandatory in demo data/10_TradeFinanceData.xml)
        Map createOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
                .parameters([lcNumber: "MAND-CHG-TEST", productId: "PROD_ILC_SIGHT", amount: 10000.00]).call()
        String mandLcId = createOut.lcId
        
        // 2. Automated charges should be created. Delete the mandatory one.
        ec.entity.find("moqui.trade.finance.LcCharge").condition("lcId", mandLcId).condition("chargeTypeEnumId", "LC_CHG_ISSUANCE").list().each { it.delete() }
        
        // 3. Move to Approved transaction status so we can attempt issuance
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: mandLcId, toStatusId: "LcTxSubmitted"]).call()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: mandLcId, toStatusId: "LcTxApproved"]).call()

        when: "Attempt to issue the LC"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: mandLcId, toStatusId: "LcLfApplied"]).call()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: mandLcId, toStatusId: "LcLfIssued"]).call()

        then: "The service should fail"
        ec.message.hasError()
        ec.message.errors.any { it.contains("Mandatory charge") || it.contains("missing") }
        
        cleanup:
        ec.message.clearErrors()
    }
}
