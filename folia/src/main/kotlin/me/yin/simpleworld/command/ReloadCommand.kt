package me.yin.simpleworld.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.WorldManager

class ReloadCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("reload")
            .requires { support.hasPermission(it, support.permissionReload) }
            .executes { context ->
                val sender = context.source.sender
                sender.sendMessage(support.prefixMessage("正在重新加载…"))
                support.scope.launch {
                    try {
                        if (worldManager.tryLoad()) {
                            sender.sendMessage(support.prefixMessage("重新加载完成"))
                        } else {
                            sender.sendMessage(support.prefixMessage("已有保存或加载在进行中,请稍后再试"))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        support.logger.error("重新加载失败", e)
                        sender.sendMessage(support.prefixMessage("重新加载失败：${e.message}"))
                    }
                }
                return@executes 1
            }
    }
}
