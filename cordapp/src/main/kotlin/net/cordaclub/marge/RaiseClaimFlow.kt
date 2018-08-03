package net.cordaclub.marge

import co.paralleluniverse.fibers.Suspendable
import com.template.ClaimCommand
import com.template.ClaimContract
import com.template.ClaimRequest
import com.template.ClaimState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap

/**
 * This is run by the Hospital when raising a claim
 */
@StartableByRPC
@StartableByService
@InitiatingFlow
class RaiseClaimFlow(private val claimRequest: ClaimRequest) : FlowLogic<SignedTransaction>() {

    companion object {
        private val log = loggerFor<RaiseClaimFlow>()
    }

    @Suspendable
    override fun call(): SignedTransaction {
        // set up flow session with the insurer
        val session = initiateFlow(claimRequest.insurer)
        // send the claim request and receive the claim state
        sendClaim(session)
        val claimState = receiveClaimState(session)
        return createTransactionSignAndCommit(claimState, session)
    }

    @Suspendable
    private fun createTransactionSignAndCommit(claimState: ClaimState, session: FlowSession): SignedTransaction {
        log.info("selecting notary from the first available in the network") // for real system, we should be more prescriptive
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        // hospital constructs the transactions
        val txb = TransactionBuilder(notary)
        // what type of transaction is this and who should be signing it
        txb.addCommand(Command(ClaimCommand.ClaimRequestCommand(), listOf(claimRequest.insurer.owningKey, ourIdentity.owningKey)))
        // add the claim as the only output state of this transaction, ref the contract that will verify this transaction anytime in the future e.g. by notary
        txb.addOutputState(claimState, ClaimContract.CONTRACT_ID, notary)
        log.info("signing transaction")
        val stx = serviceHub.signInitialTransaction(txb) // hospital signs the transaction
        log.info("awaiting signature collection from all sides")
        val fullySignedTransaction = subFlow(CollectSignaturesFlow(stx, listOf(session), CollectSignaturesFlow.tracker()))
        log.info("initiating notarisation and awaiting successful result")
        val result = subFlow(FinalityFlow(fullySignedTransaction))
        log.info("transaction fully notarised and committed")
        return result
    }

    @Suspendable
    private fun receiveClaimState(session: FlowSession): ClaimState {
        log.info("awaiting return of ClaimState")
        val claimState = session.receive<ClaimState>().unwrap { it }
        log.info("received ClaimState")
        return claimState
    }

    @Suspendable
    private fun sendClaim(session: FlowSession) {
        log.info("sending claim request $claimRequest")
        session.send(claimRequest)
        log.info("claim request sent $claimRequest")
    }
}


/**
 * This class handles the insurers side of the flow and initiated by [RaiseClaimFlow]
 */
@InitiatedBy(RaiseClaimFlow::class)
class VerifyClaimFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
    companion object {
        private val log = loggerFor<RaiseClaimFlow>()
    }

    @Suspendable
    override fun call(): SignedTransaction {
        val hospital = session.counterparty
        log.info("claim flow started by hospital ${hospital.name.commonName}")
        val claim = receiveClaimRequest()
        log.info("request received $claim")
        val claimState = calculateCompensation(hospital, claim)
        log.info("compensation $claimState created for claim $claim. sending to hospital")
        sendClaimState(claimState)
        log.info("compensation claimstate sent back to hospital")
        log.info("awaiting signed transaction")
        // we create some logic here to check that the final transaction is verified and that the claim in the transaction is the one we quote for.
        val signTransactionFlow = object : SignTransactionFlow(session, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                stx.verify(serviceHub, checkSufficientSignatures = false) // verify the transaction - we set checkSufficientSignatures to false because we don't sign until this stage is complete
                "the claim in the proposed output state matches the one we had received" using (stx.tx.outputsOfType<ClaimState>().first().request == claim)
            }
        }
        // we invoke the sign Transaction flow which in turn awaits the CollectSignaturesFlow above
        val stx = subFlow(signTransactionFlow)
        log.info("signed transaction received and validated. awaiting notary verification and ledger commit")
        // we then await the notary verification and commit to the database
        waitForLedgerCommit(stx.id)
        return stx
    }

    @Suspendable
    private fun sendClaimState(claimState: ClaimState) {
        session.send(claimState)
    }

    @Suspendable
    private fun receiveClaimRequest(): ClaimRequest {
        val claim = session.receive<ClaimRequest>().unwrap { claim ->
            // TODO: add security checks to ensure that we can even begin processing this claim
            requireThat {
                "we are the insurer listed" using serviceHub.myInfo.legalIdentities.contains(claim.insurer)
            }
            claim // return the claim because we've passed our checks for the payload
        }
        return claim
    }

    @Suspendable
    private fun calculateCompensation(hospital: Party, claim: ClaimRequest): ClaimState {
        // TODO: something more elaborate here to determine exactly how much the insurer is willing to cover, if any at all.
        return ClaimState(hospital, claim, claim.amount)
    }
}


