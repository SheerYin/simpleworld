package me.yin.simpleworld.world.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.WorldManager

class ReloadCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("reload")
            .requires { support.hasPermission(it, support.permissionReload) }
            .executes { context ->
                val sender = context.source.sender
                worldManager.tryLoadWorlds()
                sender.sendMessage(support.prefixMessage("重新加载已提交"))
                return@executes 1
            }
    }
}
