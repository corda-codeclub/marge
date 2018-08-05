package net.cordaclub.marge.bank

import io.cordite.dgl.corda.account.Account
import io.cordite.dgl.corda.account.AccountAddress
import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.impl.LedgerApiImpl
import io.vertx.core.Future
import net.corda.core.node.AppServiceHub
import net.corda.core.utilities.loggerFor
import net.cordaclub.marge.Initializer
import net.cordaclub.marge.Insurers
import net.cordaclub.marge.Patient
import net.cordaclub.marge.hospital.HospitalAPI
import net.cordaclub.marge.insurer.InsurerAPI
import net.cordaclub.marge.util.onFail
import net.cordaclub.marge.util.onSuccess
import net.cordaclub.marge.util.toEasyFuture

class BankAPI(private val serviceHub: AppServiceHub, private val patients: List<Patient>, private val ledger: LedgerApiImpl) : Initializer() {
    companion object {
        const val BANK_ISSUANCE_ACCOUNT = "bank"
        const val TOKEN_SYMBOL = "GBP"
        var TOKEN_TYPE_URI: String = ""

        private val log = loggerFor<BankAPI>()
    }

    override fun initialiseDemo(): Future<Unit> {
        return if (!initialised) {
            initialised = true
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }.map { it.name }
                .filter { it.organisation.contains("insurer", true) }

            val hospitals = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }.map { it.name }
                .filter { it.organisation.contains("hospital", true) }

            val totalOtherPartyTransfers = Insurers.allInsurers.size * 1_000_000 + hospitals.size * 500_000
            ledger.createTokenType(TOKEN_SYMBOL, 2, notary.name)
                .onSuccess { TOKEN_TYPE_URI = it.descriptor.uri }
                .compose { ledger.createAccount(BANK_ISSUANCE_ACCOUNT, notary.name) }
                .compose { ledger.issueToken(
                    BANK_ISSUANCE_ACCOUNT,
                    "$totalOtherPartyTransfers.00",
                    TOKEN_SYMBOL,
                    "initial issuance",
                    notary.name
                ) }
                .compose {
                    // create the accounts for the patients
                    val requests = patients.map { CreateAccountFlow.Request(it.name) }
                    serviceHub.startFlow(CreateAccountFlow(requests, notary)).toEasyFuture()
                }
                .compose {
                    // sequentially issue to each patient
                    // we do this because it's easier to fold
                    patients.fold(Future.succeededFuture<Unit>()) { acc, patient ->
                        acc.compose { ledger.issueToken(
                            patient.name,
                            "100000.00",
                            TOKEN_SYMBOL,
                            "pocket money",
                            notary.name
                        ).mapEmpty<Unit>() }
                    }
                }
                .compose {
                    // transfer funds to the insurers
                    Insurers.allInsurers.fold(Future.succeededFuture<Unit>()) { acc, insurer ->
                        val insurerAccount = AccountAddress(InsurerAPI.INSURER_ACCOUNT, insurer).toString()
                        acc.compose {
                            ledger.transferToken(
                                "1000000.0",
                                TOKEN_TYPE_URI,
                                BANK_ISSUANCE_ACCOUNT,
                                insurerAccount,
                                "there you go",
                                notary.name
                            ).mapEmpty<Unit>()
                        }
                    }
                }
                .compose {
                    // transfer funds to the insurers
                    hospitals.fold(Future.succeededFuture<Unit>()) { acc, hospital ->
                        val hospitalAccount = AccountAddress(HospitalAPI.HOSPITAL_ACCOUNT, hospital).toString()
                        acc.compose {
                            ledger.transferToken(
                                "500000.0",
                                TOKEN_TYPE_URI,
                                BANK_ISSUANCE_ACCOUNT,
                                hospitalAccount,
                                "there you go",
                                notary.name
                            ).mapEmpty<Unit>()
                        }
                    }
                }
                .onSuccess {
                    log.info("bank initialised")
                }.onFail {
                    log.error("failed to initialise bank!", it)
                }
        } else {
            Future.succeededFuture()
        }
    }

    fun getInitialState() : Future<BankInitialState> {
        return initialiseDemo()
            .compose { ledger.listAccounts() }
            .map { accounts ->

                val balances = accounts.map { it.address.accountId }
                    .sorted()
                    .map {
                        val balance = Account.getBalances(serviceHub, it).firstOrNull()
                            ?.toDecimal()?.toString()?:"0.00"
                        it to balance}.toMap()
                BankInitialState(serviceHub.myInfo.legalIdentities.first().name.organisation, balances)
            }
    }
}

data class BankInitialState(val name: String, val balances: Map<String, String>)
