package moqui.trade.finance

import spock.lang.Specification
import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TradeFinanceCbsSpec extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceCbsSpec.class)
    protected ExecutionContext ec

    def setup() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
        // Set to Simulator
        System.setProperty("cbs.integration.impl", "Simulator")
    }

    def cleanup() {
        System.clearProperty("cbs.integration.impl")
        ec.destroy()
    }

    def "Simple Entity Test"() {
        when:
        println "Testing CbsSimulatorState access..."
        def state = ec.entity.find("moqui.trade.finance.CbsSimulatorState").one()
        println "State found: ${state}"
        
        then:
        true
    }

    def "Verify CBS Simulator State Retention"() {
        when:
        String partyId = "DEMO_ORG_ABC"
        BigDecimal holdAmount = 50000.00
        
        println "1. Checking Initial Balance for ${partyId}"
        def limitOut = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.check#CreditLimit")
                .parameter("partyId", partyId).call()
        BigDecimal initialLimit = limitOut.availableLimit
        println "Initial limit: ${initialLimit}"

        println "2. Performing a Hold of ${holdAmount}"
        def holdOut = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.hold#Funds")
                .parameters([partyId: partyId, amount: holdAmount, currencyUomId: "USD", referenceId: "TEST-001"])
                .call()
        println "Hold result success: ${holdOut.success}, message: ${holdOut.message}"
        
        println "3. Checking Limit Again"
        def limitAfterOut = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.check#CreditLimit")
                .parameter("partyId", partyId).call()
        println "Limit after hold: ${limitAfterOut.availableLimit}"

        then:
        holdOut.success == true
        limitAfterOut.availableLimit == initialLimit - holdAmount
    }
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
}
