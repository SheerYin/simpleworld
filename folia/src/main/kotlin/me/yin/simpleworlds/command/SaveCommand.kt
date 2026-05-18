package me.yin.simpleworlds.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import net.kyori.adventure.text.Component

class SaveCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("save")
            .requires { support.hasPermission(it, PERMISSION) }
            .executes { context ->
                val sender = context.source.sender
                sender.sendMessage(support.prefixMessage().append(Component.text("正在保存…")))
                support.scope.launch {
                    try {
                        if (worldsManager.trySave()) {
                            sender.sendMessage(support.prefixMessage().append(Component.text("保存完成")))
                        } else {
                            sender.sendMessage(support.prefixMessage().append(Component.text("已有保存或加载在进行中，请稍后再试")))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        support.logger.error("保存失败", e)
                        sender.sendMessage(support.prefixMessage().append(Component.text("保存失败：${e.message}")))
                    }
                }
                return@executes 1
            }
    }

    companion object {
        const val PERMISSION = "simpleworlds.command.save"
    }
}
