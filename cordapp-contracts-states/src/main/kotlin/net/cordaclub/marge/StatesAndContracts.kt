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

@CordaSerializable
data class InsurerQuote(
        val insurer: Party,
        val maxCoveredValue: Amount<Currency>
)

@CordaSerializable
enum class TreatmentStatus{
    ESTIMATED, QUOTED, FINALISED, PARTIALLY_PAYED, FULLY_PAYED
}

/**
 * This is the main state that models a treatment from estimation to payment
 */
class TreatmentState(
        val treatment: Treatment,
        val estimatedTreatmentCost: Amount<Currency>,
        val treatmentCost: Amount<Currency>?,
        val amountPayed: Amount<Currency>?,
        val insurerQuote: InsurerQuote?,
        val treatmentStatus: TreatmentStatus,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty>
        get() = listOf(treatment.hospital)

    override fun toString(): String {
        return "TreatmentState(treatment=$treatment, treatmentCost=$treatmentCost)"
    }
}

sealed class TreatmentCommand : TypeOnlyCommandData() {
    class EstimateTreatment : TreatmentCommand()
    class QuoteTreatment : TreatmentCommand()
    class FinaliseTreatment : TreatmentCommand()
    class CollectInsurerPay : TreatmentCommand()
    class FullyPayTreatment : TreatmentCommand()
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
