package net.cordaclub.marge

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User

fun main(args: Array<String>) {
    // No permissions required as we are not invoking flows.
    val user = User("user1", "test", permissions = setOf("ALL"))
    driver(DriverParameters(isDebug = true, waitForAllNodesToFinish = true, startNodesInProcess = true, extraCordappPackagesToScan = listOf("io.cordite"))) {
        val (hospital, insurer1, insurer2, bank) = listOf(
                startNode(providedName = CordaX500Name("Fixalot Hospital", "London", "GB"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("General Insurer", "Delhi", "IN"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("Frugal Insurer", "Tokyo", "JP"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("Kaching! Bank", "Paris", "FR"), rpcUsers = listOf(user))
        ).map { it.getOrThrow() }

        val patient = Patient(name = "John Doe", nino = "AB123456B")
        val treatment = Treatment(patient, "serious disease", hospital.nodeInfo.legalIdentities.first())
        val estimation = TreatmentCoverageEstimation(treatment, 1000.POUNDS)

        val estimationTx = hospital.rpc.startFlow(::RetrieveInsurerQuotesFlow, estimation, listOf(insurer1.nodeInfo.legalIdentities.first(), insurer2.nodeInfo.legalIdentities.first())).returnValue.getOrThrow()
        val quote = estimationTx.coreTransaction.outRefsOfType<InsurerQuoteState>()[0]

        println("Successfully got quote: ${quote.state.data.maxCoveredValue} from: ${quote.state.data.insurer}")

        hospital.rpc.vaultTrack(TreatmentState::class.java).updates.subscribe { vaultUpdate ->
            println("Produced: ${vaultUpdate.produced}")
            println("Consumed: ${vaultUpdate.consumed}")
        }

        //todo Fuzz - monitor the tokens
        hospital.rpc.startFlow(::TreatmentPaymentFlow, treatment, 1500.POUNDS, quote).returnValue.getOrThrow()

        println("Successfully payed for the treatment.")

//        startWebserver(partyA)
//        startWebserver(partyB)
    }
}
