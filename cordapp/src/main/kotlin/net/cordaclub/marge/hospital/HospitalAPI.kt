package net.cordaclub.marge.hospital

import io.bluebank.braid.core.async.getOrThrow
import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.account.GetAccountFlow
import io.cordite.dgl.corda.impl.LedgerApiImpl
import io.vertx.core.Future
import net.corda.core.node.AppServiceHub
import net.corda.core.utilities.getOrThrow
import net.cordaclub.marge.Initializer
import net.cordaclub.marge.util.onSuccess
import net.cordaclub.marge.util.toEasyFuture

class HospitalAPI(private val serviceHub: AppServiceHub) : Initializer(){
    companion object {
        const val HOSPITAL_ACCOUNT = "hospital"
    }

    override fun initialiseDemo() : Future<Unit> {
        if (!initialised) {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            try {
                serviceHub.startFlow(GetAccountFlow(accountId = HOSPITAL_ACCOUNT)).returnValue.getOrThrow()
            } catch (e: Exception) {
                val ledgerApi = LedgerApiImpl(serviceHub)
                ledgerApi.createTokenType("GBP", 1, notary.name).getOrThrow()
                val account = ledgerApi.createAccount(HOSPITAL_ACCOUNT, notary.name).getOrThrow()
//                ledgerApi.issueToken(account.address.accountId, "100000.00", "GBP", "issuance", notary.name)
            }
        }
        initialised = true
        return Future.succeededFuture()
    }

    fun getPatients() {

    }

}