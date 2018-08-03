package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.util.*

@CordaSerializable
data class InsurerPaymentPayload(
        val treatmentState: StateAndRef<TreatmentState>,
        val outputTreatmentState: TreatmentState?,
        val amountToPay: Amount<Currency>,
        val insurerQuoteState: StateAndRef<InsurerQuoteState>)

/**
 * Triggered by the hospital to collect money from the insurer
 */
@InitiatingFlow
class InsurerTreatmentPaymentFlow(
        private val insurerQuoteState: StateAndRef<InsurerQuoteState>,
        private val treatmentState: StateAndRef<TreatmentState>,
        private val insuranceAmount: Amount<Currency>) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // create a tx with the insurer to (partially) settle the cost of the treatment
        val insurerSession = initiateFlow(insurerQuoteState.state.data.insurer)

        val inputTreatmentState = treatmentState.state.data
        val remaining = inputTreatmentState.treatmentCost - insuranceAmount

        // if the treatment is not paid in fully by the insurer, a new TreatmentState is created to be payed by the patient
        val outputTreatmentState = if (remaining.quantity == 0L) null else TreatmentState(inputTreatmentState.treatment, remaining, linearId = inputTreatmentState.linearId)

        // send the [InsurerPaymentPayload] and receive the transaction containing the payment
        insurerSession.send(InsurerPaymentPayload(treatmentState, outputTreatmentState, insuranceAmount, insurerQuoteState))

        return subFlow(object: SignTransactionFlow(insurerSession){
            override fun checkTransaction(stx: SignedTransaction) {
                //all good
            }
        })
    }
}

@InitiatedBy(InsurerTreatmentPaymentFlow::class)
class InsurerTreatmentPaymentResponseFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val hospital = session.counterparty
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val insurerPaymentPayload = session.receive<InsurerPaymentPayload>().unwrap { it }

        //Build transaction
        val txb = TransactionBuilder(notary).apply {
            addCommand(Command(InsurerQuoteCommand.RedeemQuote(), listOf(ourIdentity.owningKey)))
            addCommand(Command(TreatmentCommand.PayTreatment(), listOf(hospital.owningKey, ourIdentity.owningKey)))
            addInputState(insurerPaymentPayload.insurerQuoteState)
            addInputState(insurerPaymentPayload.treatmentState)
            insurerPaymentPayload.outputTreatmentState?.let { addOutputState(it, TreatmentContract.CONTRACT_ID, notary) }
            // todo Fuzz - select treatmentCost.amountToPay tokens from the insurer account and pay them to the hospital
        }

        val stx = serviceHub.signInitialTransaction(txb) // insurer signs the transaction
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(session)))

        val bank = serviceHub.networkMapCache.allNodes.find { node ->
            node.legalIdentities.first().name.organisation.toLowerCase().contains("bank")
        }!!.legalIdentities.first()


        val result = subFlow(FinalityFlow(fullySignedTransaction, setOf(bank)))
        println("Finished InsurerTreatmentPaymentResponseFlow")

        return result
    }
}


/**
 * Triggered by the hospital to collect money from the patient's bank
 */
@InitiatingFlow
class PatientTreatmentPaymentFlow(private val paymentFromInsurerTx: SignedTransaction) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // create a tx with the patient's bank to settle the rest
        val bank = serviceHub.networkMapCache.allNodes.find { node ->
            node.legalIdentities.first().name.organisation.toLowerCase().contains("bank")
        }!!.legalIdentities.first()

        val bankSession = initiateFlow(bank)
        val treatmentState = paymentFromInsurerTx.coreTransaction.outRefsOfType<TreatmentState>().first()
        bankSession.send(treatmentState)
        return subFlow(object: SignTransactionFlow(bankSession){
            override fun checkTransaction(stx: SignedTransaction) {
                //all good
            }
        })
    }
}

// This is run by the bank
@InitiatedBy(PatientTreatmentPaymentFlow::class)
class PatientTreatmentPaymentResponseFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val hospital = session.counterparty

        val treatment = session.receive<StateAndRef<TreatmentState>>().unwrap { it }

        //Build transaction
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txb = TransactionBuilder(notary).apply {
            addCommand(Command(TreatmentCommand.PayTreatment(), listOf(hospital.owningKey, ourIdentity.owningKey)))
            addInputState(treatment)
            // todo Fuzz - select treatment.treatmentCost tokens from the patient account and pay them to the hospital
        }

        val stx = serviceHub.signInitialTransaction(txb) // insurer signs the transaction
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(session)))
        val tx = subFlow(FinalityFlow(fullySignedTransaction))
        println("Finished PatientTreatmentPaymentResponseFlow")
        return tx
    }
}

