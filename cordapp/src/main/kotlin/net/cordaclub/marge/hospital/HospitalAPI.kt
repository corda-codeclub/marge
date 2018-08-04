package net.cordaclub.marge.hospital

import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.account.GetAccountFlow
import io.vertx.core.Future
import net.corda.core.node.AppServiceHub
import net.cordaclub.marge.Patient
import net.cordaclub.marge.Patients
import net.cordaclub.marge.util.onSuccess
import net.cordaclub.marge.util.toEasyFuture

class HospitalAPI(private val serviceHub: AppServiceHub) {
    companion object {
        const val HOSPITAL_ACCOUNT = "hospital"
    }

    private var initialised = false

    fun isInitialised(): Boolean {
        return initialised
    }

    fun initialiseDemo(): Future<Unit> {
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

    fun getInitialState(): Future<HospitalInitialState> {
        return initialiseDemo()
            .map {
                HospitalInitialState(
                    serviceHub.myInfo.legalIdentities.first().name.organisation,
                    Patients.allPatients
                )
            }
    }
}


data class HospitalInitialState(val name: String, val patients: List<Patient>)