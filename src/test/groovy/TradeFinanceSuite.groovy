package moqui.trade.finance

import org.junit.jupiter.api.AfterAll
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite
import org.moqui.Moqui

@Suite
@SelectClasses([
        TradeFinanceServicesSpec.class,
        TradeFinancePhase2Spec.class,
        TradeFinancePhase3Spec.class,
        TradeFinancePhase4Spec.class,
        TradeFinanceDrawingFlowSpec.class,
        TradeFinanceCbsSpec.class,
        TradeFinanceScreensSpec.class
])
class TradeFinanceSuite {
    @AfterAll
    static void destroyMoqui() {
        Moqui.destroyActiveExecutionContextFactory()
    }
}
