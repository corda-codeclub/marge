package com.template

import net.corda.core.contracts.Amount
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class FlowTests {

  private val network = MockNetwork(listOf("com.template"))
  private val hospitalNode = network.createNode()
  private val insurerNode = network.createNode()

  init {
    listOf(hospitalNode, insurerNode).forEach {
      it.registerInitiatedFlow(VerifyClaimFlow::class.java)
    }
  }

  @Before
  fun setup() = network.runNetwork()

  @After
  fun tearDown() = network.stopNodes()

  @Test
  fun `dummy test`() {
    val patient = Patient("fuzz", "NW533428A")
    val claimRequest = ClaimRequest(insurerNode.info.legalIdentities.first(),
      Amount(100, Currency.getInstance("GBP")),
      patient, "brain transplant")

    val future = hospitalNode.startFlow(RaiseClaimFlow(claimRequest))
    network.runNetwork()
    val result = future.getOrThrow()
    println(result)
  }
}