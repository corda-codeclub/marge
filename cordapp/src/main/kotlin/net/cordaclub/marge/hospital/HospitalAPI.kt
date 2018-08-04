package net.cordaclub.marge.hospital

import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.account.GetAccountFlow
import io.vertx.core.Future
import net.corda.core.node.AppServiceHub
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
            return serviceHub.startFlow(GetAccountFlow(accountId = HOSPITAL_ACCOUNT))
                .toEasyFuture().mapEmpty<Unit>()
                .recover {
                    serviceHub.startFlow(CreateAccountFlow(listOf(CreateAccountFlow.Request(HOSPITAL_ACCOUNT)), notary))
                        .toEasyFuture().mapEmpty()
                }
                .onSuccess {
                    initialised = true
                }
        } else {
            return Future.succeededFuture()
        }
    }

    fun getPatients() {

    }

}