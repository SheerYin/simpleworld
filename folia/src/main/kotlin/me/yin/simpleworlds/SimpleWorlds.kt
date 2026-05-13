package me.yin.simpleworlds

import me.yin.simpleworlds.command.SimpleWorldsCommand
import me.yin.simpleworlds.configuration.WorldsConfiguration
import me.yin.simpleworlds.listener.WorldGameRuleChange
import me.yin.simpleworlds.world.WorldsManager
import org.bukkit.plugin.java.JavaPlugin

class SimpleWorlds : JavaPlugin() {

    private var worldsManager: WorldsManager? = null

    override fun onEnable() {
        val worldsConfiguration = WorldsConfiguration(this)
        val worldsManager = WorldsManager(this, worldsConfiguration)
        this.worldsManager = worldsManager
        worldsManager.load()

        val pluginManager = server.pluginManager
        pluginManager.registerEvents(WorldGameRuleChange(worldsManager), this)

        SimpleWorldsCommand(this, worldsManager).register()

        slF4JLogger.info("Enabled ${pluginMeta.name} ${pluginMeta.version}")
    }

    override fun onDisable() {
        slF4JLogger.info("Disabled ${pluginMeta.name} ${pluginMeta.version}")
        worldsManager?.save()
        worldsManager = null
    }
}
