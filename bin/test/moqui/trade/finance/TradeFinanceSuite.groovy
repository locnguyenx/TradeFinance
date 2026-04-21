package moqui.trade.finance

import org.junit.jupiter.api.AfterAll
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite
import org.moqui.Moqui

@Suite
@SelectClasses([
        TradeFinanceApplicationSpec.class,
        TradeFinanceServicesSpec.class,
        TradeFinanceIssuanceSpec.class,
        TradeFinanceWorkflowSpec.class,
        TradeFinanceNotificationSpec.class,
        TradeFinanceProvisionChargeSpec.class
])
class TradeFinanceSuite {
    @AfterAll
    static void destroyMoqui() {
        Moqui.destroyActiveExecutionContextFactory()
    }
}
