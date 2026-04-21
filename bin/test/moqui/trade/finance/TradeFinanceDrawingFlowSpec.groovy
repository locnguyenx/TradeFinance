package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import spock.lang.Specification

class TradeFinanceDrawingFlowSpec extends Specification {
    protected final static ExecutionContext ec = Moqui.getExecutionContext()

    def setupSpec() {
        // Prepare data for the test
        ec.user.loginUser("tf-admin", "moqui")
        ec.artifactExecution.disableAuthz()
    }

    def cleanupSpec() {
        ec.artifactExecution.enableAuthz()
        ec.destroy()
    }

    def "Verify End-to-End Drawing Lifecycle"() {
        setup:
        String lcId = "DEMO_LC_03" // An existing amended LC
        BigDecimal drawingAmount = 100000.00
        
        when:
        println "1. Registering New Drawing for LC ${lcId}"
        def createOut = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: lcId, drawingAmount: drawingAmount, presentationDate: ec.user.nowTimestamp])
                .call()
        String drawingSeqId = createOut.drawingSeqId
        
        then:
        drawingSeqId != null
        
        when:
        println "2. Performing Automated Examination (should find amount discrepancy)"
        // Note: DEMO_LC_001 has amount 250,000. If I want a discrepancy, I need to check the exact amount.
        // Actually, in the service, it checks drawingAmount > lc.amount.
        // Let's force it by creating a drawing larger than LC amount.
        def lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        BigDecimal largeAmount = lc.amount + 10000.00
        
        ec.service.sync().name("update#moqui.trade.finance.LcDrawing")
                .parameters([lcId: lcId, drawingSeqId: drawingSeqId, drawingAmount: largeAmount])
                .call()

        ec.service.sync().name("moqui.trade.finance.DrawingServices.examine#LcDrawing")
                .parameters([lcId: lcId, drawingSeqId: drawingSeqId])
                .call()
        
        def drawing = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition([lcId: lcId, drawingSeqId: drawingSeqId]).one()
        
        then:
        drawing.drawingStatusId == "LcDrDiscrepant"
        
        when:
        println "3. Verifying MT734 Generation"
        def mt734 = ec.entity.find("moqui.trade.finance.LcDocument")
                .condition([lcId: lcId, documentTypeEnumId: "LC_DOC_SWIFT_MSG", documentReference: "MT734_" + drawingSeqId])
                .one()
        
        then:
        mt734 != null
        
        when:
        println "4. Resolving Discrepancy (Accepted by Applicant)"
        def disc = ec.entity.find("moqui.trade.finance.LcDiscrepancy")
                .condition([lcId: lcId, drawingSeqId: drawingSeqId]).one()
        
        ec.service.sync().name("moqui.trade.finance.DrawingServices.resolve#LcDiscrepancy")
                .parameters([lcId: lcId, drawingSeqId: drawingSeqId, discrepancySeqId: disc.discrepancySeqId, resolutionEnumId: "LC_DISRES_ACCEPTED"])
                .call()

        def finalDrawing = ec.entity.find("moqui.trade.finance.LcDrawing")
                .condition([lcId: lcId, drawingSeqId: drawingSeqId]).one()
        
        then:
        finalDrawing.drawingStatusId == "LcDrAccepted"
    }
    def "Verify basic drawing creation"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: "DEMO_LC_07", drawingAmount: 50000, presentationDate: ec.user.nowTimestamp]).call()
        String drawingSeqId = result.drawingSeqId
        
        then:
        drawingSeqId != null
        def drw = ec.entity.find("moqui.trade.finance.LcDrawing").condition("lcId", "DEMO_LC_07").condition("drawingSeqId", drawingSeqId).one()
        drw != null
        drw.drawingStatusId == "LcDrReceived"
    }
    def "calculate charges on drawing registration (BR3)"() {
        when: "Register a new Drawing for an LC"
        // Ensure DEMO_LC_07 is in Issued status
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.update#LetterOfCredit")
                .parameters([lcId: "DEMO_LC_07", lcStatusId: "LcLfIssued"]).call()

        def result = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: "DEMO_LC_07", drawingAmount: 1000, presentationDate: ec.user.nowTimestamp]).call()
        
        then: "LC Charges should be automatically created for this drawing"
        result != null
        String drawingSeqId = result.drawingSeqId
        drawingSeqId != null

        List<EntityValue> charges = ec.entity.find("moqui.trade.finance.LcCharge")
                .condition("lcId", "DEMO_LC_07").condition("drawingSeqId", drawingSeqId).list()
        
        // This is expected to FAIL in the RED phase
        charges.size() > 0
    }
}
