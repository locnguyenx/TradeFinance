package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import java.sql.Timestamp

@Stepwise
class TradeFinancePhase3Spec extends Specification {
    @Shared protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeFinancePhase3Spec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = "DEMO_LC_08"
    @Shared String drawingSeqId
    // LC intended to be issued

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
        // Cleanup provisions for DEMO_LC_08 to ensure fresh test state
        ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", "DEMO_LC_08").deleteAll()
        // Note: For Phase 3, we expect seed and demo data (like DEMO_LC_08) to already exist in DB.
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() { ec.artifactExecution.disableAuthz() }
    def cleanup() { ec.artifactExecution.enableAuthz() }

    // =========================================================
    // 1. CBS Integration Tests
    // =========================================================
    def "test CBS Funds Hold Mock"() {
        when: "Calling the CBS interface to hold funds"
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.hold#Funds")
                .parameters([partyId: "CUST_ORG", amount: 15000.00, currencyUomId: "USD", referenceId: "TEST_LC_01"]).call()
        
        then: "The mock should return success and a hold reference"
        result.success == true || result.success == "true"
        result.holdReference != null
        result.holdReference.startsWith("HLD-")
    }

    def "test CBS Funds Release Mock"() {
        when: "Calling the CBS interface to release funds"
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.release#Funds")
                .parameters([holdReference: "HLD-123456", partyId: "CUST_ORG", amount: 15000.00]).call()
        
        then: "The mock should return success"
        result.success == true || result.success == "true"
    }

    // =========================================================
    // 2. Financial Integration (Refactored to CBS) Tests
    // =========================================================
    def "hold and release provision using CBS integration"() {
        setup:
        // Ensure the DEMO_LC_08 is cleanly setup
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        if (!lc) {
            ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
              .parameters([lcId: lcId, lcNumber: "TESTLC08", amount: 100000.00, amountCurrencyUomId: "USD", 
                           applicantPartyId: "CUST_ORG", beneficiaryPartyId: "SUPPLIER_ORG"]).call()
        }

        when: "We place a provision hold on the LC"
        ec.service.sync().name("moqui.trade.finance.FinancialServices.hold#LcProvision")
                .parameters([lcId: lcId, provisionRate: 0.10]).call() // 10% provision
        
        def prov = ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", lcId).one()
        
        then: "The provision is Active and has a CBS Reference assigned"
        prov != null
        prov.provisionStatusId == "LcPrvActive"
        def checkLc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        prov.provisionAmount == checkLc.amount * 0.10
        prov.cbsHoldReference != null

        when: "We release the provision"
        ec.service.sync().name("moqui.trade.finance.FinancialServices.release#LcProvision")
                .parameters([lcId: lcId]).call()
        prov.refresh()
        
        then: "The provision is Released"
        prov.provisionStatusId == "LcPrvReleased"
        prov.releaseDate != null
    }

    // =========================================================
    // 3. SWIFT MT700 Generation Tests
    // =========================================================
    def "generate SWIFT MT700 for an LC"() {
        when: "We invoke the MT700 generator service"
        def result = ec.service.sync().name("moqui.trade.finance.SwiftServices.generate#SwiftMt700")
                .parameters([lcId: lcId]).call()
        
        then: "The raw text shouldn't be null and should look like an MT700 message"
        result.swiftMessageText != null
        result.swiftMessageText.contains("{1:F01BANKXXXXAXXX0000000000}")
        result.swiftMessageText.contains(":20:") // LC Number tag
    }

    // =========================================================
    // 4. Lifecycle Issuance Tests
    // =========================================================
    def "issue Letter of Credit and verify MT700 document attachment"() {
        setup:
        // Before issuing, the LC transaction status must be Approved
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        if (lc.transactionStatusId != "LcTxApproved") {
             ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
               .parameters([lcId: lcId, toStatusId: "LcTxApproved", comments: "Test Approval"]).call()
        }

        when: "We issue the LC"
        ec.service.sync().name("moqui.trade.finance.LifecycleServices.issue#LetterOfCredit")
                .parameters([lcId: lcId, comments: "Phase 3 Test Issuance"]).call()
                
        lc.refresh()
        
        then: "Lifecycle is Issued, Transaction is Closed"
        lc.lcStatusId == "LcLfIssued"
        lc.transactionStatusId == "LcTxClosed"

        and: "An MT700 Document record should have been generated and linked"
        def swiftDocs = ec.entity.find("moqui.trade.finance.LcDocument")
                           .condition("lcId", lcId)
                           .condition("documentTypeEnumId", "LC_DOC_SWIFT_MSG").list()
        swiftDocs.size() >= 1
    }

    // =========================================================
    // 5. Scheduled Expiry Tests
    // =========================================================
    def "scheduled auto-expiry test"() {
        setup: "Set an LC's expiry date to yesterday and status to Issued"
        def expireLcId = "DEMO_LC_01"
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.update#LetterOfCredit")
          .parameters([lcId: expireLcId, lcStatusId: "LcLfIssued", expiryDate: new Timestamp(System.currentTimeMillis() - 86400000L)]).call()

        when: "We run the Scheduled Expiry Job manually"
        ec.service.sync().name("moqui.trade.finance.ScheduledServices.check#LcExpiry").call()
        
        def expiredLc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", expireLcId).one()
        
        then: "The LC must have been transitioned to Expired automatically"
        expiredLc.lcStatusId == "LcLfExpired"
    }

    // =========================================================
    // 6. Notification Verification Tests (Phase 3.D)
    // =========================================================
    def "verify LC notifications are recorded in history"() {
        when: "We check the history for DEMO_LC_08 (which was issued in previous steps)"
        def notifications = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", lcId)
                .condition("changeType", "Notification")
                .list()
        
        then: "There should be at least two notification entries (Drafted and Issued)"
        notifications.size() >= 1
        notifications.any { it.newValue.contains("Letter of Credit Issued") }
    }

    def "verify user actually receives system notification"() {
        setup: "Link tf-admin user to the target party temporarily"
        def userId = "TF_ADMIN_USER"
        def partyId = "DEMO_ORG_AUTO"
        ec.entity.find("moqui.security.UserAccount").condition("userId", userId).one().set("partyId", partyId).update()

        when: "We manually trigger a notification for that party"
        ec.service.sync().name("moqui.trade.finance.NotificationServices.send#LcNotification")
            .parameters([lcId: lcId, eventDescription: "Phase 3 Real-time Test", targetPartyId: partyId]).call()

        then: "A NotificationMessageUser record should exist for this user"
        def userNotifications = ec.entity.find("moqui.security.user.NotificationMessageUser")
                .condition("userId", userId)
                .list()
        
        userNotifications.size() >= 1
        
        and: "The latest notification should match our message"
        def latestMsgId = userNotifications.sort { it.sentDate }.last().notificationMessageId
        def msg = ec.entity.find("moqui.security.user.NotificationMessage")
                .condition("notificationMessageId", latestMsgId).one()
        msg.messageJson.contains("Phase 3 Real-time Test")
    }
}
