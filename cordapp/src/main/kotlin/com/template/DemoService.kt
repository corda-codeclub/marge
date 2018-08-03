package com.template

import net.corda.core.node.AppServiceHub
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor

@CordaService
class DemoService(serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        private val log = loggerFor<DemoService>()
    }

    init {
        serviceHub.myInfo.apply {
            when {
                this.isOfNodeType("insurer") -> configureInsurer()
                this.isOfNodeType("bank") -> configureBank()
                this.isOfNodeType("hospital") -> configureHospital()
                else -> configureOtherNode()
            }
        }
    }

    private fun configureOtherNode() {

    }

    private fun configureHospital() {

    }

    private fun configureBank() {

    }

    private fun configureInsurer() {

    }

    private fun NodeInfo.isOfNodeType(name: String): Boolean {
        return legalIdentities.any { it.name.organisation.contains(name, true) }
    }
}