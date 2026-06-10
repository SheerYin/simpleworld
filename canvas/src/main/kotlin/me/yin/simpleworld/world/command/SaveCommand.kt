package me.yin.simpleworld.world.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.WorldManager

class SaveCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("save")
            .requires { support.hasPermission(it, support.permissionSave) }
            .executes { context ->
                val sender = context.source.sender
                worldManager.trySaveWorlds()
                sender.sendMessage(support.prefixMessage("保存已提交"))
                return@executes 1
            }
    }
}
