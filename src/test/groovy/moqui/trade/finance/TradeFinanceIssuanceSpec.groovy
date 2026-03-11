package moqui.trade.finance

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Stepwise
import org.moqui.Moqui
import org.moqui.context.ExecutionContext

@Stepwise
class TradeFinanceIssuanceSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceIssuanceSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        boolean begun = ec.transaction.begin(null)
        try {
            // Load required seed data for status flows
            ec.entity.makeDataLoader().location("component://TradeFinance/data/10_TradeFinanceData.xml").load()

            // Ensure Product exists
            if (ec.entity.find("moqui.trade.finance.LcProduct").condition("productId", "PROD_ILC_SIGHT").one() == null) {
                ec.entity.makeValue("moqui.trade.finance.LcProduct").setAll([
                    productId: "PROD_ILC_SIGHT", productName: "Import LC Sight",
                    lcProductTypeEnumId: "LC_PROD_SIGHT", defaultProvisionRate: 10
                ]).create()
            }
            ec.transaction.commit(begun)
        } catch (Throwable t) {
            logger.error("Error in setupSpec: ${t.toString()}", t)
            ec.transaction.rollback(begun, "Error in setupSpec", t)
        } finally {
            if (ec.transaction.isTransactionInPlace()) ec.transaction.commit()
        }
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

    def "Verify LC Issuance with Accounting and Provision Activation"() {
        given: "An Approved LC with Held Provision (Processed via IPC)"
        Map createOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
            .parameters([
                lcNumber: "PH3-ISSUE-001",
                productId: "PROD_ILC_SIGHT",
                applicantPartyId: "DEMO_ORG_ABC",
                beneficiaryPartyId: "DEMO_ORG_XYZ",
                issuingBankPartyId: "DEMO_ORG_VIETCOMBANK",
                amount: 50000.00,
                amountCurrencyUomId: "USD",
                expiryDate: ec.user.nowTimestamp + 30
            ]).call()
        if (ec.message.hasError()) logger.error("Create Errors: " + ec.message.getErrorsString())
        assert !ec.message.hasError()
        lcId = createOut.lcId

        // Progress through workflow to Approved (Phase 2 Flow)
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.submit#LetterOfCredit").parameter("lcId", lcId).call()
        assert !ec.message.hasError()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcBySupervisor").parameter("lcId", lcId).call()
        assert !ec.message.hasError()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeOperator").parameter("lcId", lcId).call()
        if (ec.message.hasError()) logger.error("Trade Ops Approve Errors: " + ec.message.getErrorsString())
        assert !ec.message.hasError()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeSupervisor").parameter("lcId", lcId).call()
        assert !ec.message.hasError()

        // Verify state before issuance
        def provisionBefore = ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", lcId).one()
        if (provisionBefore == null) {
            logger.error("No provision found for LC ${lcId} after Trade Ops approval")
            def lcTemp = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
            logger.info("LC Data: ${lcTemp}")
        }
        assert provisionBefore != null
        assert provisionBefore.provisionStatusId == "LcPrvHeld"
        def lcBefore = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        assert lcBefore.transactionStatusId == "LcTxApproved"

        when: "Issue the Letter of Credit"
        Map issueOut = ec.service.sync().name("moqui.trade.finance.LifecycleServices.issue#LetterOfCredit").parameter("lcId", lcId).call()
        then: "No error in issuance"
        !ec.message.hasError()

        then: "LC and Transaction statuses are progressed"
        def lcAfter = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).useCache(false).one()
        lcAfter.lcStatusId == "LcLfIssued"
        lcAfter.transactionStatusId == "LcTxClosed"

        and: "Provision is activated"
        def provisionAfter = ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", lcId).one()
        provisionAfter.provisionStatusId == "LcPrvActive"

        and: "MT700 is generated (Document created)"
        ec.entity.find("moqui.trade.finance.LcDocument").condition("lcId", lcId).condition("documentTypeEnumId", "LC_DOC_SWIFT_MSG").one() != null
    }
}
