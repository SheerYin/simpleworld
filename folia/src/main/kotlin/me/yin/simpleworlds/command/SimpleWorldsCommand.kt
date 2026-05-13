package me.yin.simpleworlds.command

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import org.bukkit.plugin.java.JavaPlugin

class SimpleWorldsCommand(
    private val plugin: JavaPlugin,
    private val worldsManager: WorldsManager
) {

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val support = CommandSupport(plugin)
            val rootCommand = Commands.literal(MAIN_COMMAND)
                .requires { support.hasPermission(it, PERMISSION) }
                // Folia 不支持运行时 createWorld / unloadWorld，相关命令暂不注册
                // .then(CreateCommand(support, worldsManager).root())
                // .then(LoadCommand(support, worldsManager).root())
                // .then(UnloadCommand(support, worldsManager).root())
                .then(ReloadCommand(support, worldsManager).root())
                .then(SaveCommand(support, worldsManager).root())
                .then(TeleportCommand(support, worldsManager).root())
                .then(TeleportLocationCommand(support, worldsManager).root())
                .then(ListCommand(support, worldsManager).root())
                .build()
            event.registrar().register(rootCommand, "SimpleWorlds commands", COMMAND_ALIASES)
        }
    }

    companion object {
        const val MAIN_COMMAND = "simpleworlds"
        const val PERMISSION = "simpleworlds.command"
        val COMMAND_ALIASES = listOf("sw")
    }
}
