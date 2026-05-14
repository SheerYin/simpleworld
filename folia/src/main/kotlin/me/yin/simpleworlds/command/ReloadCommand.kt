package me.yin.simpleworlds.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsStore
import net.kyori.adventure.text.Component

class ReloadCommand(
    private val support: CommandSupport,
    private val worldsStore: WorldsStore,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("reload")
            .requires { support.hasPermission(it, PERMISSION) }
            .executes { context ->
                val sender = context.source.sender
                sender.sendMessage(support.prefixMessage().append(Component.text("正在重新加载…")))
                worldsStore.load()
                sender.sendMessage(support.prefixMessage().append(Component.text("重新加载完成")))
                return@executes 1
            }
    }

    companion object {
        const val PERMISSION = "simpleworlds.command.reload"
    }
}
