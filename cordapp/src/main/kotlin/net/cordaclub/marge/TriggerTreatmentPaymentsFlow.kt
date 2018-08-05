package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * This is run by the Hospital when the treatment is over.
 * It will self sign a TreatmentState and then run sub-flows to collect the payment from the Insurer and maybe the Patient.
 */
@StartableByRPC
@StartableByService
class TriggerTreatmentPaymentsFlow(private val treatment: Treatment, private val treatmentCost: Amount<Currency>, private val insurerQuoteState: StateAndRef<InsurerQuoteState>) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // Create and sign the Treatment State that will be used to justify the redemption of the Quote, and payment from the patient.
        val issueTreatmentTx = issueTreatmentState(insurerQuoteState.state.data.insurer)

        val treatmentState = issueTreatmentTx.coreTransaction.outRef<TreatmentState>(0)

        // The amount to be paid by the insurer
        val insuranceAmount = min(treatmentCost, insurerQuoteState.state.data.maxCoveredValue)

        val paymentFromInsurerTx = subFlow(InsurerFlows.InsurerTreatmentPaymentFlow(insurerQuoteState, treatmentState, insuranceAmount))

        // the patient needs to cover the difference
        if (paymentFromInsurerTx.coreTransaction.outRefsOfType<TreatmentState>().isNotEmpty()) {
            subFlow(PatientFlows.PatientTreatmentPaymentFlow(paymentFromInsurerTx))
        }
        println("Finished TriggerTreatmentPaymentsFlow")
    }

    @Suspendable
    private fun issueTreatmentState(insurer: Party): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txb = TransactionBuilder(notary).apply {
            addCommand(Command(TreatmentCommand.IssueTreatment(), listOf(ourIdentity.owningKey)))
            addOutputState(TreatmentState(treatment, treatmentCost), TreatmentContract.CONTRACT_ID, notary)
        }
        val stx = serviceHub.signInitialTransaction(txb)

        return subFlow(FinalityFlow(stx, setOf(insurer)))
    }
}
