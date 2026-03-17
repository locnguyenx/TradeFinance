package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import java.sql.Timestamp

class TradeFinanceLifecycleSpec extends Specification {
    @Shared protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeFinanceLifecycleSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = "DEMO_LC_08"

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
    // 1. LC Revocation Tests (from Phase 4)
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

    // =========================================================
    // 2. Scheduled Expiry Tests (from Phase 3)
    // =========================================================
    def "scheduled auto-expiry test"() {
        setup: "Set an LC's expiry date to yesterday and status to Issued"
        def expireLcId = "DEMO_LC_01"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.update#LetterOfCredit")
          .parameters([lcId: expireLcId, lcStatusId: "LcLfIssued", expiryDate: new Timestamp(System.currentTimeMillis() - 86400000L)]).call()
        
        // Ensure there is an active provision to release for DEMO_LC_01
        ec.service.sync().name("moqui.trade.finance.FinancialServices.hold#LcProvision")
                .parameters([lcId: expireLcId, provisionRate: 0.10]).call()

        when: "We run the Scheduled Expiry Job manually"
        ec.service.sync().name("moqui.trade.finance.ScheduledServices.check#LcExpiry").call()
        
        def expiredLc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", expireLcId).one()
        
        then: "The LC must have been transitioned to Expired automatically"
        expiredLc.lcStatusId == "LcLfExpired"

        and: "All provisions must have been released"
        def provisions = ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", expireLcId).list()
        provisions.size() > 0
        provisions.every { it.provisionStatusId == 'LcPrvReleased' }

        and: "History should record the release"
        def history = ec.entity.find("moqui.trade.finance.LcHistory").condition("lcId", expireLcId).list()
        history.any { it.comments.contains("Provision released") }
    }
}
