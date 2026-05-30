package me.yin.simpleworld.command

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import me.yin.simpleworld.SimpleWorld
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.WorldManager
import org.slf4j.Logger

class SimpleWorldCommand(
    private val plugin: SimpleWorld,
    private val logger: Logger,
    private val prefix: String,
    private val scope: CoroutineScope,
    private val worldManager: WorldManager,
) {

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val support = CommandSupport(plugin, logger, prefix, scope)
            val rootBuilder = Commands.literal(MAIN_COMMAND)
                .requires { support.hasPermission(it, PERMISSION) }
                .then(ReloadCommand(support, worldManager).root())
                .then(SaveCommand(support, worldManager).root())
                .then(TeleportCommand(support).root())
                .then(SetSpawnCommand(support).root())
                .then(ListCommand(support, worldManager).root())

            if (!plugin.isFolia) {
                rootBuilder
                    .then(CreateCommand(support, worldManager).root())
                    .then(LoadCommand(support, worldManager).root())
                    .then(UnloadCommand(support, worldManager).root())
            }

            val rootCommand = rootBuilder.build()
            event.registrar().register(rootCommand, "SimpleWorld commands", COMMAND_ALIASES)
        }
    }

    companion object {
        const val MAIN_COMMAND = "simpleworld"
        const val PERMISSION = "simpleworld.command"
        val COMMAND_ALIASES = listOf("sw")
    }
}
