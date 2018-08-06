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
enum class TreatmentStatus {
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
        val command = tx.commands.requireSingleCommand<TreatmentCommand>()
        val setOfSigners = command.signers.toSet()
        val outputTreatment = tx.outputsOfType<TreatmentState>().single()
        val inputTreatments = tx.inputsOfType<TreatmentState>()

        requireThat {
            inputTreatments.singleOrNull()?.let {
                "The same treatment" using (it.treatment == outputTreatment.treatment)
                "The same estimatedTreatmentCost" using (it.estimatedTreatmentCost == outputTreatment.estimatedTreatmentCost)
                "The same linearId" using (it.linearId == outputTreatment.linearId)
            }
            "The hospital signed the transaction" using (setOfSigners.containsAll(outputTreatment.participants.map { it.owningKey } + inputTreatments.flatMap { it.participants.map { it.owningKey } }))
            when (command.value) {
                is TreatmentCommand.EstimateTreatment -> {
                    "No inputs should be consumed when estimating a treatment." using (inputTreatments.isEmpty())
                    "The output status is correct" using (outputTreatment.treatmentStatus == TreatmentStatus.ESTIMATED)
                }
                is TreatmentCommand.QuoteTreatment -> {
                    "The input status is correct" using (inputTreatments.single().treatmentStatus == TreatmentStatus.ESTIMATED)
                    "The output status is correct" using (outputTreatment.treatmentStatus == TreatmentStatus.QUOTED)
                    "The insurer signed the transaction" using (setOfSigners.contains(outputTreatment.insurerQuote!!.insurer.owningKey))
                    "The estimated value is greater or equal than the quote" using (outputTreatment.estimatedTreatmentCost >= outputTreatment.insurerQuote!!.maxCoveredValue)
                }
                is TreatmentCommand.FinaliseTreatment -> {
                    "The input status is correct" using (inputTreatments.single().treatmentStatus == TreatmentStatus.QUOTED)
                    "The output status is correct" using (outputTreatment.treatmentStatus == TreatmentStatus.FINALISED)
                    "The actual cost of treatment is set" using (outputTreatment.treatmentCost != null)
                }
                is TreatmentCommand.CollectInsurerPay -> {
                    "The input status is correct" using (inputTreatments.single().treatmentStatus == TreatmentStatus.FINALISED)
                    "The output status is correct" using (outputTreatment.treatmentStatus == TreatmentStatus.PARTIALLY_PAYED)
                    "The amount payed is less than the cost" using (outputTreatment.amountPayed!! <= outputTreatment.treatmentCost!!)
                    "The amount payed is correct" using (outputTreatment.amountPayed!! == min(outputTreatment.insurerQuote!!.maxCoveredValue, outputTreatment.treatmentCost))
                }
                is TreatmentCommand.FullyPayTreatment -> {
                    "The input status is correct" using (inputTreatments.single().treatmentStatus == TreatmentStatus.PARTIALLY_PAYED)
                    "The output status is correct" using (outputTreatment.treatmentStatus == TreatmentStatus.FULLY_PAYED)
                    "There is nothing left to pay" using (outputTreatment.amountPayed!! == outputTreatment.treatmentCost!!)
                }
            }
        }
    }
}


// Utility
fun <T : Comparable<T>> min(v1: T, v2: T): T = if (v1 < v2) v1 else v2
