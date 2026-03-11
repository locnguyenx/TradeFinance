package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import java.sql.Timestamp

@Stepwise
class TradeFinancePhase4Spec extends Specification {
    @Shared protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeFinancePhase4Spec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = "DEMO_LC_08" // Reuse issued LC from Phase 3

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() { ec.artifactExecution.disableAuthz() }
    def cleanup() { ec.artifactExecution.enableAuthz() }

    // =========================================================
    // 1. Amendment Validation & Confirmation Tests
    // =========================================================
    def "fail to amend restricted field (lcNumber)"() {
        when: "We attempt to create an amendment for lcNumber"
        def result = ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
                .parameters([lcId: lcId, fieldName: "lcNumber", newValue: "NEWLC123"]).call()
        
        then: "An error should be returned"
        ec.message.errors.any { it.contains("cannot be amended") }

        cleanup:
        ec.message.clearAll()
    }

    def "successfully process amendment from creation to confirmation"() {
        setup:
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        String oldShipDate = lc.latestShipDate_44C ? lc.latestShipDate_44C.toString() : "None"
        String newShipDate = "2026-12-31"

        when: "We create a valid amendment"
        def createRes = ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
                .parameters([lcId: lcId, fieldName: "latestShipDate_44C", newValue: newShipDate]).call()
        def amndSeqId = createRes.amendmentSeqId

        then: "Amendment is created with Pending confirmation"
        amndSeqId != null
        def amnd = ec.entity.find("moqui.trade.finance.LcAmendment").condition([lcId:lcId, amendmentSeqId:amndSeqId]).one()
        amnd.confirmationStatusId == "LcAmndPending"

        when: "We submit and approve the amendment internally"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.submit#LcAmendment")
                .parameters([lcId: lcId, amendmentSeqId: amndSeqId]).call()
        amnd = ec.entity.find("moqui.trade.finance.LcAmendment").condition([lcId:lcId, amendmentSeqId:amndSeqId]).one()
        println(">>> AFTER SUBMIT: " + amnd.amendmentStatusId)
        
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.approve#LcAmendment")
                .parameters([lcId: lcId, amendmentSeqId: amndSeqId]).call()
        if (ec.message.hasError()) {
            println(">>> SERVICE ERRORS: " + ec.message.getErrorsString())
        }
        amnd = ec.entity.find("moqui.trade.finance.LcAmendment").condition([lcId:lcId, amendmentSeqId:amndSeqId]).one()
        println(">>> AFTER APPROVE: " + amnd.amendmentStatusId)

        then: "Transaction status is Approved, but LC is not yet updated"
        amnd.amendmentStatusId == "LcTxApproved"
        lc.refresh()
        lc.latestShipDate_44C.toString() != newShipDate

        when: "We confirm the amendment (Simulation of external advice)"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.confirm#LcAmendment")
                .parameters([lcId: lcId, amendmentSeqId: amndSeqId, isAccepted: true]).call()
        
        lc.refresh()
        amnd.refresh()

        then: "LC is now updated (Effective), Amendment is Confirmed and Closed"
        lc.latestShipDate_44C.toString().contains(newShipDate)
        lc.lcStatusId == "LcLfAmended"
        lc.amendmentNumber == 1
        amnd.confirmationStatusId == "LcAmndConfirmed"
        amnd.amendmentStatusId == "LcTxClosed"
        
        and: "An MT707 Document should have been generated"
        def mt707Docs = ec.entity.find("moqui.trade.finance.LcDocument")
                            .condition("lcId", lcId)
                            .condition("documentReference", "MT707_" + amndSeqId).list()
        mt707Docs.size() == 1
    }

    // =========================================================
    // 2. LC Revocation Tests
    // =========================================================
    def "fail to revoke an irrevocable LC"() {
        setup: "DEMO_LC_08 is Irrevocable by default"
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        
        when: "We attempt to revoke it"
        ec.service.sync().name("moqui.trade.finance.LifecycleServices.revoke#LetterOfCredit")
                .parameters([lcId: lcId]).call()
        
        then: "It should fail validation"
        ec.message.errors.any { it.contains("It is not a Revocable LC") }

        cleanup:
        ec.message.clearAll()
    }

    def "successfully revoke a revocable LC"() {
        setup: "Change DEMO_LC_08 to Revocable for this test"
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        lc.formOfCredit_40A = "LC_FORM_REVOCABLE"
        lc.update()
        
        // Ensure there is an active provision to release
        ec.service.sync().name("moqui.trade.finance.FinancialServices.hold#LcProvision")
                .parameters([lcId: lcId, provisionRate: 0.10]).call()

        when: "We revoke the LC"
        ec.service.sync().name("moqui.trade.finance.LifecycleServices.revoke#LetterOfCredit")
                .parameters([lcId: lcId, comments: "Revocation Test"]).call()
        
        // Re-fetch LC to ensure we have latest status from DB
        lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        
        then: "LC status is Revoked"
        lc.lcStatusId == "LcLfRevoked"
        
        and: "Provisions are released"
        def prov = ec.entity.find("moqui.trade.finance.LcProvision")
                       .condition("lcId", lcId)
                       .condition("provisionStatusId", "LcPrvActive").list()
        prov.size() == 0
        
        and: "MT799 document is generated"
        def mt799Docs = ec.entity.find("moqui.trade.finance.LcDocument")
                            .condition("lcId", lcId)
                            .condition("description", org.moqui.entity.EntityCondition.LIKE, "%MT799%")
                            .list()
        mt799Docs.size() >= 1
    }
}
