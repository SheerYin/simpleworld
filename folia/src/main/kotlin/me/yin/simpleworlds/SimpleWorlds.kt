package me.yin.simpleworlds

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.yin.simpleworlds.command.SimpleWorldsCommand
import me.yin.simpleworlds.world.WorldsManager
import org.bukkit.plugin.java.JavaPlugin
import kotlin.time.Duration.Companion.seconds

class SimpleWorlds : JavaPlugin() {

    var worldsManager: WorldsManager? = null
    var scope: CoroutineScope? = null

    override fun onEnable() {
        val logger = slF4JLogger
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val worldsManager = WorldsManager(this, logger, json, scope)
        this.scope = scope
        this.worldsManager = worldsManager

        runBlocking { worldsManager.load() }
        worldsManager.startAutoSave()

        SimpleWorldsCommand(this, logger, prefix, scope, worldsManager).register()

        logger.info("Enabled $prefix ${pluginMeta.version}")
    }

    override fun onDisable() {
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled $prefix ${pluginMeta.version}")
        try {
            runBlocking {
                withTimeout(SHUTDOWN_TIMEOUT) { worldsManager?.shutdown() }
            }
        } catch (e: TimeoutCancellationException) {
            slF4JLogger.error("shutdown 超时 $SHUTDOWN_TIMEOUT,放弃")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            slF4JLogger.error("关闭失败", e)
        }
        scope?.cancel()
        this.scope = null
        this.worldsManager = null
    }

    companion object {
        val SHUTDOWN_TIMEOUT = 10.seconds
    }
}
