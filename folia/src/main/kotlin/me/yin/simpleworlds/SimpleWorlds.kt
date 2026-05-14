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

    private var worldsStore: WorldsStore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    override fun onEnable() {
        val worldsStore = WorldsStore(this, json, scope)
        val worldsService = WorldsService(this, worldsStore)
        this.worldsStore = worldsStore

        worldsStore.load()
        worldsStore.start()

        SimpleWorldsCommand(this, worldsStore, worldsService).register()

        slF4JLogger.info("Enabled ${pluginMeta.name} ${pluginMeta.version}")
    }

    override fun onDisable() {
        slF4JLogger.info("Disabled ${pluginMeta.name} ${pluginMeta.version}")
        val worldsStore = this.worldsStore
        if (worldsStore != null) {
            runBlocking { worldsStore.shutdown() }
        }
        this.worldsStore = null
        scope.cancel()
    }
}
