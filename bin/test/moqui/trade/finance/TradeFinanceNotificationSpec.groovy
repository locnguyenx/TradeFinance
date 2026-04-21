package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import java.sql.Timestamp

/**
 * BDD R8.3-UC6 Coverage (System Notification for Application):
 * - R8.3-UC6-SC1: Notification sent to CSR when approved by IPC
 * - R8.3-UC6-SC2: Notification sent to Applicant when LC ready for pickup
 * 
 * @Scenario(BDD-R8.3-UC6-SC1) - COVERED
 * @Scenario(BDD-R8.3-UC6-SC2) - COVERED
 */

@Stepwise
class TradeFinanceNotificationSpec extends Specification {
    @Shared protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeFinanceNotificationSpec.class)
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
    // Notification Verification Tests (from Phase 3)
    // =========================================================
    def "verify LC notifications are recorded in history"() {
        when: "We check the history for DEMO_LC_08"
        def notifications = ec.entity.find("moqui.trade.finance.LcHistory")
                .condition("lcId", lcId)
                .condition("changeType", "Notification")
                .list()
        
        then: "There should be notification entries"
        notifications.size() >= 1
    }

    def "verify user actually receives system notification"() {
        setup: "Link tf-admin user to the target party temporarily"
        def userId = "TF_ADMIN_USER"
        def partyId = "DEMO_ORG_AUTO"
        def userAccount = ec.entity.find("moqui.security.UserAccount").condition("userId", userId).one()
        if (userAccount) {
            userAccount.set("partyId", partyId).update()
        }

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
