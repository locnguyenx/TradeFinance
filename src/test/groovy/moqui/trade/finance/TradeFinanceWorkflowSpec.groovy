package moqui.trade.finance

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Stepwise
import org.moqui.Moqui
import org.moqui.context.ExecutionContext

@Stepwise
class TradeFinanceWorkflowSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceWorkflowSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    def "Create LC and Update Collateral (Step 4)"() {
        when: "Create a Draft LC"
        Map createOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
            .parameters([
                lcNumber: "WF-TEST-001",
                productId: "PROD_ILC_SIGHT",
                applicantPartyId: "DEMO_ORG_ABC",
                beneficiaryPartyId: "DEMO_ORG_XYZ",
                amount: 100000.00,
                amountCurrencyUomId: "USD",
                expiryDate: ec.user.nowTimestamp + 30
            ]).call()
        lcId = createOut.lcId

        then: "LC is created in Draft status"
        lcId != null
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        lc.transactionStatusId == "LcTxDraft"
        lc.lcStatusId == "LcLfDraft"

        when: "Update Collateral and Credit Agreement (Step 4)"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.update#LcApplicationDetail")
            .parameters([
                lcId: lcId,
                isSecured: "Y",
                securedPercentage: 10,
                creditAgreementId: "CA-MOCK-001",
                collateralDescription: "Cash margin 10%"
            ]).call()

        then: "LC is updated and credit limit is retrieved from CBS"
        def updatedLc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        updatedLc.isSecured == "Y"
        updatedLc.creditAgreementId == "CA-MOCK-001"
        updatedLc.availableCreditLimit != null
        updatedLc.availableCreditLimit > 0
    }

    def "Perform Multi-Level Approval Workflow"() {
        when: "Submit for Review (Draft -> Pending Review)"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.submit#LetterOfCredit")
            .parameter("lcId", lcId).call()

        then: "Status is Pending Review"
        ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one().transactionStatusId == "LcTxPendingReview"

        when: "Branch Supervisor Approve (Pending Review -> Pending Processing)"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcBySupervisor")
            .parameter("lcId", lcId).call()

        then: "Status is Pending Processing"
        ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one().transactionStatusId == "LcTxPendingProcessing"

        when: "Trade Operator Approve (Pending Processing -> Pending Approval)"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeOperator")
            .parameter("lcId", lcId).call()

        then: "Status is Pending Approval"
        ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one().transactionStatusId == "LcTxPendingApproval"

        when: "Trade Supervisor Final Approve (Pending Approval -> Approved)"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeSupervisor")
            .parameter("lcId", lcId).call()

        then: "Transaction is Approved and LC Status is Applied"
        def finalLc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        finalLc.transactionStatusId == "LcTxApproved"
        finalLc.lcStatusId == "LcLfApplied"
    }

    def "Verify Return Workflow"() {
        given: "Another test LC"
        Map createOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
            .parameters([lcNumber: "WF-TEST-002", productId: "PROD_ILC_SIGHT", applicantPartyId: "DEMO_ORG_ABC", beneficiaryPartyId: "DEMO_ORG_XYZ", amount: 50000, amountCurrencyUomId: "USD", expiryDate: ec.user.nowTimestamp + 30]).call()
        String testLcId = createOut.lcId

        when: "Submit and then Return"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.submit#LetterOfCredit").parameter("lcId", testLcId).call()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.return#LetterOfCredit")
            .parameters([lcId: testLcId, comments: "Missing document"]).call()

        then: "Status is Returned"
        ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", testLcId).one().transactionStatusId == "LcTxReturned"
    }
}
