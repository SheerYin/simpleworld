package me.yin.simpleworlds.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.runBlocking
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsStore
import net.kyori.adventure.text.Component

class SaveCommand(
    private val support: CommandSupport,
    private val worldsStore: WorldsStore,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("save")
            .requires { support.hasPermission(it, PERMISSION) }
            .executes { context ->
                val sender = context.source.sender
                sender.sendMessage(support.prefixMessage().append(Component.text("正在保存…")))
                try {
                    runBlocking { worldsStore.save() }
                    sender.sendMessage(support.prefixMessage().append(Component.text("保存完成")))
                } catch (e: Throwable) {
                    support.logger.error("保存失败", e)
                    sender.sendMessage(support.prefixMessage().append(Component.text("保存失败：${e.message}")))
                }
                return@executes 1
            }
    }

    companion object {
        const val PERMISSION = "simpleworlds.command.save"
    }
}
