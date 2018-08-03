package net.cordaclub.marge.insurer

import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.account.GetAccountFlow
import io.vertx.core.Future
import net.corda.core.node.AppServiceHub
import net.cordaclub.marge.util.onSuccess
import net.cordaclub.marge.util.toEasyFuture

class InsurerAPI(private val serviceHub: AppServiceHub) {
    companion object {
        const val INSURER_ACCOUNT = "insurer"
    }
    private var initialised = false

    fun isInitialised() : Boolean {
        return initialised
    }

    fun initialiseDemo() : Future<Unit> {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        return serviceHub.startFlow(GetAccountFlow(accountId = INSURER_ACCOUNT))
            .toEasyFuture().mapEmpty<Unit>()
            .recover {
                serviceHub.startFlow(CreateAccountFlow(listOf(CreateAccountFlow.Request(INSURER_ACCOUNT)), notary))
                    .toEasyFuture().mapEmpty()
            }
            .onSuccess {
                initialised = true
            }
    }
}

