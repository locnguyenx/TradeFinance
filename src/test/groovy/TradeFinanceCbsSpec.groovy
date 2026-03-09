/*
 * TradeFinanceCbsSpec.groovy
 *
 * Spock tests for CBS Integration Services (mock/stub implementations):
 *   - post#AccountingEntries
 *   - check#CreditLimit
 *   - get#ExchangeRate
 *
 * Tests for hold#Funds and release#Funds already exist in TradeFinancePhase3Spec.
 */

package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class TradeFinanceCbsSpec extends Specification {
    @Shared protected final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeFinanceCbsSpec.class)
    @Shared ExecutionContext ec

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("tf-admin", "moqui")
    }

    def cleanupSpec() { ec.destroy() }
    def setup() { ec.artifactExecution.disableAuthz() }
    def cleanup() { ec.artifactExecution.enableAuthz() }

    // =========================================================
    // 1. Accounting Entries
    // =========================================================

    def "post accounting entries returns success and transaction reference"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.post#AccountingEntries")
                .parameters([debitAccountId: "1301000", creditAccountId: "2101000",
                             amount: 500000.00, currencyUomId: "USD",
                             transactionType: "LC_ISSUANCE", referenceId: "DEMO_LC_01"]).call()

        then:
        result.success == true || result.success == "true"
        result.transactionReference != null
        result.transactionReference.startsWith("TXN-")
    }

    // =========================================================
    // 2. Credit Limit Check
    // =========================================================

    def "check credit limit returns available limit"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.check#CreditLimit")
                .parameters([partyId: "DEMO_ORG_ABC"]).call()

        then:
        result.availableLimit != null
        new BigDecimal(result.availableLimit.toString()) > 0
        result.currencyUomId == "USD"
    }

    def "check credit limit with custom limit type"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.check#CreditLimit")
                .parameters([partyId: "DEMO_ORG_ABC", limitType: "TRADE_GUARANTEE"]).call()

        then:
        result.availableLimit != null
    }

    // =========================================================
    // 3. Exchange Rate
    // =========================================================

    def "get exchange rate for same currency returns 1.0"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.get#ExchangeRate")
                .parameters([fromCurrencyUomId: "USD", toCurrencyUomId: "USD"]).call()

        then:
        new BigDecimal(result.exchangeRate.toString()) == 1.0
    }

    def "get exchange rate USD to EUR returns valid rate"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.get#ExchangeRate")
                .parameters([fromCurrencyUomId: "USD", toCurrencyUomId: "EUR"]).call()

        then:
        result.exchangeRate != null
        new BigDecimal(result.exchangeRate.toString()) == 0.92
    }

    def "get exchange rate EUR to USD returns valid rate"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.get#ExchangeRate")
                .parameters([fromCurrencyUomId: "EUR", toCurrencyUomId: "USD"]).call()

        then:
        result.exchangeRate != null
        new BigDecimal(result.exchangeRate.toString()) == 1.09
    }

    def "get exchange rate for unknown pair returns default 1.0"() {
        when:
        def result = ec.service.sync().name("moqui.trade.finance.CbsIntegrationServices.get#ExchangeRate")
                .parameters([fromCurrencyUomId: "VND", toCurrencyUomId: "JPY"]).call()

        then:
        result.exchangeRate != null
        new BigDecimal(result.exchangeRate.toString()) == 1.0
    }
}
