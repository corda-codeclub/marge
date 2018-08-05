package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import java.util.*

/**
 * This is run by the Hospital when raising a claim
 */
@StartableByRPC
@StartableByService
@InitiatingFlow
class RetrieveInsurerQuotesFlow(private val treatmentCoverageEstimation: TreatmentCoverageEstimation, private val insurers: List<Party>) : FlowLogic<SignedTransaction>() {

    companion object {
        private val log = loggerFor<RetrieveInsurerQuotesFlow>()
    }

    @Suspendable
    override fun call(): SignedTransaction {

        // Collect quotes from each insurer and select the best for the committed quote.
        val quotes = insurers.map { insurer ->
            // set up flow session with the insurer
            val session = initiateFlow(insurer)

            // send the claim request and receive the claim state
            val insurerQuote = session.sendAndReceive<Amount<Currency>>(treatmentCoverageEstimation).unwrap { it }

            println("Received quote: ${insurerQuote} from insurer ${insurer}")
            Pair(insurerQuote, session)
        }.sortedByDescending { it.first }

        for ((_, session) in quotes.drop(1)) {
            session.send(QuoteStatus.REJECTED_QUOTE)
        }
        val bestQuote = quotes.first()
        bestQuote.second.send(QuoteStatus.ACCEPTED_QUOTE)
        return createTransactionSignAndCommit(bestQuote.first, bestQuote.second)
    }

    @Suspendable
    private fun createTransactionSignAndCommit(amount: Amount<Currency>, session: FlowSession): SignedTransaction {
        val insurer = session.counterparty
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txb = TransactionBuilder(notary).apply {
            addCommand(Command(InsurerQuoteCommand.IssueQuote(), listOf(insurer.owningKey, ourIdentity.owningKey)))
            addOutputState(InsurerQuoteState(treatmentCoverageEstimation, insurer, amount), InsurerQuoteContract.CONTRACT_ID, notary)
        }
        val stx = serviceHub.signInitialTransaction(txb) // hospital signs the transaction
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(session)))
        return subFlow(FinalityFlow(fullySignedTransaction))
    }
}

@CordaSerializable
sealed class QuoteStatus {
    @CordaSerializable
    object REJECTED_QUOTE : QuoteStatus()

    @CordaSerializable
    object ACCEPTED_QUOTE : QuoteStatus()
}

/**
 * This class handles the insurers side of the flow and initiated by [RetrieveInsurerQuotesFlow]
 */
@InitiatedBy(RetrieveInsurerQuotesFlow::class)
class InsurerRespondFlow(private val session: FlowSession) : FlowLogic<SignedTransaction?>() {

    @Suspendable
    override fun call(): SignedTransaction? {
        val treatment = session.receive<TreatmentCoverageEstimation>().unwrap { treatmentCost ->
            // TODO: add security checks to ensure that we can even begin processing this claim
            requireThat {
            }
            treatmentCost // return the claim because we've passed our checks for the payload
        }

        val status = session.sendAndReceive<QuoteStatus>(calculateAmountWeCanPay(treatment)).unwrap { it }

        if (status == QuoteStatus.ACCEPTED_QUOTE) {
            val signTransactionFlow = object : SignTransactionFlow(session, SignTransactionFlow.tracker()) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    stx.verify(serviceHub, checkSufficientSignatures = false) // verify the transaction - we set checkSufficientSignatures to false because we don't sign until this stage is complete
                    "the claim in the proposed output state matches the one we had received" using (stx.tx.outputsOfType<InsurerQuoteState>().first().request == treatment)
                }
            }
            // we invoke the sign Transaction flow which in turn awaits the CollectSignaturesFlow above
            return subFlow(signTransactionFlow)
        }
        return null
    }

    // This performs a highly complex algorithm.
    @Suspendable
    private fun calculateAmountWeCanPay(treatmentCoverageEstimation: TreatmentCoverageEstimation): Amount<Currency> {
        val percentage = (Random().nextDouble() * 100).toInt()
        val amount = treatmentCoverageEstimation.estimatedAmount
        return amount.copy(quantity = (amount.quantity * percentage) / 100)
    }
}