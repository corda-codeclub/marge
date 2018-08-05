package net.cordaclub.marge.hospital

import io.cordite.dgl.corda.impl.LedgerApiImpl
import io.cordite.dgl.corda.token.TokenType
import io.vertx.core.Future
import net.corda.core.contracts.Amount
import net.corda.core.node.AppServiceHub
import net.corda.core.utilities.loggerFor
import net.cordaclub.marge.Initializer
import net.cordaclub.marge.Patient
import net.cordaclub.marge.Patients
import net.cordaclub.marge.util.onFail
import net.cordaclub.marge.util.onSuccess
import java.math.BigDecimal

class HospitalAPI(private val serviceHub: AppServiceHub) : Initializer(){
    companion object {
        const val HOSPITAL_ACCOUNT = "hospital"
        private val log = loggerFor<HospitalAPI>()
    }

    val ledgerApi = LedgerApiImpl(serviceHub)

    override fun initialiseDemo() : Future<Unit> {
        return if (!initialised) {
            initialised = true
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            ledgerApi.createAccount(HOSPITAL_ACCOUNT, notary.name).mapEmpty<Unit>()
                .recover {
                    log.info("account exists")
                    Future.succeededFuture<Unit>()
                }
                .onSuccess { log.info("hospital initialised") }
                .onFail { log.info("failed to initialise hospital") }
        } else {
            Future.succeededFuture()
        }
    }

    fun getInitialState(): Future<HospitalInitialState> {
        return initialiseDemo()
            .compose {
                ledgerApi.balanceForAccount(HOSPITAL_ACCOUNT)
            }
            .map { balances ->
                val balancesString = balances.map { BigDecimal(it.quantity).multiply(it.displayTokenSize).toString() }
                HospitalInitialState(
                    serviceHub.myInfo.legalIdentities.first().name.organisation,
                    Patients.allPatients,
                    balancesString
                )
            }
    }
}

data class HospitalInitialState(val name: String, val patients: List<Patient>, val balances: List<String>)