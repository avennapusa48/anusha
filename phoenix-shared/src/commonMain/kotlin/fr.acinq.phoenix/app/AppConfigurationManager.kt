package fr.acinq.phoenix.app

import fr.acinq.eclair.blockchain.electrum.ElectrumClient
import fr.acinq.eclair.blockchain.electrum.HeaderSubscriptionResponse
import fr.acinq.phoenix.data.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.kodein.db.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, ExperimentalStdlibApi::class)
class AppConfigurationManager(
    private val appDB: DB,
    private val electrumClient: ElectrumClient,
    private val chain: Chain,
    loggerFactory: LoggerFactory
) : CoroutineScope by MainScope() {

    private val logger = newLogger(loggerFactory)

    private val electrumServerUpdates = ConflatedBroadcastChannel<ElectrumServer>()
    fun openElectrumServerUpdateSubscription(): ReceiveChannel<ElectrumServer> =
        electrumServerUpdates.openSubscription()

    /*
        TODO Manage updates for connection configurations:
            e.g. for Electrum Server : reconnect to new server
     */
    init {
        appDB.on<ElectrumServer>().register {
            didPut {
                launch { electrumServerUpdates.send(it) }
            }
        }
        launch {
            electrumClient.openNotificationsSubscription().consumeAsFlow()
                .filterIsInstance<HeaderSubscriptionResponse>().collect { notification ->
                    if (getElectrumServer().blockHeight == notification.height &&
                        getElectrumServer().tipTimestamp == notification.header.time
                    ) return@collect

                    putElectrumServer(
                        getElectrumServer().copy(
                            blockHeight = notification.height,
                            tipTimestamp = notification.header.time
                        )
                    )
                }
        }
    }

    // Electrum
    private val electrumServerKey = appDB.key<ElectrumServer>(0)
    private fun createElectrumConfiguration(): ElectrumServer {
        if (appDB[electrumServerKey] == null) {
            logger.info { "Create ElectrumX configuration" }
            setRandomElectrumServer()
        }
        return appDB[electrumServerKey] ?: error("ElectrumServer must be initialized.")
    }

    fun getElectrumServer(): ElectrumServer = appDB[electrumServerKey] ?: createElectrumConfiguration()

    private fun putElectrumServer(electrumServer: ElectrumServer) {
        logger.info { "Update electrum configuration [$electrumServer]" }
        appDB.put(electrumServerKey, electrumServer)
    }

    fun putElectrumServerAddress(host: String, port: Int, customized: Boolean = false) {
        putElectrumServer(getElectrumServer().copy(host = host, port = port, customized = customized))
    }

    fun setRandomElectrumServer() {
        putElectrumServer(
            when (chain) {
                Chain.MAINNET -> electrumMainnetConfigurations.random()
                Chain.TESTNET -> electrumTestnetConfigurations.random()
                Chain.REGTEST -> platformElectrumRegtestConf()
            }.asElectrumServer()
        )
    }
}