
package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import java.sql.Timestamp

@Stepwise
class TradeFinancePhase2Spec extends Specification {
    @Shared protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeFinancePhase2Spec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = "DEMO_LC_05" // Draft LC for testing
    @Shared String amendmentSeqId
    @Shared String drawingSeqId

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
        // Setup predictable IDs for Phase 2 entities
        ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LcAmendment", 960000, 10)
        ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LcDrawing", 960000, 10)
        ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LcCharge", 960000, 10)
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() { ec.artifactExecution.disableAuthz() }
    def cleanup() { ec.artifactExecution.enableAuthz() }

    // Amendment Service Tests
    def "create LC amendment"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.AmendmentServices.create#LcAmendment")
                .parameters([lcId: lcId, fieldName: "amount", newValue: "400000", remarks: "Test Amendment"]).call()
        amendmentSeqId = result.amendmentSeqId
        
        then:
        amendmentSeqId != null
        def amd = ec.entity.find("moqui.trade.finance.LcAmendment").condition("lcId", lcId).condition("amendmentSeqId", amendmentSeqId).one()
        amd.amendmentStatusId == "LcTxDraft"
    }

    // Drawing Service Tests
    def "create LC drawing"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.DrawingServices.create#LcDrawing")
                .parameters([lcId: "DEMO_LC_07", drawingAmount: 50000, presentationDate: ec.user.nowTimestamp]).call()
        drawingSeqId = result.drawingSeqId
        logger.info("Drawing creation result: ${result}")
        
        then:
        logger.info("Verifying Drawing with lcId: DEMO_LC_07 and drawingSeqId: ${drawingSeqId}. Messages: ${ec.message.messagesString}, Errors: ${ec.message.errorsString}")
        def drw = ec.entity.find("moqui.trade.finance.LcDrawing").condition("lcId", "DEMO_LC_07").condition("drawingSeqId", drawingSeqId).one()
        if (drw == null) {
            def allDrawings = ec.entity.find("moqui.trade.finance.LcDrawing").condition("lcId", "DEMO_LC_07").list()
            logger.error("Failed to find LcDrawing. All drawings for DEMO_LC_07: ${allDrawings}")
        }
        drw != null
        drw.drawingStatusId == "LcDrReceived"
    }

    // Financial Service Tests
    def "calculate LC charges"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.FinancialServices.calculate#LcCharges")
                .parameters([lcId: "DEMO_LC_07"]).call()
        
        then:
        !ec.message.hasError()
    }

    def "hold and release provision"() {
        when:
        ec.service.sync().name("moqui.trade.finance.FinancialServices.hold#LcProvision")
                .parameters([lcId: "DEMO_LC_07", provisionAmount: 5000, provisionRate: 1]).call()
        def prov = ec.entity.find("moqui.trade.finance.LcProvision").condition("lcId", "DEMO_LC_07").one()
        
        then:
        prov.provisionStatusId == "LcPrvActive"

        when:
        ec.service.sync().name("moqui.trade.finance.FinancialServices.release#LcProvision")
                .parameters([lcId: "DEMO_LC_07"]).call()
        prov.refresh()
        
        then:
        prov.provisionStatusId == "LcPrvReleased"
    }

    // Document Service Tests
    def "attach LC document"() {
        when:
        // Mock a file attachment (empty for test purpose if possible)
        def result = ec.service.sync().name("moqui.trade.finance.DocumentServices.attach#LcDocument")
                .parameters([lcId: "DEMO_LC_07", documentTypeEnumId: "LC_DOC_COMM_INVOICE", description: "Test Doc"]).call()
        
        then:
        !ec.message.hasError()
    }
}
