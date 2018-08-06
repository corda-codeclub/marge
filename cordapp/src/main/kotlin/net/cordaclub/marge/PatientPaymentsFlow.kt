package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import io.cordite.dgl.corda.account.AccountAddress
import io.cordite.dgl.corda.account.GetAccountFlow
import io.cordite.dgl.corda.token.flows.TransferTokenSenderFunctions.Companion.prepareTokenMoveWithSummary
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

object PatientFlows {

    /**
     * Triggered by the hospital to collect money from the patient's bank
     */
    @InitiatingFlow
    class PatientTreatmentPaymentFlow(private val paymentFromInsurerTx: SignedTransaction, private val hospitalAccount: AccountAddress) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // create a tx with the patient's bank to settle the rest
            val bankSession = initiateFlow(getBank(serviceHub))
            val treatmentState = paymentFromInsurerTx.coreTransaction.outRefsOfType<TreatmentState>().first()
            subFlow(SendStateAndRefFlow(bankSession, listOf(treatmentState)))
            bankSession.send(hospitalAccount)

            val tx = subFlow(object : SignTransactionFlow(bankSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val tx = stx.coreTransaction.outputsOfType<TreatmentState>().single()
                    stx.verify(serviceHub, checkSufficientSignatures = false)
                    "The treatment is payed in full." using (tx.amountPayed!! == tx.treatmentCost!!)
                    //todo - check that the correct tokens were added
                }
            })
            return waitForLedgerCommit(tx.id)
        }
    }

    /**
     * This is run by the bank
     */
    @InitiatedBy(PatientTreatmentPaymentFlow::class)
    class PatientTreatmentPaymentResponseFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val hospital = session.counterparty

            val treatmentState = subFlow(ReceiveStateAndRefFlow<TreatmentState>(session)).single()

            val hospitalAccount = session.receive<AccountAddress>().unwrap { it }

            val treatment = treatmentState.state.data
            val toPay = treatment.treatmentCost!! - treatment.amountPayed!!

            //Build transaction
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val txb = TransactionBuilder(notary).apply {
                addCommand(Command(TreatmentCommand.FullyPayTreatment(), listOf(hospital.owningKey, ourIdentity.owningKey)))
                addInputState(treatmentState)
                addOutputState(treatmentState.state.copy(data = treatmentState.state.data.let {
                    TreatmentState(
                            treatment = it.treatment,
                            estimatedTreatmentCost = it.estimatedTreatmentCost,
                            treatmentCost = it.treatmentCost,
                            amountPayed = it.treatmentCost,
                            insurerQuote = it.insurerQuote,
                            treatmentStatus = TreatmentStatus.FULLY_PAID,
                            linearId = it.linearId
                    )
                }))
            }

            // select treatmentCost tokens from the patient account and pay them to the hospital
            val patientAccount = subFlow(GetAccountFlow(treatment.treatment.patient.name)).state.data
            prepareTokenMoveWithSummary(txb, patientAccount.address, hospitalAccount, toPay.toToken(getBank(serviceHub)), serviceHub, ourIdentity, "pay for treatment $treatment")

            val stx = serviceHub.signInitialTransaction(txb) // insurer signs the transaction
            val fullySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(session)))
            val tx = subFlow(FinalityFlow(fullySignedTransaction))
            println("Finished PatientTreatmentPaymentResponseFlow")
            return tx
        }
    }

}

