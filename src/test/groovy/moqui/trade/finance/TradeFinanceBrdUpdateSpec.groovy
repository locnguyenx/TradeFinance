package moqui.trade.finance

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Stepwise
import org.moqui.Moqui
import org.moqui.context.ExecutionContext

@Stepwise
class TradeFinanceBrdUpdateSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceBrdUpdateSpec.class)
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

    def "Verify Automated Provision and Funds Hold in IPC Approval"() {
        when: "Create a Draft LC for a product with 10% provision"
        // PROD_ILC_SIGHT has 10% provision in 10_TradeFinanceData.xml
        Map createOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
            .parameters([
                lcNumber: "BRD2-TEST-001",
                productId: "PROD_ILC_SIGHT",
                applicantPartyId: "DEMO_ORG_ABC",
                beneficiaryPartyId: "DEMO_ORG_XYZ",
                amount: 100000.00,
                amountCurrencyUomId: "USD",
                expiryDate: ec.user.nowTimestamp + 30
            ]).call()
        lcId = createOut.lcId

        then: "LC is created"
        lcId != null

        when: "Progress through workflow to Pending Processing (requires Branch Supervisor approval)"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.submit#LetterOfCredit").parameter("lcId", lcId).call()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcBySupervisor").parameter("lcId", lcId).call()

        then: "Status is Pending Processing"
        ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one().transactionStatusId == "LcTxPendingProcessing"

        when: "Trade Operator Approves (IPC Processing)"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeOperator").parameter("lcId", lcId).call()

        then: "Provision is calculated (10% of 100k = 10k) and held"
        def provision = ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", lcId).one()
        provision != null
        provision.provisionAmount == 10000.00
        provision.provisionStatusId == "LcPrvHeld"
        provision.cbsHoldReference != null

        and: "Status is moved to Pending Approval"
        ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one().transactionStatusId == "LcTxPendingApproval"
    }

    def "Verify Funds Hold Failure Blocks Approval"() {
        when: "Create an LC with a specific amount that triggers mock hold failure (999.99)"
        // Note: LcProvision is calculated as % of total. We need Provision Amount to be 999.99.
        // If rate is 10%, we need total amount to be 9999.90.
        Map createOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
            .parameters([
                lcNumber: "BRD2-FAIL-HOLD",
                productId: "PROD_ILC_SIGHT",
                applicantPartyId: "DEMO_ORG_ABC",
                beneficiaryPartyId: "DEMO_ORG_XYZ",
                amount: 9999.90,
                amountCurrencyUomId: "USD",
                expiryDate: ec.user.nowTimestamp + 30
            ]).call()
        String failLcId = createOut.lcId

        and: "Progress to Pending Processing"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.submit#LetterOfCredit").parameter("lcId", failLcId).call()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcBySupervisor").parameter("lcId", failLcId).call()

        and: "Try to Approve as Trade Operator (should fail due to 999.99 provision hold)"
        Map approveOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.approve#LcByTradeOperator")
            .parameter("lcId", failLcId).call()

        then: "Approval fails with error"
        ec.message.hasError()
        ec.message.errors.any { it.contains("Insufficient funds") }
        
        and: "Status remains Pending Processing"
        ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", failLcId).one().transactionStatusId == "LcTxPendingProcessing"
    }
}
