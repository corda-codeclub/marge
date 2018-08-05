package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import io.cordite.dgl.corda.account.Account
import io.cordite.dgl.corda.account.GetAccountFlow
import io.cordite.dgl.corda.token.flows.TransferTokenSenderFunctions
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.cordaclub.marge.hospital.HospitalAPI

object PatientFlows {
    /**
     * Triggered by the hospital to collect money from the patient's bank
     */
    @InitiatingFlow
    class PatientTreatmentPaymentFlow(private val paymentFromInsurerTx: SignedTransaction) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // create a tx with the patient's bank to settle the rest
            val bankSession = initiateFlow(getBank(serviceHub))
            val treatmentState = paymentFromInsurerTx.coreTransaction.outRefsOfType<TreatmentState>().first()
            subFlow(SendStateAndRefFlow(bankSession, listOf(treatmentState)))

            val hospitalAccount = subFlow(GetAccountFlow(HospitalAPI.HOSPITAL_ACCOUNT)).state.data
            bankSession.send(hospitalAccount)

            val tx = subFlow(object : SignTransactionFlow(bankSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    //all good
                }
            })
            return waitForLedgerCommit(tx.id)
        }
    }

    // This is run by the bank
    @InitiatedBy(PatientTreatmentPaymentFlow::class)
    class PatientTreatmentPaymentResponseFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val hospital = session.counterparty

            val treatmentState = subFlow(ReceiveStateAndRefFlow<TreatmentState>(session)).single()

            val hospitalAccount = session.receive<Account.State>().unwrap { it }

            //Build transaction
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val txb = TransactionBuilder(notary).apply {
                addCommand(Command(TreatmentCommand.PayTreatment(), listOf(hospital.owningKey, ourIdentity.owningKey)))
                addInputState(treatmentState)
            }

            //todo Fuzz - pls review
            // select treatmentCost tokens from the patient account and pay them to the hospital
            val treatment = treatmentState.state.data
            val toPay = treatment.treatmentCost
            val patientAccount = subFlow(GetAccountFlow(treatment.treatment.patient.name)).state.data
            val inputSigningKeys = TransferTokenSenderFunctions.prepareTokenMoveWithSummary(
                    txb, patientAccount.address, hospitalAccount.address, toPay.toToken(ourIdentity), serviceHub, ourIdentity, "pay for treatment $treatment")

            val stx = serviceHub.signInitialTransaction(txb) // insurer signs the transaction
            val fullySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(session)))
            val tx = subFlow(FinalityFlow(fullySignedTransaction))
            println("Finished PatientTreatmentPaymentResponseFlow")
            return tx
        }
    }

}

