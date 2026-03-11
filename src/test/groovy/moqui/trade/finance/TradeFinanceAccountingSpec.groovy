package moqui.trade.finance

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.entity.EntityValue
import org.moqui.entity.EntityList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import java.sql.Timestamp

@Stepwise
class TradeFinanceAccountingSpec extends Specification {
    @Shared protected final static Logger logger = LoggerFactory.getLogger(TradeFinanceAccountingSpec.class)
    @Shared ExecutionContext ec
    @Shared String lcId = null
    @Shared String invoiceId = null

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.artifactExecution.disableAuthz()
        ec.user.loginUser("tf-admin", "moqui")
        
        // Find current max numeric IDs to avoid collisions in persistent H2
        def numericLcs = ec.entity.find("moqui.trade.finance.LetterOfCredit").list().findAll { it.lcId.isNumber() }.collect { it.lcId.toLong() }
        def maxLc = numericLcs ? (long) numericLcs.max() : 0L
        
        def numericReqs = ec.entity.find("mantle.request.Request").list().findAll { it.requestId.isNumber() }.collect { it.requestId.toLong() }
        def maxReq = numericReqs ? (long) numericReqs.max() : 0L
        
        def numericInvs = ec.entity.find("mantle.account.invoice.Invoice").list().findAll { it.invoiceId.isNumber() }.collect { it.invoiceId.toLong() }
        def maxInv = numericInvs ? (long) numericInvs.max() : 0L
        
        def startId = [maxLc, maxReq, maxInv].max() + 200000L
        logger.info("Setting sequencer startId to ${startId} (Max LC: ${maxLc}, Max Req: ${maxReq}, Max Inv: ${maxInv})")

        boolean begun = ec.transaction.begin(null)
        try {
            ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LetterOfCredit", startId, 100)
            ec.entity.tempSetSequencedIdPrimary("moqui.trade.finance.LcHistory", startId, 100)
            ec.entity.tempSetSequencedIdPrimary("mantle.request.Request", startId, 100)
            ec.entity.tempSetSequencedIdPrimary("mantle.account.invoice.Invoice", startId, 100)

            // Ensure Accounting Configuration for Bank
            String bankId = "DEMO_ORG_VIETCOMBANK"
            
            // 1. Role
            if (!ec.entity.find("mantle.party.PartyRole").condition([partyId: bankId, roleTypeId: "InternalOrganization"]).one()) {
                ec.entity.makeValue("mantle.party.PartyRole").setAll([partyId: bankId, roleTypeId: "InternalOrganization"]).store()
            }

            // 2. Journal
            String journalId = "TF_CHARGES"
            if (!ec.entity.find("mantle.ledger.config.GlJournal").condition("glJournalId", journalId).one()) {
                ec.entity.makeValue("mantle.ledger.config.GlJournal").setAll([glJournalId: journalId, organizationPartyId: bankId, glJournalName: "TF Charges Journal", isPosted: "Y"]).store()
            }

            // 3. Preference
            if (!ec.entity.find("mantle.ledger.config.PartyAcctgPreference").condition("organizationPartyId", bankId).one()) {
                ec.entity.makeValue("mantle.ledger.config.PartyAcctgPreference").setAll([
                    organizationPartyId: bankId, baseCurrencyUomId: "USD", 
                    fiscalYearStartMonth: 1, fiscalYearStartDay: 1, errorGlJournalId: journalId
                ]).store()
            }

            // 4. GL Account and ItemType Mappings
            if (!ec.entity.find("mantle.ledger.account.GlAccount").condition([orgPartyId: bankId, glAccountId: "401000000"]).one()) {
                ec.entity.makeValue("mantle.ledger.account.GlAccount").setAll([
                    orgPartyId: bankId, glAccountId: "401000000", accountCode: "401000000", 
                    accountName: "Service Revenue", glAccountTypeEnumId: "GatRevenue"
                ]).store()
            }
            
            Timestamp now = new Timestamp(System.currentTimeMillis())
            if (!ec.entity.find("mantle.ledger.config.GlAccountCategoryMember").condition([glAccountId: "401000000", enumId: "ItemCommission"]).one()) {
                ec.entity.makeValue("mantle.ledger.config.GlAccountCategoryMember").setAll([
                    glAccountId: "401000000", glAccountCategoryTypeId: "ItemType", enumId: "ItemCommission", fromDate: now
                ]).store()
            }
            if (!ec.entity.find("mantle.ledger.config.GlAccountCategoryMember").condition([glAccountId: "401000000", enumId: "ItemMiscCharge"]).one()) {
                ec.entity.makeValue("mantle.ledger.config.GlAccountCategoryMember").setAll([
                    glAccountId: "401000000", glAccountCategoryTypeId: "ItemType", enumId: "ItemMiscCharge", fromDate: now
                ]).store()
            }

            // 5. LC Product Charges
            ec.entity.makeValue("moqui.trade.finance.LcProductCharge")
                    .setAll([productId: "PROD_ILC_SIGHT", chargeTypeEnumId: "LC_CHG_ISSUANCE", 
                            itemTypeEnumId: "ItemCommission", defaultAmount: 100, defaultCurrencyUomId: "USD"]).store()
            ec.entity.makeValue("moqui.trade.finance.LcProductCharge")
                    .setAll([productId: "PROD_ILC_SIGHT", chargeTypeEnumId: "LC_CHG_SWIFT", 
                            itemTypeEnumId: "ItemMiscCharge", defaultAmount: 25, defaultCurrencyUomId: "USD"]).store()
        } finally {
            ec.transaction.commit(begun)
        }
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

