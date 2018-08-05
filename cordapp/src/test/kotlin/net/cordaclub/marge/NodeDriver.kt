package net.cordaclub.marge

import io.cordite.dgl.corda.account.AccountAddress
import io.cordite.dgl.corda.account.CreateAccountFlow
import io.cordite.dgl.corda.token.CreateTokenTypeFlow
import io.cordite.dgl.corda.token.Token
import io.cordite.dgl.corda.token.TokenType
import io.cordite.dgl.corda.token.flows.IssueTokenFlow
import io.cordite.dgl.corda.token.flows.TransferTokenFlow
import io.cordite.dgl.corda.token.issuedBy
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.InProcessImpl
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import java.math.BigDecimal

class NodeDriver {
    companion object {
        private val log = loggerFor<NodeDriver>()

        @JvmStatic
        fun main(args: Array<String>) {
            val hospitalName = CordaX500Name("Fixalot Hospital", "London", "GB")
            val bankName = CordaX500Name("Kaching! Bank", "Paris", "FR")
            val notaryName = CordaX500Name("Notary", "London", "GB")
            // No permissions required as we are not invoking flows.
            val user = User("user1", "test", permissions = setOf("ALL"))
            driver(DriverParameters(
                isDebug = true,
                waitForAllNodesToFinish = true,
                startNodesInProcess = true,
                notarySpecs = listOf(NotarySpec(notaryName, false, listOf(user))),
                extraCordappPackagesToScan = listOf("io.cordite")
            )) {

                val hospital = startNode(providedName = hospitalName, rpcUsers = listOf(user)).getOrThrow()
                val bank = startNode(providedName = bankName, rpcUsers = listOf(user)).getOrThrow()

                val hospitalProxy = CordaRPCClient(hospital.rpcAddress).start(user.username, user.password).proxy
                val bankProxy = CordaRPCClient(bank.rpcAddress).start(user.username, user.password).proxy

                hospitalProxy.startFlow(::CreateAccountFlow, listOf(CreateAccountFlow.Request("hospital")), hospitalProxy.notaryIdentities().first()).returnValue.getOrThrow()

                bankProxy.startFlow(::CreateAccountFlow, listOf(CreateAccountFlow.Request("bank")), bankProxy.notaryIdentities().first()).returnValue.getOrThrow()
                val tokenType = bankProxy.startFlow(::CreateTokenTypeFlow, "GBP", 2, hospitalProxy.notaryIdentities().first()).returnValue.getOrThrow().tx.outputStates.first() as TokenType.State
                val descriptor = tokenType.descriptor
                val issuer = bankProxy.nodeInfo().legalIdentities.first()
                val amount = Amount.fromDecimal(BigDecimal(100), descriptor)

                val token = Token.generateIssuance(amount.issuedBy(issuer.ref(0)), "bank", issuer, "issuance")
                bankProxy.startFlow(::IssueTokenFlow, token, bankProxy.notaryIdentities().first(), "issuance").returnValue.getOrThrow()
                val srcAddress = AccountAddress("bank", bankName)
                val destAddress = AccountAddress("hospital", hospitalName)
                val transfer = bankProxy.startFlow(::TransferTokenFlow, srcAddress, destAddress, Amount.fromDecimal(BigDecimal(1), descriptor), "transfer", bankProxy.notaryIdentities().first()).returnValue.getOrThrow()
                println(transfer)
//                bankProxy.startFlow(::IssueTokenFlow, )

//        val insurer1 = startNode(providedName = CordaX500Name("General Insurer", "Delhi", "IN"), rpcUsers = listOf(user)).getOrThrow()
//        val insurer2 = startNode(providedName = CordaX500Name("Frugal Insurer", "Tokyo", "JP"), rpcUsers = listOf(user)).getOrThrow()

//                val nodes : List<NodeHandle> = listOf(hospital, bank)
//
//                bank.
//                // chain the initialisers in a sequence
//                nodes.fold(Future.succeededFuture<Unit>()) { acc, nh ->
//                    acc.compose { nh.getInitializer().initialiseDemo() }
//                }.onSuccess {
//                    log.info("nodes started - opening webpages")
//                    nodes.forEach { it.openWebPage() }
//                }.onFail {
//                    log.error("failed to start app", it)
//                }

//        listOf(hospital, insurer1, bank).map { it.getInitializer().initialiseDemo() }.forEach { if (!it.isComplete) it.complete() }

//        val patient = Patients.allPatients.first()
//        val treatment = Treatment(patient, "serious disease", hospital.nodeInfo.legalIdentities.first())
//        val estimation = TreatmentCoverageEstimation(treatment, 1000.POUNDS)
//
////        val estimationTx = hospital.rpc.startFlow(::RetrieveInsurerQuotesFlow, estimation, listOf(insurer1.nodeInfo.legalIdentities.first(), insurer2.nodeInfo.legalIdentities.first())).returnValue.getOrThrow()
//        val estimationTx = hospital.rpc.startFlow(::RetrieveInsurerQuotesFlow, estimation, listOf(insurer1.nodeInfo.legalIdentities.first())).returnValue.getOrThrow()
//        val quote = estimationTx.coreTransaction.outRefsOfType<InsurerQuoteState>()[0]
//
//        println("Successfully got quote: ${quote.state.data.maxCoveredValue} from: ${quote.state.data.insurer}")
//
//        hospital.rpc.vaultTrack(TreatmentState::class.java).updates.subscribe { vaultUpdate ->
//            println("Produced: ${vaultUpdate.produced}")
//            println("Consumed: ${vaultUpdate.consumed}")
//        }
//
//        hospital.rpc.startFlow(::TreatmentPaymentFlow, treatment, 1500.POUNDS, quote).returnValue.getOrThrow()
//
//        println("Successfully payed for the treatment.")

//        startWebserver(hospital)
//        startWebserver(insurer1)
//        startWebserver(insurer2)
//        startWebserver(bank)
            }
        }
    }
}

fun NodeHandle.getInitializer() = (this as InProcessImpl).services.cordaService(DemoService::class.java).service
fun NodeHandle.openWebPage() = (this as InProcessImpl).services.cordaService(DemoService::class.java).openWebPage()