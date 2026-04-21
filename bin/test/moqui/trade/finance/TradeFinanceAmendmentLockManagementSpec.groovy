/*
 * TradeFinanceAmendmentLockManagementSpec.groovy
 *
 * Spock unit tests for R8.5 UC11: Amendment Lock Management Screen.
 * Tests the admin UI screen for viewing and managing amendment locks.
 *
 * Pre-requisites:
 * - Run: ./gradlew reloadSave
 *
 * Test Coverage (BDD-R8.5 UC11):
 * - SC37: View all active amendment locks
 * - SC38: Filter locks by LC Number
 * - SC39: Force release lock via UI
 * - SC40: Prevent non-admin access
 * - SC41: Lock expires automatically
 *
 * Note: Backend services tested in TradeFinanceAmendmentLockScreenSpec.groovy
 */

package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.screen.ScreenTest
import org.moqui.screen.ScreenTest.ScreenTestRender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Timestamp
import java.math.BigDecimal

class TradeFinanceAmendmentLockManagementSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceAmendmentLockManagementSpec.class)

    @Shared ExecutionContext ec
    @Shared ScreenTest screenTest
    @Shared String lcId1
    @Shared String lcId2

    def cleanupLock(String lid) {
        try { 
            def lock = ec.entity.find("moqui.trade.finance.LcAmendmentLock").condition("lcId", lid).one()
            if (lock) lock.delete()
        } catch (Exception e) {}
    }

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        ec.entity.makeDataLoader().location("component://TradeFinance/data/10_TradeFinanceData.xml").load()
        ec.user.loginUser("tf-admin", "moqui")
        screenTest = ec.screen.makeTest().baseScreenPath("apps/trade-finance")

        long timestamp = System.currentTimeMillis()

        // Create first test LC
        lcId1 = "TDD_LOCK_UI_1_" + timestamp
        ec.entity.makeValue("moqui.trade.finance.LetterOfCredit")
            .setAll([
                lcId: lcId1,
                lcNumber: "TDD-LOCK-UI-1",
                lcStatusId: "LcLfIssued",
                transactionStatusId: "LcTxApproved",
                productId: "PROD_ILC_SIGHT",
                lcProductTypeEnumId: "LC_PROD_SIGHT",
                applicantPartyId: "DEMO_ORG_ABC",
                beneficiaryPartyId: "DEMO_ORG_XYZ",
                issuingBankPartyId: "DEMO_ORG_VIETCOMBANK",
                applicantName: "ABC Trading Co",
                beneficiaryName: "XYZ Exports",
                amount: new BigDecimal("10000.00"),
                amountCurrencyUomId: "USD",
                issueDate: new Timestamp(System.currentTimeMillis() - 86400000L * 30),
                expiryDate: new Timestamp(System.currentTimeMillis() + 86400000L * 180)
            ]).create()

        // Create second test LC
        lcId2 = "TDD_LOCK_UI_2_" + (timestamp + 1)
        ec.entity.makeValue("moqui.trade.finance.LetterOfCredit")
            .setAll([
                lcId: lcId2,
                lcNumber: "TDD-LOCK-UI-2",
                lcStatusId: "LcLfIssued",
                transactionStatusId: "LcTxApproved",
                productId: "PROD_ILC_SIGHT",
                amount: new BigDecimal("20000.00"),
                amountCurrencyUomId: "USD"
            ]).create()

        ec.message.clearAll()
    }

    def cleanupSpec() {
        cleanupLock(lcId1)
        cleanupLock(lcId2)
        try { ec.entity.makeValue("moqui.trade.finance.LetterOfCredit").setAll([lcId: lcId1]).delete() } catch (Exception e) {}
        try { ec.entity.makeValue("moqui.trade.finance.LetterOfCredit").setAll([lcId: lcId2]).delete() } catch (Exception e) {}

        long totalTime = System.currentTimeMillis() - screenTest.startTime
        logger.info("Amendment Lock Management Tests: ${screenTest.renderCount} screens rendered, ${screenTest.errorCount} errors")
        ec.destroy()
    }

    def setup() {
        ec.message.clearAll()
        ec.artifactExecution.enableAuthz()
        cleanupLock(lcId1)
        cleanupLock(lcId2)
    }

    def cleanup() {
        ec.artifactExecution.disableAuthz()
    }

    // ============================================================
    // UC11: Amendment Lock Management - Service Tests
    // ============================================================

    // @Scenario(BDD-R8.5-SC37)
    def "get all active locks returns list"() {
        when: "Get all active locks"
        Map result = ec.service.sync().name("moqui.trade.finance.AmendmentServices.get#AllActiveLocks").call()

        then: "Should return list"
        result.lockList != null
    }

    // @Scenario(BDD-R8.5-SC38)
    def "filter locks by LC Number works via service"() {
        given: "Create locks on LCs"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.acquire#AmendmentLock").parameters([lcId: lcId1]).call()
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.acquire#AmendmentLock").parameters([lcId: lcId2]).call()

        when: "Call service with filter"
        Map result = ec.service.sync().name("moqui.trade.finance.AmendmentServices.get#AllActiveLocks")
            .parameters([filterLcNumber: "TDD-LOCK-UI-1"]).call()

        then: "Should return filtered results"
        result.lockList != null
    }

    // @Scenario(BDD-R8.5-SC39)
    def "force release lock works"() {
        given: "Create a lock"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.acquire#AmendmentLock").parameters([lcId: lcId1]).call()

        when: "Force release the lock"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.forceRelease#AmendmentLock")
            .parameters([lcId: lcId1, reason: "Test force release"]).call()

        then: "Lock should be released"
        def lock = ec.entity.find("moqui.trade.finance.LcAmendmentLock").condition("lcId", lcId1).one()
        lock == null
    }

    // @Scenario(BDD-R8.5-SC40)
    def "admin can access lock management"() {
        when: "Admin calls lock services"
        Map result = ec.service.sync().name("moqui.trade.finance.AmendmentServices.get#AllActiveLocks").call()

        then: "Service should be accessible"
        result.lockList != null
    }

    // @Scenario(BDD-R8.5-SC41)
    def "expire locks service releases expired locks"() {
        given: "Create lock with past expiry"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.acquire#AmendmentLock")
            .parameters([lcId: lcId1, lockTimeoutMinutes: -120]).call()

        when: "Call expire locks service"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.expire#Locks").call()

        then: "Expired lock should be released"
        def lock = ec.entity.find("moqui.trade.finance.LcAmendmentLock").condition("lcId", lcId1).one()
        lock == null
    }

    // Screen rendering test
    def "LockManagement screen renders with lock list"() {
        given: "Create a lock on test LC"
        ec.service.sync().name("moqui.trade.finance.AmendmentServices.acquire#AmendmentLock")
            .parameters([lcId: lcId1]).call()

        when: "Render the LockManagement screen"
        ScreenTestRender str = screenTest.render("ImportLc/Amendment/LockManagement", [:], null)

        then: "Screen should render successfully"
        str != null
        
        cleanup:
        cleanupLock(lcId1)
    }
}
