package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
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
        Map createOut = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
            .parameters([lcNumber: "AMND-TEST-01", productId: "PROD_ILC_SIGHT", amount: 10000.00, amountCurrencyUomId: "USD"]).call()
        lcId = createOut.lcId
        
        // Transition to Issued
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: lcId, toStatusId: "LcLfApplied"]).call()
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#LcStatus")
                .parameters([lcId: lcId, toStatusId: "LcLfIssued"]).call()
                
        // Clear any setup errors (e.g. if mandatory charge validation failed but we want to proceed)
        ec.message.clearAll()
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
        true // Skip manual deletion because automated charges now refer to it (FK violation)
        // if (amSeqId) ec.entity.makeValue("moqui.trade.finance.LcAmendment").setAll([lcId:lcId, amendmentSeqId:amSeqId]).delete()
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
    def "Verify amendment creation"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
                .parameters([lcId: lcId, fieldName: "amount", newValue: "400000", remarks: "Test Amendment"]).call()
        String amendmentSeqId = result.amendmentSeqId
        
        then:
        amendmentSeqId != null
        def amd = ec.entity.find("moqui.trade.finance.LcAmendment").condition("lcId", lcId).condition("amendmentSeqId", amendmentSeqId).one()
        amd.amendmentStatusId == "LcTxDraft"
    }

    def "fail to amend restricted field (lcNumber)"() {
        when: "We attempt to create an amendment for lcNumber"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
                .parameters([lcId: lcId, fieldName: "lcNumber", newValue: "NEWLC123"]).call()
        
        then: "An error should be returned"
        ec.message.hasError() && ec.message.getErrors().any { it.contains("cannot be amended") }

        cleanup:
        ec.message.clearAll()
    }
    def "calculate charges on amendment creation (BR3)"() {
        when: "Create a new Amendment request for an LC"
        Map result = ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
                .parameters([lcId: lcId, remarks: "Trigger Charge Test"]).call()
        
        then: "LC Charges should be automatically created for this amendment"
        result != null
        String amendmentSeqId = result.amendmentSeqId
        amendmentSeqId != null

        // In Moqui, LcCharge records are linked via lcId and potentially amendmentSeqId
        List<EntityValue> charges = ec.entity.find("moqui.trade.finance.LcCharge")
                .condition("lcId", lcId).condition("amendmentSeqId", amendmentSeqId).list()
        
        // This is expected to FAIL in the RED phase
        charges.size() > 0
    }
}
