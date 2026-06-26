package me.yin.simpleworld

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.yin.simpleworld.world.command.SimpleWorldCommand
import me.yin.simpleworld.world.manager.WorldManager
import me.yin.simpleworld.world.permission.WorldPermissions
import org.bukkit.plugin.java.JavaPlugin
import kotlin.time.Duration.Companion.seconds

class SimpleWorld : JavaPlugin() {

    var worldManager: WorldManager? = null
    var scope: CoroutineScope? = null

    // onDisable 时等待保存完成的最长时间
    @Volatile
    var shutdownTimeout = 10.seconds

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
        val permissions = WorldPermissions()
        this.scope = scope
        this.worldManager = worldManager

        scope.launch {
            try {
                worldManager.load()
                worldManager.startAutoSave()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("世界配置加载失败，禁用插件", e)
                if (isEnabled) {
                    server.globalRegionScheduler.execute(this@SimpleWorld) {
                        server.pluginManager.disablePlugin(this@SimpleWorld)
                    }
                }
            }
        }

        SimpleWorldCommand(this, logger, prefix, scope, worldManager, permissions).register()

        logger.info("Enabled {} {}", prefix, pluginMeta.version)
    }

    override fun onDisable() {
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled {} {}", prefix, pluginMeta.version)
        slF4JLogger.info("开始保存,最多等待 {}", shutdownTimeout)
        try {
            runBlocking {
                withTimeout(shutdownTimeout) { worldManager?.shutdown() }
            }
        } catch (e: TimeoutCancellationException) {
            slF4JLogger.error("shutdown 超时 {},放弃", shutdownTimeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            slF4JLogger.error("关闭失败", e)
        }
        scope?.cancel()
        this.scope = null
        this.worldManager = null
    }

    fun globalRegionScheduler(block: () -> Unit) {
        if (!isEnabled) {
            block()
            return
        }
        server.globalRegionScheduler.execute(this) { block() }
    }
}
