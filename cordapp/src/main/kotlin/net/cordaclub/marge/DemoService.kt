package net.cordaclub.marge

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.corda.router.Routers
import io.cordite.dgl.corda.impl.LedgerApiImpl
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.handler.StaticHandler
import net.corda.core.node.AppServiceHub
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import net.cordaclub.marge.bank.BankAPI
import net.cordaclub.marge.hospital.HospitalAPI
import net.cordaclub.marge.insurer.InsurerAPI
import java.awt.Desktop
import java.net.URI

@CordaService
class DemoService(private val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        private val log = loggerFor<DemoService>()
    }

    //this is an ugly hack
    lateinit var service: Initializer

    private val vertx = Vertx.vertx()
    private var port: Int = 0
    private val ledger = LedgerApiImpl(serviceHub)

    init {
        log.info("Starting DemoService for ${serviceHub.myInfo.legalIdentities.first().name.organisation}")
        serviceHub.myInfo.apply {
            when {
                this.isOfNodeType("insurer") -> configureInsurer()
                this.isOfNodeType("bank") -> configureBank()
                this.isOfNodeType("hospital") -> configureHospital()
                else -> configureOtherNode()
            }
        }
    }

    private fun configureHospital() {
        service = HospitalAPI(serviceHub)
        val name = serviceHub.myInfo.legalIdentities.first().name
        port = 9000 + name.organisation.hashCode() % 2
        log.info("Starting Hospital $name on port http://localhost:$port")
        val static = StaticHandler.create("web/hospital").setCachingEnabled(false)
        val router = Routers.create(vertx, port)
        router.get("/*").order(10000).handler(static)
        BraidConfig()
            .withVertx(vertx)
            .withPort(port)
            .withHttpServerOptions(HttpServerOptions().setSsl(false))
            .withService("hospital", service)
            .withService("ledger", ledger)
            .bootstrapBraid(serviceHub)
    }

    private fun configureBank() {
        service = BankAPI(serviceHub, Patients.allPatients, ledger)
        val name = serviceHub.myInfo.legalIdentities.first().name
        port = 7000 + name.organisation.hashCode() % 2
        log.info("Starting Bank $name on port http://localhost:$port")
        val static = StaticHandler.create("web/bank").setCachingEnabled(false)
        val router = Routers.create(vertx, port)
        router.get("/*").order(10000).handler(static)
        BraidConfig()
            .withVertx(vertx)
            .withPort(port)
            .withHttpServerOptions(HttpServerOptions().setSsl(false))
            .withService("bank", service)
            .withService("ledger", service)
            .bootstrapBraid(serviceHub)
    }

    private fun configureInsurer() {
        service = InsurerAPI(serviceHub)
        val name = serviceHub.myInfo.legalIdentities.first().name
        port = 8000 + name.organisation.hashCode() % 2
        log.info("Starting Insurer $name on port http://localhost:$port")
        val static = StaticHandler.create("web/insurer").setCachingEnabled(false)
        val router = Routers.create(vertx, port)
        router.get("/*").order(10000).handler(static)
        BraidConfig()
            .withVertx(vertx)
            .withPort(port)
            .withHttpServerOptions(HttpServerOptions().setSsl(false))
            .withService("insurer", service)
            .withService("ledger", ledger)
            .bootstrapBraid(serviceHub)
    }

    fun openWebPage() {
        Desktop.getDesktop().browse(URI("http://localhost:$port"))
    }

    private fun NodeInfo.isOfNodeType(name: String): Boolean {
        return legalIdentities.any { it.name.organisation.contains(name, true) }
    }

    private fun configureOtherNode() {
        log.info("unknown node type for ${serviceHub.myInfo.legalIdentities.first().name.organisation} - not configuring any services")
    }
}