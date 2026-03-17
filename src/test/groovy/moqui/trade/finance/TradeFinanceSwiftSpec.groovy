package moqui.trade.finance

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Stepwise
import org.moqui.Moqui
import org.moqui.context.ExecutionContext

@Stepwise
class TradeFinanceSwiftSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceSwiftSpec.class)
    @Shared ExecutionContext ec

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    def "Verify MT700 Generation and Parsing"() {
        given:
        String lcId = "DEMO_LC_01" // Sight LC from demo data
        
        when: "Generate MT700"
        Map genOut = ec.service.sync().name("moqui.trade.finance.SwiftServices.generate#SwiftMt700")
            .parameter("lcId", lcId).call()
        String swiftText = genOut.swiftMessageText

        then: "MT700 should contain expected tags from Demo LC 01"
        swiftText != null
        swiftText.contains(":20:ILC-2026-0001")
        swiftText.contains(":32B:USD500000,00") // Actual value in demo data

        when: "Parse the generated MT700"
        Map parseOut = ec.service.sync().name("moqui.trade.finance.SwiftServices.parse#SwiftMt700")
            .parameter("swiftTextCode", swiftText).call()
        Map fieldMap = parseOut.fieldMap

        then: "Parsed fields should match original record"
        fieldMap != null
        fieldMap.lcNumber == "ILC-2026-0001"
        fieldMap.amount == "USD500000,00"
    }

    def "Verify MT707 Generation and Parsing"() {
        given:
        String lcId = "DEMO_LC_03"
        String amendmentSeqId = "01" // Seq ID in demo data for LC 03

        when: "Generate MT707"
        Map genOut = ec.service.sync().name("moqui.trade.finance.SwiftServices.generate#SwiftMt707")
            .parameters([lcId: lcId, amendmentSeqId: amendmentSeqId]).call()
        String swiftText = genOut.swiftMessageText

        then: "MT707 should contain expected tags"
        swiftText != null
        swiftText.contains(":20:ILC-2026-0003")
        swiftText.contains(":26E:1")

        when: "Parse the generated MT707"
        Map parseOut = ec.service.sync().name("moqui.trade.finance.SwiftServices.parse#SwiftMt707")
            .parameter("swiftTextCode", swiftText).call()
        Map fieldMap = parseOut.fieldMap

        then: "Parsed fields should match"
        fieldMap != null
        fieldMap.lcNumber == "ILC-2026-0003"
        fieldMap.amendmentNumber == "1"
    }
    def "generate SWIFT MT700 for an LC manually"() {
        when: "We invoke the MT700 generator service"
        def result = ec.service.sync().name("moqui.trade.finance.SwiftServices.generate#SwiftMt700")
                .parameters([lcId: "DEMO_LC_08"]).call()
        
        then: "The raw text look like an MT700 message"
        result.swiftMessageText != null
        result.swiftMessageText.contains("{1:F01BANKXXXXAXXX0000000000}")
        result.swiftMessageText.contains(":20:") 
    }
}
