package net.cordaclub.marge

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.util.*

@CordaSerializable
data class Patient(val name: String, val nino: String)

@CordaSerializable
data class Treatment(
        val patient: Patient,
        val description: String,
        val hospital: Party
)

/**
 * Initial request sent to insurers to get a coverage estimation
 */
@CordaSerializable
data class TreatmentCoverageEstimation(
        val treatment: Treatment,
        val estimatedAmount: Amount<Currency>
)

/**
 * The official quote signed by insurers.
 */
class InsurerQuoteState(
        val request: TreatmentCoverageEstimation,
        val insurer: Party,
        val maxCoveredValue: Amount<Currency>
) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(request.treatment.hospital, insurer)
}

sealed class InsurerQuoteCommand : TypeOnlyCommandData() {
    class IssueQuote : InsurerQuoteCommand()
    class RedeemQuote : InsurerQuoteCommand()
}

/**
 * This contract checks the validity of a transaction with a [InsurerQuoteState] output
 */
class InsurerQuoteContract : Contract {
    companion object {
        val CONTRACT_ID: ContractClassName = InsurerQuoteContract::class.qualifiedName!!
    }

    override fun verify(tx: LedgerTransaction) {
//        val claimStates = tx.outputsOfType<InsurerQuoteState>()
        requireThat {
            // TODO: much more checks can be placed here
//            "we must have one and only one claim state" using (claimStates.size == 1)
//            val claimState = claimStates.first()
//            "that amount being paid out is less than or equal to the claim request" using (claimState.cover <= claimState.request.amount)
//            "that there are no inputs states" using tx.inputStates.isEmpty()
        }
    }
}


/**
 * Once the treatment is over, the hospital issues an official signed state with the treatment.
 */
class TreatmentState(
        val treatment: Treatment,
        val treatmentCost: Amount<Currency>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty>
        get() = listOf(treatment.hospital)

    override fun toString(): String {
        return "TreatmentState(treatment=$treatment, treatmentCost=$treatmentCost)"
    }
}

sealed class TreatmentCommand : TypeOnlyCommandData() {
    class IssueTreatment : TreatmentCommand()
    class PayTreatment : TreatmentCommand()
}

class TreatmentContract : Contract {
    companion object {
        val CONTRACT_ID: ContractClassName = TreatmentContract::class.qualifiedName!!
    }

    override fun verify(tx: LedgerTransaction) {
        //todo
    }
}


// Utility
fun <T : Comparable<T>> min(v1: T, v2: T): T = if (v1 < v2) v1 else v2
