package me.yin.simpleworld.world.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import me.yin.simpleworld.SimpleWorld
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.WorldManager
import me.yin.simpleworld.world.permission.WorldPermissions
import org.slf4j.Logger

class SimpleWorldCommand(
    private val plugin: SimpleWorld,
    private val logger: Logger,
    private val prefix: String,
    private val scope: CoroutineScope,
    private val worldManager: WorldManager,
    private val permissions: WorldPermissions,
) {

    fun register() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(simpleWorldNode(), COMMAND_ALIASES)
        }
    }

    fun simpleWorldNode(): LiteralCommandNode<CommandSourceStack> {
        val support = CommandSupport(plugin, prefix)
        val root = Commands.literal(MAIN_COMMAND)
            .requires { it.sender.hasPermission(permissions.worldCommand) }
            .then(ReloadCommand(support, permissions, worldManager).reloadBuilder())
            .then(SaveCommand(support, permissions, worldManager).saveBuilder())
            .then(TeleportCommand(plugin, support, permissions).teleportBuilder())
            .then(SetSpawnCommand(plugin, support, permissions).setSpawnBuilder())
            .then(ListCommand(plugin, support, permissions, worldManager).listBuilder())
            .then(CreateCommand(plugin, logger, support, permissions, worldManager).createBuilder())
            .then(LoadCommand(plugin, logger, scope, support, permissions, worldManager).loadBuilder())
            .then(UnloadCommand(plugin, logger, support, permissions, worldManager).unloadBuilder())
            .then(RemoveCommand(logger, support, permissions, worldManager).removeBuilder())

        return root.build()
    }

    companion object {
        const val MAIN_COMMAND = "simpleworld"
        val COMMAND_ALIASES = listOf("sw")
    }
}
