package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.util.*

/**
 * A patient
 */
@CordaSerializable
data class Patient(val name: String, val id: String)

/**
 * A request to process a claim
 */
@CordaSerializable
data class ClaimRequest(val insurer: Party,
                        val amount: Amount<Currency>,
                        val patient: Patient,
                        val description: String)

/**
 * The final state generated for the transaction that will contain the claim request, the originating hospital
 * and the cover provided
 */
class ClaimState(
  val hospital: Party,
  val request: ClaimRequest,
  val cover: Amount<Currency>
) : ContractState {
  override val participants: List<AbstractParty>
    get() = listOf(hospital, request.insurer)
}


/**
 * The set of commands representing the type of flows we're providing
 * n.b 'sealed' - means no one else can inherit from this to create their flows
 * TypeOnlyCommandData - because these commands are not carrying any typed data - they are in-effect just flags.
 */
sealed class ClaimCommand : TypeOnlyCommandData() {
  class ClaimRequestCommand : ClaimCommand()
}


/**
 * This contract checks the validity of a transaction with a ClaimState output
 */
class ClaimContract : Contract {
  companion object {
    val CONTRACT_ID : ContractClassName = ClaimContract::class.qualifiedName!!
  }
  override fun verify(tx: LedgerTransaction) {
    val claimStates = tx.outputsOfType<ClaimState>()
    requireThat {
      // TODO: much more checks can be placed here
      "we must have one and only one claim state" using (claimStates.size == 1)
      val claimState = claimStates.first()
      "that amount being paid out is less than or equal to the claim request" using (claimState.cover <= claimState.request.amount)
      "that there are no inputs states" using tx.inputStates.isEmpty()
    }
  }
}

