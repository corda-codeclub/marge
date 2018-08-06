package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import io.cordite.dgl.corda.account.Account
import io.cordite.dgl.corda.account.GetAccountFlow
import io.cordite.dgl.corda.token.TokenType
import io.cordite.dgl.corda.token.flows.TransferTokenSenderFunctions
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.cordaclub.marge.hospital.HospitalAPI
import net.cordaclub.marge.insurer.InsurerAPI
import java.util.*

object InsurerFlows {

    @CordaSerializable
    data class InsurerPaymentPayload(
            val amountToPay: Amount<Currency>,
            val hospitalAccount: Account.State
    )

    /**
     * Triggered by the hospital to collect money from the insurer
     */
    @InitiatingFlow
    class InsurerTreatmentPaymentFlow(private val treatmentState: StateAndRef<TreatmentState>, private val hospitalAccount: Account.State) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val treatment = treatmentState.state.data

            // create a tx with the insurer to (partially) settle the cost of the treatment
            val insurerSession = initiateFlow(treatment.insurerQuote!!.insurer)

            subFlow(SendStateAndRefFlow(insurerSession, listOf(treatmentState)))

            // send the [InsurerPaymentPayload] and receive the transaction containing the payment
            // The amount to be paid by the insurer
            val insuranceAmount = min(treatment.treatmentCost!!, treatment.insurerQuote!!.maxCoveredValue)

            insurerSession.send(InsurerPaymentPayload(insuranceAmount, hospitalAccount))

            val tx = subFlow(object : SignTransactionFlow(insurerSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    //all good
                }
            })
            return waitForLedgerCommit(tx.id)
        }
    }

    @InitiatedBy(InsurerTreatmentPaymentFlow::class)
    class InsurerTreatmentPaymentResponseFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val hospital = session.counterparty
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val treatmentState = subFlow(ReceiveStateAndRefFlow<TreatmentState>(session)).single()
            val treatment = treatmentState.state.data

            val insurerPaymentPayload = session.receive<InsurerPaymentPayload>().unwrap { it }

            //Build transaction
            val txb = TransactionBuilder(notary).apply {
                addCommand(Command(TreatmentCommand.CollectInsurerPay(), listOf(hospital.owningKey, ourIdentity.owningKey)))
                addInputState(treatmentState)
                addOutputState(treatmentState.state.copy(data = treatment.let {
                    TreatmentState(
                            treatment = it.treatment,
                            estimatedTreatmentCost = it.estimatedTreatmentCost,
                            treatmentCost = it.treatmentCost,
                            amountPayed = insurerPaymentPayload.amountToPay,
                            insurerQuote = it.insurerQuote,
                            treatmentStatus = TreatmentStatus.PARTIALLY_PAYED,
                            linearId = it.linearId
                    )
                }))
            }

            // select treatmentCost tokens from the insurer account and pay them to the hospital
            val insurerAccount = subFlow(GetAccountFlow(InsurerAPI.INSURER_ACCOUNT)).state.data
            val inputSigningKeys = TransferTokenSenderFunctions.prepareTokenMoveWithSummary(
                    txb, insurerAccount.address, insurerPaymentPayload.hospitalAccount.address, insurerPaymentPayload.amountToPay!!.toToken(ourIdentity), serviceHub, ourIdentity, "pay for treatment $treatment")

            val stx = serviceHub.signInitialTransaction(txb) // insurer signs the transaction
            val fullySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(session)))

            val result = subFlow(FinalityFlow(fullySignedTransaction))
            println("Finished InsurerTreatmentPaymentResponseFlow")

            return result
        }
    }

}

fun getBank(serviceHub: ServiceHub) = serviceHub.networkMapCache.allNodes.find { node ->
    node.legalIdentities.first().name.organisation.toLowerCase().contains("bank")
}!!.legalIdentities.first()


fun Amount<Currency>.toToken(issuer: Party): Amount<TokenType.Descriptor> = Amount(
        quantity = this.quantity,
        displayTokenSize = this.displayTokenSize,
        token = TokenType.Descriptor("GBP", 1, issuer.name))