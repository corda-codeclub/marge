package net.cordaclub.marge.hospital

import io.bluebank.braid.corda.services.transaction
import io.cordite.dgl.corda.impl.LedgerApiImpl
import io.cordite.dgl.corda.token.listAllTokenTypes
import io.vertx.core.Future
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.loggerFor
import net.corda.finance.GBP
import net.cordaclub.marge.*
import net.cordaclub.marge.util.*
import rx.Observable
import java.math.BigDecimal
import java.util.*

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
                val balancesString = balances.firstOrNull()?.toDecimal()?.toString()?:"0.00"
                HospitalInitialState(
                    serviceHub.myInfo.legalIdentities.first().name.organisation,
                    Patients.allPatients,
                    balancesString,
                    serviceHub.loadTreatments().map { it.linearId.id.toString() to it }.toMap()
                )
            }
    }

    fun processTreatmentRequest(request: TreatmentRequest) : Future<TreatmentState> {
        val flow = try {
            val patient = Patients.allPatients.firstOrNull { it.name == request.name }
                ?: throw RuntimeException("could not find patient ${request.name}")

            val amount = BigDecimal(request.amount).longValueExact() * 100
            val estimation = TreatmentCoverageEstimation(
                Treatment(
                    patient,
                    request.description,
                    this.serviceHub.myInfo.legalIdentities.first()
                ), Amount(amount, GBP))

            val insurers = Insurers.allInsurers.map {
                serviceHub.networkMapCache.getNodeByLegalName(it)?.legalIdentities?.first() ?: throw RuntimeException("failed to locate insurer $it")
            }
            InsurerQuotingFlows.RetrieveInsurerQuotesFlow(estimation, insurers)
        } catch (err: Throwable) {
            return Future.failedFuture(err)
        }
        return serviceHub.startFlow(flow).toEasyFuture()
            .map { it.coreTransaction.outputsOfType(TreatmentState::class.java).first() }
    }

    fun listenForTreatments() : Observable<List<TreatmentState>> {
        return serviceHub.listenForTreatments()
    }

    fun requestPayment(id: String, amount: Long) : Future<Unit> {
        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(UniqueIdentifier(id = UUID.fromString(id))))
        val treatmentStateAndRef = serviceHub.transaction {
            val results = serviceHub.vaultService.queryBy<TreatmentState>(criteria)
            results.states.first()
        }
        val actualAmount = Amount(amount, GBP)
        val flow = TriggerTreatmentPaymentsFlow(treatmentStateAndRef, actualAmount)
        return serviceHub.startFlow(flow).toEasyFuture().mapEmpty<Unit>()
    }
}

data class HospitalInitialState(val name: String, val patients: List<Patient>, val balance: String, val treatments: Map<String, TreatmentState>)
data class TreatmentRequest(val name: String, val description: String, val amount: String)