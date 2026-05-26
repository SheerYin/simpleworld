package me.yin.simpleworld.command

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.WorldManager
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.Logger

class SimpleWorldCommand(
    private val plugin: JavaPlugin,
    private val logger: Logger,
    private val prefix: String,
    private val scope: CoroutineScope,
    private val worldManager: WorldManager,
) {

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val support = CommandSupport(plugin, logger, prefix, scope)
            val rootCommand = Commands.literal(MAIN_COMMAND)
                .requires { support.hasPermission(it, PERMISSION) }
                // Folia 不支持运行时 createWorld / unloadWorld，相关命令暂不注册
                // .then(CreateCommand(support, worldManager).root())
                // .then(LoadCommand(support, worldManager).root())
                // .then(UnloadCommand(support, worldManager).root())
                .then(ReloadCommand(support, worldManager).root())
                .then(SaveCommand(support, worldManager).root())
                .then(TeleportCommand(support).root())
                .then(SetSpawnCommand(support).root())
                .then(ListCommand(support, worldManager).root())
                .build()
            event.registrar().register(rootCommand, "SimpleWorld commands", COMMAND_ALIASES)
        }
    }

    companion object {
        const val MAIN_COMMAND = "simpleworld"
        const val PERMISSION = "simpleworld.command"
        val COMMAND_ALIASES = listOf("sw")
    }
}
