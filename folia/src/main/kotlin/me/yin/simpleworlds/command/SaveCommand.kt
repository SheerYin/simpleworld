package me.yin.simpleworlds.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import net.kyori.adventure.text.Component

class SaveCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("save")
            .requires { support.hasPermission(it, "simpleworlds.command.save") }
            .executes { context ->
                val sender = context.source.sender
                support.sendMessage(sender, Component.text("正在保存…"))
                worldsManager.save()
                support.sendMessage(sender, Component.text("保存完成"))
                1
            }
    }
}
