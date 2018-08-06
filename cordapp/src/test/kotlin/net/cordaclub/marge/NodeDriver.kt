package net.cordaclub.marge

import io.vertx.core.Future
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.InProcessImpl
import net.corda.testing.node.User
import net.cordaclub.marge.InsurerQuotingFlows.RetrieveInsurerQuotesFlow
import net.cordaclub.marge.Insurers.allInsurers
import net.cordaclub.marge.util.onFail
import net.cordaclub.marge.util.onSuccess
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("NodeDriver")

    // No permissions required as we are not invoking flows.
    val users = listOf(User("user1", "test", permissions = setOf("ALL")))

    // the ordering here is important - we want all the respective nodes to setup their accounts before we start issuing currency
    val names = listOf(CordaX500Name("Fixalot Hospital", "London", "GB")) + allInsurers + listOf(CordaX500Name("Kaching! Bank", "Paris", "FR"))

    driver(DriverParameters(isDebug = true, waitForAllNodesToFinish = true, startNodesInProcess = true, extraCordappPackagesToScan = listOf("io.cordite"))) {

        val nodes = names.map {
            startNode(providedName = it, rpcUsers = users)
        }.map { it.getOrThrow() }

        val hospital = nodes.first()
        val insurers = nodes.drop(1).dropLast(1)

        log.info("initialing node state")

        nodes.fold(Future.succeededFuture<Unit>()) { acc, node ->
            acc.compose { node.getInitializer().initialiseDemo() }
        }.onSuccess {
            log.info("nodes initialised")
            nodes.forEach { it.openWebPage() }
        }.onFail { err ->
            log.error("failed to initalise nodes", err)
        }

        val patient = Patients.allPatients.first()
        val treatment = Treatment(patient, "serious disease", hospital.nodeInfo.legalIdentities.first())
        val estimation = TreatmentCoverageEstimation(treatment, 1000.POUNDS)

        val estimationTx = hospital.rpc.startFlow(::RetrieveInsurerQuotesFlow, estimation, insurers.map { it.nodeInfo.legalIdentities.first() }).returnValue.getOrThrow()
        val treatmentState = estimationTx.coreTransaction.outRefsOfType<TreatmentState>()[0]
        val quote = treatmentState.state.data.insurerQuote!!

        println("Successfully got quote: ${quote.maxCoveredValue} from: ${quote.insurer}")

        hospital.rpc.vaultTrack(TreatmentState::class.java).updates.subscribe { vaultUpdate ->
            println("Produced: ${vaultUpdate.produced}")
            println("Consumed: ${vaultUpdate.consumed}")
        }

        hospital.rpc.startFlow(::TriggerTreatmentPaymentsFlow, treatmentState, 1500.POUNDS).returnValue.getOrThrow()

        println("Successfully payed for the treatment.")

//        startWebserver(hospital)
//        startWebserver(insurer1)
//        startWebserver(insurer2)
//        startWebserver(bank)
    }
}

fun NodeHandle.getInitializer() = (this as InProcessImpl).services.cordaService(DemoService::class.java).service
fun NodeHandle.openWebPage() = (this as InProcessImpl).services.cordaService(DemoService::class.java).openWebPage()