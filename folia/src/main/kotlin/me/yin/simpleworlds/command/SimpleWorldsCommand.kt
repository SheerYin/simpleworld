package me.yin.simpleworlds.command

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsService
import me.yin.simpleworlds.world.WorldsStore
import org.bukkit.plugin.java.JavaPlugin

class SimpleWorldsCommand(
    private val plugin: JavaPlugin,
    private val worldsStore: WorldsStore,
    private val worldsService: WorldsService,
) {

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val support = CommandSupport(plugin)
            val rootCommand = Commands.literal(MAIN_COMMAND)
                .requires { support.hasPermission(it, PERMISSION) }
                // Folia 不支持运行时 createWorld / unloadWorld，相关命令暂不注册
                // .then(CreateCommand(support, worldsStore, worldsService).root())
                // .then(LoadCommand(support, worldsStore, worldsService).root())
                // .then(UnloadCommand(support, worldsStore, worldsService).root())
                .then(ReloadCommand(support, worldsStore).root())
                .then(SaveCommand(support, worldsStore).root())
                .then(TeleportCommand(support).root())
                .then(TeleportLocationCommand(support).root())
                .then(ListCommand(support, worldsStore).root())
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
