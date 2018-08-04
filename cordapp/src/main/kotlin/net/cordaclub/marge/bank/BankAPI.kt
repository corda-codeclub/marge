package net.cordaclub.marge.bank

import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.account.GetAccountFlow
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import net.corda.core.node.AppServiceHub
import net.cordaclub.marge.Patient
import net.cordaclub.marge.util.toEasyFuture

class BankAPI(private val serviceHub: AppServiceHub, private val patients: List<Patient>) {
    private var initialised = false

    fun isInitialised(): Boolean {
        return initialised
    }

    fun initialiseDemo(): Future<Unit> {
        if (!initialised) {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            initialised = CompositeFuture.all(
                    patients.map { patient ->
                        serviceHub.startFlow(GetAccountFlow(accountId = patient.name))
                                .toEasyFuture().mapEmpty<Unit>()
                                .recover {
                                    serviceHub.startFlow(CreateAccountFlow(listOf(CreateAccountFlow.Request(patient.name)), notary)).toEasyFuture().mapEmpty()
                                }
                    }
            ).succeeded()
        }
        return Future.succeededFuture()
    }

}