package me.yin.simpleworld

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.yin.simpleworld.command.SimpleWorldCommand
import me.yin.simpleworld.world.WorldManager
import org.bukkit.plugin.java.JavaPlugin
import kotlin.time.Duration.Companion.seconds

class SimpleWorld : JavaPlugin() {

    var worldManager: WorldManager? = null
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
        val worldManager = WorldManager(this, logger, json, scope)
        this.scope = scope
        this.worldManager = worldManager

        runBlocking { worldManager.load() }
        worldManager.startAutoSave()

        SimpleWorldCommand(this, logger, prefix, scope, worldManager).register()

        logger.info("Enabled $prefix ${pluginMeta.version}")
    }

    override fun onDisable() {
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled $prefix ${pluginMeta.version}")
        slF4JLogger.info("开始保存,最多等待 $SHUTDOWN_TIMEOUT")
        try {
            runBlocking {
                withTimeout(SHUTDOWN_TIMEOUT) { worldManager?.shutdown() }
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
        this.worldManager = null
    }

    companion object {
        val SHUTDOWN_TIMEOUT = 10.seconds
    }
}
