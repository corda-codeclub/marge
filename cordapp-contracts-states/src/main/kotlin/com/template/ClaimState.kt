package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.util.*

/**
 * A patient
 */
data class Patient(val name: String, val id: String)

/**
 * A request to process a claim
 */
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
    requireThat {
      // TODO: checks to determine if the transaction is well formed - N.B. this is after a transaction is formed.
      // In our example, there are additional check that happen within the flow
    }
  }
}

