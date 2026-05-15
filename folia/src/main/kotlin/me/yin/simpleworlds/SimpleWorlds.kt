package me.yin.simpleworlds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.yin.simpleworlds.command.SimpleWorldsCommand
import me.yin.simpleworlds.world.WorldsService
import me.yin.simpleworlds.world.WorldsStore
import org.bukkit.plugin.java.JavaPlugin

class SimpleWorlds : JavaPlugin() {

    var worldsStore: WorldsStore? = null
    var worldsService: WorldsService? = null
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
        val worldsStore = WorldsStore(this, logger, json, scope)
        val worldsService = WorldsService(this, worldsStore)
        this.scope = scope
        this.worldsStore = worldsStore
        this.worldsService = worldsService

        runBlocking { worldsStore.load() }
        worldsStore.startAutoSave()

        SimpleWorldsCommand(this, logger, prefix, scope, worldsStore, worldsService).register()

        logger.info("Enabled $prefix ${pluginMeta.version}")
    }

    override fun onDisable() {
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled $prefix ${pluginMeta.version}")
        try {
            runBlocking { worldsStore?.shutdown() }
        } catch (e: Throwable) {
            slF4JLogger.error("关闭失败", e)
        }
        scope?.cancel()
        this.scope = null
        this.worldsStore = null
        this.worldsService = null
    }
}
