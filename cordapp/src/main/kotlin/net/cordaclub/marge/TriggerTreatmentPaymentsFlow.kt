package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import io.cordite.dgl.corda.account.GetAccountFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.cordaclub.marge.hospital.HospitalAPI
import java.util.*

/**
 * Second Flow!
 * This is run by the Hospital when the treatment is over.
 *
 * 1) It will evolve the TreatmentSate
 * 2) It will requests payments from the insurer and patient
 */
@StartableByRPC
@StartableByService
class TriggerTreatmentPaymentsFlow(private val treatmentState: StateAndRef<TreatmentState>, private val realTreatmentCost: Amount<Currency>) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        //todo check the state
        val finalisedTreatment = finaliseTreatment()
        val hospitalAccount = subFlow(GetAccountFlow(HospitalAPI.HOSPITAL_ACCOUNT)).state.data

        val paymentFromInsurerTx = subFlow(InsurerFlows.InsurerTreatmentPaymentFlow(finalisedTreatment.coreTransaction.outRef(0), hospitalAccount))

        // the patient needs to cover the difference
        subFlow(PatientFlows.PatientTreatmentPaymentFlow(paymentFromInsurerTx, hospitalAccount))

        println("Finished TriggerTreatmentPaymentsFlow")
    }

    @Suspendable
    private fun finaliseTreatment(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val treatment = treatmentState.state.data
        val txb = TransactionBuilder(notary).apply {
            addCommand(Command(TreatmentCommand.FinaliseTreatment(), listOf(ourIdentity.owningKey)))
            addInputState(treatmentState)
            addOutputState(treatmentState.state.copy(data = treatment.let {
                TreatmentState(
                        treatment = it.treatment,
                        estimatedTreatmentCost = it.estimatedTreatmentCost,
                        treatmentCost = realTreatmentCost,
                        amountPayed = null,
                        insurerQuote = it.insurerQuote,
                        treatmentStatus = TreatmentStatus.FINALISED,
                        linearId = it.linearId
                )
            }))
        }
        val stx = serviceHub.signInitialTransaction(txb)
        return subFlow(FinalityFlow(stx))
    }
}
