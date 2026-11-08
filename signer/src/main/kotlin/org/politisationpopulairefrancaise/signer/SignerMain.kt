package org.politisationpopulairefrancaise.signer

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("ppf-signer")

/** How often the service checks its keys (rotation, end of a certificate, revocation already effective) and drafts. */
private const val MAINTENANCE_INTERVAL_MILLIS: Long = 5 * 60 * 1000L

/**
 * Starts the signing service (D-129): `java -jar ppf-signer.jar`, configured by environment variables
 * ([SignerConfig]). It refuses to start on an invalid configuration, an unreadable key file or a broken
 * journal, with a message that never quotes a secret.
 *
 * Two listeners, two separate applications that share nothing but the service: the internal API for the
 * server on `PPF_SIGNER_PORT` ([signerModule]), and the approval pages on `PPF_SIGNER_APPROVAL_PORT`
 * ([approvalModule]). No route of one exists on the other.
 */
fun main() {
    val config = try {
        SignerConfig.fromEnvironment()
    } catch (e: SignerConfigException) {
        System.err.println("ppf-signer: ${e.message}")
        exitProcess(2)
    }
    val service = try {
        SignerService.open(config)
    } catch (e: SignerStartupException) {
        System.err.println("ppf-signer: ${e.message}")
        exitProcess(3)
    }
    val token = ClientToken(config.clientToken)
    log.info(
        "Signing service started on {}:{} and approval pages on {}:{} ({}; root {})",
        config.host, config.port, config.host, config.approvalPort, config.approvalOrigin,
        if (config.rootPublicKey == null) "not configured: nothing is signed" else "configured",
    )
    embeddedServer(Netty, host = config.host, port = config.approvalPort) {
        approvalModule(service, config)
    }.start(wait = false)
    embeddedServer(Netty, host = config.host, port = config.port) {
        signerModule(service, token)
        launch(Dispatchers.IO) {
            while (isActive) {
                delay(MAINTENANCE_INTERVAL_MILLIS)
                try {
                    service.maintain()
                } catch (e: Exception) {
                    log.error("Maintenance of the keys failed: {}", e.javaClass.simpleName)
                }
            }
        }
    }.start(wait = true)
}
