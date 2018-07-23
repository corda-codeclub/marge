package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.security.PublicKey

// PLEASE NOTE: we could have used the CollectSignatures flow that is a frequently used flow for simple cases
// however, to give you a flavour of how Corda can be used to implement complex multi-party protocols
// this example uses slighly more lower-level APIs with flow session and send/receive invocations
// to build up the transaction and get it mutually signed

/**
 * This is run by the Hospital
 */
@StartableByRPC
@StartableByService
class RaiseClaimFlow(private val claimRequest: ClaimRequest) : FlowLogic<SignedTransaction>() {

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
  private fun createTransactionSignAndCommit(claimState: ClaimState, session: FlowSession) : SignedTransaction {
    val notary = serviceHub.networkMapCache.notaryIdentities.first() // get notary that we to use - NB this is hacky but it gets us going
    val txb = TransactionBuilder(notary) // hospital constructs the transactions
    txb.addCommand(ClaimCommand.ClaimRequestCommand()) // what type of transaction is this
    txb.addOutputState(claimState, ClaimContract.CONTRACT_ID, notary) // add the claim as the only output state of this transaction, ref the contract that will verify this transaction anytime in the future e.g. by notary
    val stx = serviceHub.signInitialTransaction(txb) // hospital signs the transaction
    session.send(stx)
    val signatures = session.receive<List<TransactionSignature>>().unwrap { it }
    val fullySigned = stx + signatures
    subFlow(FinalityFlow(fullySigned))
    return fullySigned
  }

  @Suspendable
  private fun receiveClaimState(session: FlowSession): ClaimState {
    return session.receive<ClaimState>().unwrap { it }
  }

  @Suspendable
  private fun sendClaim(session: FlowSession) {
    session.send(claimRequest)
  }
}


/**
 * This class handles the insurers side of the flow and initiated by [RaiseClaimFlow]
 */
@InitiatedBy(RaiseClaimFlow::class)
class VerifyClaimFlow(private val session: FlowSession) : FlowLogic<SignedTransaction>() {
  @Suspendable
  override fun call(): SignedTransaction {
    // who is the hospital?
    val hospital = session.counterparty

    val claim = receiveClaimRequest()

    val claimState = calculateCompensation(hospital, claim)

    sendClaimState(claimState)

    val stx = receiveSignedTransaction()
    verifySignedTransaction(stx)

    // now sign it
    val signingKeys = session.receive<List<PublicKey>>().unwrap { keys ->
      serviceHub.keyManagementService.filterMyKeys(keys)
    }
    val mySignatures = signingKeys.map { key ->
      serviceHub.createSignature(stx, key)
    }
    session.send(mySignatures)
    val fullySigned = stx + mySignatures
    waitForLedgerCommit(fullySigned.id)
    return fullySigned
  }

  @Suspendable
  private fun sendClaimState(claimState: ClaimState) {
    session.send(claimState)
  }

  @Suspendable
  private fun verifySignedTransaction(stx: SignedTransaction) {
    stx.tx.toLedgerTransaction(serviceHub).verify()
  }

  @Suspendable
  private fun receiveSignedTransaction(): SignedTransaction {
    return session.receive<SignedTransaction>().unwrap { it }
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
  private fun calculateCompensation(hospital: Party, claim: ClaimRequest) : ClaimState {
    // TODO: something more elaborate here to determine exactly how much the insurer is willing to cover, if any at all.
    return ClaimState(hospital, claim, claim.amount)
  }
}


