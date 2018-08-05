package net.cordaclub.marge

import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowTests {

    private val network = MockNetwork(listOf("net.cordaclub.marge"))
    private val hospitalNode = network.createNode()
    private val insurerNode = network.createNode()

    init {
        listOf(insurerNode).forEach {
            it.registerInitiatedFlow(InsurerQuotingFlows.InsurerRespondFlow::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `dummy test`() {
//        val patient = Patient("fuzz", "NW533428A")
//        val claimRequest = TreatmentCoverageEstimation(
//                Amount(100, Currency.getInstance("GBP")),
//                patient,
//                "brain transplant")
//
//        val future = hospitalNode.startFlow(RetrieveInsurerQuotesFlow(claimRequest))
//        network.runNetwork()
//        val result = future.getOrThrow()
//        println(result)
    }
}