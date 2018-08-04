package net.cordaclub.marge.bank

import io.bluebank.braid.core.async.getOrThrow
import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.account.GetAccountFlow
import io.cordite.dgl.corda.impl.LedgerApiImpl
import io.cordite.dgl.corda.token.Token
import io.cordite.dgl.corda.token.flows.IssueTokenFlow
import io.cordite.dgl.corda.token.issuedBy
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import net.corda.core.node.AppServiceHub
import net.corda.core.utilities.getOrThrow
import net.cordaclub.marge.Initializer
import net.cordaclub.marge.Patient
import net.cordaclub.marge.util.toEasyFuture

class BankAPI(private val serviceHub: AppServiceHub, private val patients: List<Patient>) : Initializer() {

    override fun initialiseDemo(): Future<Unit> {
        if (!initialised) {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            try {
                patients.map { patient ->
                    serviceHub.startFlow(GetAccountFlow(accountId = patient.name)).returnValue.getOrThrow()
                }
            } catch (e: Exception) {
                val ledgerApi = LedgerApiImpl(serviceHub)
                ledgerApi.createTokenType("GBP", 1, notary.name).getOrThrow()
                patients.map { patient ->
                    val account = ledgerApi.createAccount(patient.name, notary.name).getOrThrow()
                    ledgerApi.issueToken(account.address.accountId, "10000.00", "GBP", "issuance", notary.name)
                }
            }
        }
        initialised = true
        return Future.succeededFuture()
    }
}