    def "create new LC for accounting test"() {
        when:
        String lcNum = "AT" + (System.currentTimeMillis() % 1000000000L)
        Map result = ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.create#LetterOfCredit")
                .parameters([lcNumber: lcNum, productId: "PROD_ILC_SIGHT",
                             applicantPartyId: "DEMO_ORG_ABC", beneficiaryPartyId: "DEMO_ORG_XYZ",
                             issuingBankPartyId: "DEMO_ORG_VIETCOMBANK", advisingBankPartyId: "DEMO_ORG_DBS",
                             applicantName: "Accounting Test Applicant", beneficiaryName: "Accounting Test Beneficiary",
                             amount: 50000.00, amountCurrencyUomId: "USD",
                             expiryDate: ec.user.nowTimestamp + 30]).call()
        lcId = result.lcId

        then:
        lcId != null
        !ec.message.hasError()
    }

    def "submit and approve the test LC"() {
        when:
        // Submit
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.submit#LetterOfCredit")
                .parameters([lcId: lcId]).call()
        // Approve
        ec.service.sync().name("moqui.trade.finance.TradeFinanceServices.transition#TransactionStatus")
                .parameters([lcId: lcId, toStatusId: 'LcTxApproved']).call()

        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()

        then:
        !ec.message.hasError()
        lc.transactionStatusId == 'LcTxApproved'
        lc.lcStatusId == 'LcLfApplied'
    }

    def "issue LC and verify charge posting"() {
        when:
        ec.message.clearErrors()
        // Issue LC - this should trigger post#LcChargesToInvoice
        ec.service.sync().name("moqui.trade.finance.LifecycleServices.issue#LetterOfCredit")
                .parameters([lcId: lcId]).call()

        // Check charges before posting
        List<EntityValue> charges = ec.entity.find("moqui.trade.finance.LcCharge").condition("lcId", lcId).list()
        EntityValue lc = ec.entity.find("moqui.trade.finance.LetterOfCredit").condition("lcId", lcId).one()
        logger.info("LC ${lcId} has product ${lc.productId} and ${charges.size()} charges: ${charges}")

        // Find the generated invoice
        EntityValue charge = ec.entity.find("moqui.trade.finance.LcCharge")
                .condition("lcId", lcId).condition("invoiceId", "not-equal", null).list().first()
        invoiceId = charge?.invoiceId

        then:
        !ec.message.hasError()
        invoiceId != null
        
        when:
        // Verify Invoice Data
        EntityValue invoice = ec.entity.find("mantle.account.invoice.Invoice").condition("invoiceId", invoiceId).one()
        logger.info("Retrieved invoice ${invoiceId} with status: ${invoice.statusId}")
        List<EntityValue> items = ec.entity.find("mantle.account.invoice.InvoiceItem").condition("invoiceId", invoiceId).list()

        then:
        invoice.statusId == 'InvoiceFinalized'
        items.size() > 0
        items.any { ['ItemCommission', 'ItemMiscCharge'].contains(it.itemTypeEnumId) }

        when:
        // Verify GL Posting (AcctgTrans)
        List<EntityValue> transList = ec.entity.find("mantle.ledger.transaction.AcctgTrans")
                .condition("invoiceId", invoiceId).list()
        
        if (transList.size() == 0) {
            logger.info("NO ACCTG TRANS FOUND for invoice ${invoiceId}. Auditing Configuration...")
            EntityValue pref = ec.entity.find("mantle.ledger.config.PartyAcctgPreference").condition("organizationPartyId", "DEMO_ORG_VIETCOMBANK").one()
            logger.info("Acctg Preference Found: ${pref}")
            EntityList bankRoles = ec.entity.find("mantle.party.PartyRole").condition("partyId", "DEMO_ORG_VIETCOMBANK").list()
            logger.info("Bank Roles Found: ${bankRoles}")
            EntityList mappings = ec.entity.find("mantle.ledger.config.GlAccountCategoryMember").condition("glAccountCategoryTypeId", "ItemType").list()
            logger.info("ItemType GL Mappings (all): ${mappings}")
            EntityList glOrgs = ec.entity.find("mantle.ledger.account.GlAccount").condition("orgPartyId", "DEMO_ORG_VIETCOMBANK").list()
            logger.info("GL Accounts for Bank: ${glOrgs}")
        }

        then:
        transList.size() > 0
        transList.every { it.isPosted == 'Y' }
    }
}
