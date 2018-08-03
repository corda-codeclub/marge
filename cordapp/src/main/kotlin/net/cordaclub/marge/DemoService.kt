package net.cordaclub.marge

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.corda.router.Routers
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.handler.StaticHandler
import net.corda.core.node.AppServiceHub
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import net.cordaclub.marge.hospital.HospitalAPI
import net.cordaclub.marge.insurer.InsurerAPI

@CordaService
class DemoService(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        private val log = loggerFor<DemoService>()
    }

    private val vertx = Vertx.vertx()

    init {
        log.info("Starting DemoService for ${serviceHub.myInfo.legalIdentities.first().name.organisation}")
        serviceHub.myInfo.apply {
            when {
                this.isOfNodeType("insurer") -> configureInsurer()
//                this.isOfNodeType("bank") -> configureBank()
                this.isOfNodeType("hospital") -> configureHospital()
                else -> configureOtherNode()
            }
        }
    }

    private fun configureHospital() {
        val service = HospitalAPI(serviceHub)
        val name = serviceHub.myInfo.legalIdentities.first().name
        val port = 9000 + name.organisation.hashCode() % 2
        log.info("Starting Hospital $name on port http://localhost:$port/api")
        val static = StaticHandler.create("web/hospital").setCachingEnabled(false)
        val router = Routers.create(vertx, port)
        router.get("/*").order(10000).handler(static)
        BraidConfig()
            .withVertx(vertx)
            .withPort(port)
            .withHttpServerOptions(HttpServerOptions().setSsl(false))
            .withService("hospital", service)
            .bootstrapBraid(serviceHub)
    }

    private fun configureBank() {

    }

    private fun configureInsurer() {
        val service = InsurerAPI(serviceHub)
        val name = serviceHub.myInfo.legalIdentities.first().name
        val port = 8000 + name.organisation.hashCode() % 2
        log.info("Starting Insurer $name on port http://localhost:$port/api")
        val static = StaticHandler.create("web/insurer").setCachingEnabled(false)
        val router = Routers.create(vertx, port)
        router.get("/*").order(10000).handler(static)
        BraidConfig()
            .withVertx(vertx)
            .withPort(port)
            .withHttpServerOptions(HttpServerOptions().setSsl(false))
            .withService("insurer", service)
            .bootstrapBraid(serviceHub)
    }

    private fun NodeInfo.isOfNodeType(name: String): Boolean {
        return legalIdentities.any { it.name.organisation.contains(name, true) }
    }

    private fun configureOtherNode() {
        log.info("unknown node type for ${serviceHub.myInfo.legalIdentities.first().name.organisation} - not configuring any services")
    }
}