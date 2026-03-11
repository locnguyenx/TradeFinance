package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification

class TradeFinanceAmendmentSpec extends Specification {
    @Shared ExecutionContext ec
    @Shared String lcId

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        
        // Disable authorization for setup
        ec.artifactExecution.disableAuthz()
        
        // Ensure seed data is loaded
        ec.entity.makeDataLoader().location("component://TradeFinance/data/10_TradeFinanceData.xml").load()
        
        ec.user.loginUser("tf-admin", "moqui")
        
        // Create an initial LC in Issued status for amendment testing
        Map createOut = ec.service.sync().name("create#moqui.trade.finance.LetterOfCredit").parameter("lcNumber", "AMND_TEST_01")
            .parameter("lcStatusId", "LcLfIssued").parameter("amount", 10000.00).parameter("amountCurrencyUomId", "USD").call()
        lcId = createOut.lcId
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def "Verify Shadow Field Cloning on Amendment Creation"() {
        when: "Create a new Amendment request"
        Map createAmOut = ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
            .parameter("lcId", lcId).parameter("remarks", "Test Amendment").call()
        String amSeqId = createAmOut.amendmentSeqId

        then: "Amendment record exists and matches master LC"
        def amendment = ec.entity.find("moqui.trade.finance.LcAmendment").condition("lcId", lcId).condition("amendmentSeqId", amSeqId).one()
        amendment != null
        amendment.amount == 10000.00
        amendment.amendmentStatusId == "LcTxDraft"
        amendment.confirmationStatusId == "LcAmndPending"

        cleanup:
        if (amSeqId) ec.entity.makeValue("moqui.trade.finance.LcAmendment").setAll([lcId:lcId, amendmentSeqId:amSeqId]).delete()
    }

    def "Verify Apply Amendment Back to Master LC"() {
        setup:
        Map createAmOut = ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
            .parameter("lcId", lcId).parameter("remarks", "Applying Changes").call()
        String amSeqId = createAmOut.amendmentSeqId

        when: "Update a shadow field and confirm"
        ec.entity.makeValue("moqui.trade.finance.LcAmendment").setAll([lcId:lcId, amendmentSeqId:amSeqId, amount: 25000.00]).update()
        
        // Submit first
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.submit#LcAmendment").parameter("lcId", lcId).parameter("amendmentSeqId", amSeqId).call()

        // Approve
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.approve#LcAmendment").parameter("lcId", lcId).parameter("amendmentSeqId", amSeqId).call()
        
        // Confirm/Apply
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.confirm#LcAmendment").parameter("lcId", lcId).parameter("amendmentSeqId", amSeqId).call()

        then: "Master LC is updated"
        def lcAfter = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        lcAfter.amount == 25000.00
        lcAfter.amendmentNumber == 1
        lcAfter.lcStatusId == "LcLfAmended"

        then: "Amendment status is closed"
        def amAfter = ec.entity.find("moqui.trade.finance.LcAmendment").condition("lcId", lcId).condition("amendmentSeqId", amSeqId).one()
        amAfter.amendmentStatusId == "LcTxClosed"
        amAfter.confirmationStatusId == "LcAmndConfirmed"
    }
}
