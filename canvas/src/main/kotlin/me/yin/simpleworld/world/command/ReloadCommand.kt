package me.yin.simpleworld.world.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.WorldManager
import me.yin.simpleworld.world.permission.WorldPermissions

class ReloadCommand(
    private val support: CommandSupport,
    private val permissions: WorldPermissions,
    private val worldManager: WorldManager,
) {

    fun reloadBuilder(name: String = "reload"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { it.sender.hasPermission(permissions.reloadCommand) }
            .executes { context ->
                val sender = context.source.sender
                worldManager.tryLoadWorlds()
                sender.sendMessage(support.prefixMessage("重新加载已提交"))
                return@executes 1
            }
    }
}
