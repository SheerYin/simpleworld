package me.yin.simpleworld.world.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.WorldManager
import me.yin.simpleworld.world.permission.WorldPermissions

class SaveCommand(
    private val support: CommandSupport,
    private val permissions: WorldPermissions,
    private val worldManager: WorldManager,
) {

    fun saveBuilder(name: String = "save"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { it.sender.hasPermission(permissions.saveCommand) }
            .executes { context ->
                val sender = context.source.sender
                worldManager.trySaveWorlds()
                sender.sendMessage(support.prefixMessage("保存已提交"))
                return@executes 1
            }
    }
}
