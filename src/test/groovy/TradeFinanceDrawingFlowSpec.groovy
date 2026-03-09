/*
 * TradeFinanceDrawingFlowSpec.groovy
 *
 * Spock tests for the drawing examination workflow:
 *   - examine#LcDrawing (Compliant vs Discrepant)
 *   - record#LcDiscrepancy
 *   - resolve#LcDiscrepancy (Waive, Accept, Reject)
 *   - transition#DrawingStatus
 *   - generate#SwiftMt734 (Refusal advice)
 *
 * Uses DEMO_LC_07 (Issued/Standby) as the base LC for creating test drawings.
 */

package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class TradeFinanceDrawingFlowSpec extends Specification {
    @Shared protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeFinanceDrawingFlowSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = "DEMO_LC_07" // Issued Standby LC (amount=3,000,000)
    @Shared String compliantDrawingSeqId
    @Shared String discrepantDrawingSeqId
    @Shared String discrepancySeqId
    @Shared String rejectDrawingSeqId

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
        ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LcDrawing", 970000, 10)
        ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LcDiscrepancy", 970000, 10)
        ec.entity.tempSetSequencedIdPrimary("mantle.request.Request", 970000, 10)
    }

    def cleanupSpec() {
        ec.entity.tempResetSequencedIdPrimary("moqui.trade.finance.LcDrawing")
        ec.entity.tempResetSequencedIdPrimary("moqui.trade.finance.LcDiscrepancy")
        ec.entity.tempResetSequencedIdPrimary("mantle.request.Request")
        ec.destroy()
    }

    def setup() { ec.artifactExecution.disableAuthz() }
    def cleanup() { ec.artifactExecution.enableAuthz() }

    // =========================================================
    // 1. Compliant Drawing Flow: Received → Compliant → Paid
    // =========================================================

    def "create a compliant drawing (amount within LC limit)"() {
        when:
        ec.message.clearErrors()
        def result = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: lcId, drawingAmount: 500000, presentationDate: ec.user.nowTimestamp,
                             remarks: "Compliant drawing test"]).call()
        compliantDrawingSeqId = result.drawingSeqId

        then:
        compliantDrawingSeqId != null
        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", compliantDrawingSeqId).one()
        drw.drawingStatusId == "LcDrReceived"
    }

    def "examine compliant drawing transitions to Compliant"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.DrawingServices.examine#LcDrawing")
                .parameters([lcId: lcId, drawingSeqId: compliantDrawingSeqId]).call()

        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", compliantDrawingSeqId).one()

        then:
        !ec.message.hasError()
        drw.drawingStatusId == "LcDrCompliant"
    }

    def "transition compliant drawing to Paid"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.DrawingServices.transition#DrawingStatus")
                .parameters([lcId: lcId, drawingSeqId: compliantDrawingSeqId, toStatusId: "LcDrPaid"]).call()

        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", compliantDrawingSeqId).one()

        then:
        !ec.message.hasError()
        drw.drawingStatusId == "LcDrPaid"
    }

    // =========================================================
    // 2. Discrepant Drawing Flow: Received → Discrepant → Waive → Accepted → Paid
    // =========================================================

    def "create a discrepant drawing (amount exceeds LC limit)"() {
        when:
        ec.message.clearErrors()
        def result = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: lcId, drawingAmount: 5000000, presentationDate: ec.user.nowTimestamp,
                             remarks: "Discrepant drawing - exceeds LC amount"]).call()
        discrepantDrawingSeqId = result.drawingSeqId

        then:
        discrepantDrawingSeqId != null
    }

    def "examine discrepant drawing transitions to Discrepant with auto-recorded discrepancy"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.DrawingServices.examine#LcDrawing")
                .parameters([lcId: lcId, drawingSeqId: discrepantDrawingSeqId]).call()

        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId).one()

        def discrepancies = ec.entity.find("moqui.trade.finance.LcDiscrepancy")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId).list()

        then:
        !ec.message.hasError()
        drw.drawingStatusId == "LcDrDiscrepant"
        discrepancies.size() >= 1
        discrepancies.any { it.discrepancyTypeEnumId == "LC_DISC_AMOUNT" }
    }

    def "record an additional discrepancy manually"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.DrawingServices.record#LcDiscrepancy")
                .parameters([lcId: lcId, drawingSeqId: discrepantDrawingSeqId,
                             discrepancyTypeEnumId: "LC_DISC_LATE_PRES",
                             description: "Documents presented after 21-day window"]).call()

        def discrepancies = ec.entity.find("moqui.trade.finance.LcDiscrepancy")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId)
                .orderBy("discrepancySeqId").list()

        then:
        !ec.message.hasError()
        discrepancies.size() >= 2
        discrepancies.any { it.discrepancyTypeEnumId == "LC_DISC_LATE_PRES" }

        cleanup:
        // Save for later resolution tests
        discrepancySeqId = discrepancies.first().discrepancySeqId
    }

    def "resolve first discrepancy as Waived (does NOT transition drawing yet)"() {
        when:
        ec.message.clearErrors()
        // Get all discrepancies to resolve them one by one
        def discrepancies = ec.entity.find("moqui.trade.finance.LcDiscrepancy")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId)
                .orderBy("discrepancySeqId").list()
        def firstSeqId = discrepancies[0].discrepancySeqId

        ec.service.sync().name("moqui.trade.finance.DrawingServices.resolve#LcDiscrepancy")
                .parameters([lcId: lcId, drawingSeqId: discrepantDrawingSeqId,
                             discrepancySeqId: firstSeqId,
                             resolutionEnumId: "LC_DISRES_WAIVED"]).call()

        // Drawing should still be Discrepant because there's a second unresolved discrepancy
        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId).one()

        def resolved = ec.entity.find("moqui.trade.finance.LcDiscrepancy")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId)
                .condition("discrepancySeqId", firstSeqId).one()

        then:
        !ec.message.hasError()
        resolved.resolutionEnumId == "LC_DISRES_WAIVED"
        resolved.resolvedByUserId != null
        drw.drawingStatusId == "LcDrDiscrepant" // Still discrepant — not all resolved
    }

    def "resolve second discrepancy as Accepted (transitions drawing to Accepted)"() {
        when:
        ec.message.clearErrors()
        // Find the remaining unresolved discrepancy
        def pending = ec.entity.find("moqui.trade.finance.LcDiscrepancy")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId)
                .condition("resolutionEnumId", null).list()
        def secondSeqId = pending[0].discrepancySeqId

        ec.service.sync().name("moqui.trade.finance.DrawingServices.resolve#LcDiscrepancy")
                .parameters([lcId: lcId, drawingSeqId: discrepantDrawingSeqId,
                             discrepancySeqId: secondSeqId,
                             resolutionEnumId: "LC_DISRES_ACCEPTED"]).call()

        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId).one()

        then:
        !ec.message.hasError()
        drw.drawingStatusId == "LcDrAccepted" // All resolved with Accept/Waive → Accepted
    }

    def "transition accepted drawing to Paid"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.DrawingServices.transition#DrawingStatus")
                .parameters([lcId: lcId, drawingSeqId: discrepantDrawingSeqId, toStatusId: "LcDrPaid"]).call()

        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", discrepantDrawingSeqId).one()

        then:
        !ec.message.hasError()
        drw.drawingStatusId == "LcDrPaid"
    }

    // =========================================================
    // 3. Rejected Drawing Flow: Received → Discrepant → Reject → MT734
    // =========================================================

    def "create drawing for rejection test"() {
        when:
        ec.message.clearErrors()
        def result = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: lcId, drawingAmount: 9000000, presentationDate: ec.user.nowTimestamp,
                             remarks: "Drawing for rejection and MT734 test"]).call()
        rejectDrawingSeqId = result.drawingSeqId

        then:
        rejectDrawingSeqId != null
    }

    def "examine and reject drawing generates discrepancy"() {
        when:
        ec.message.clearErrors()
        // Examine will auto-flag as discrepant (amount > LC amount)
        ec.service.sync().name("moqui.trade.finance.DrawingServices.examine#LcDrawing")
                .parameters([lcId: lcId, drawingSeqId: rejectDrawingSeqId]).call()

        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", rejectDrawingSeqId).one()

        then:
        drw.drawingStatusId == "LcDrDiscrepant"
    }


    def "resolve discrepancy as Rejected transitions drawing to Rejected"() {
        when:
        ec.message.clearErrors()
        def disc = ec.entity.find("moqui.trade.finance.LcDiscrepancy")
                .condition("lcId", lcId).condition("drawingSeqId", rejectDrawingSeqId).one()

        ec.service.sync().name("moqui.trade.finance.DrawingServices.resolve#LcDiscrepancy")
                .parameters([lcId: lcId, drawingSeqId: rejectDrawingSeqId,
                             discrepancySeqId: disc.discrepancySeqId,
                             resolutionEnumId: "LC_DISRES_REJECTED"]).call()

        def drw = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition("lcId", lcId).condition("drawingSeqId", rejectDrawingSeqId).one()

        then:
        assert !ec.message.hasError() : ec.message.getErrorsString()
        drw.drawingStatusId == "LcDrRejected"
    }

    def "generate SWIFT MT734 for rejected drawing"() {
        when:
        ec.message.clearErrors()
        def result = ec.service.sync().name("moqui.trade.finance.SwiftServices.generate#SwiftMt734")
                .parameters([lcId: lcId, drawingSeqId: rejectDrawingSeqId]).call()

        then:
        !ec.message.hasError()
        result.swiftMessageText != null
        result.swiftMessageText.contains(":20:") // Contains LC Number tag
    }

    // =========================================================
    // 4. Invalid Transition Tests
    // =========================================================

    def "invalid drawing status transition is rejected (Received to Paid)"() {
        setup:
        ec.message.clearErrors()
        // Create a fresh drawing in Received status
        def result = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: lcId, drawingAmount: 100000, presentationDate: ec.user.nowTimestamp]).call()
        def freshSeqId = result.drawingSeqId

        when:
        ec.service.sync().name("moqui.trade.finance.DrawingServices.transition#DrawingStatus")
                .parameters([lcId: lcId, drawingSeqId: freshSeqId, toStatusId: "LcDrPaid"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }

    def "examine fails for non-existent drawing"() {
        when:
        ec.message.clearErrors()
        ec.service.sync().name("moqui.trade.finance.DrawingServices.examine#LcDrawing")
                .parameters([lcId: lcId, drawingSeqId: "NONEXISTENT"]).call()

        then:
        ec.message.hasError()

        cleanup:
        ec.message.clearErrors()
    }
}
