package me.yin.simpleworld.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.WorldManager

class SaveCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("save")
            .requires { support.hasPermission(it, PERMISSION) }
            .executes { context ->
                val sender = context.source.sender
                sender.sendMessage(support.prefixMessage("正在保存…"))
                support.scope.launch {
                    try {
                        if (worldManager.trySave()) {
                            sender.sendMessage(support.prefixMessage("保存完成"))
                        } else {
                            sender.sendMessage(support.prefixMessage("已有保存或加载在进行中，请稍后再试"))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        support.logger.error("保存失败", e)
                        sender.sendMessage(support.prefixMessage("保存失败：${e.message}"))
                    }
                }
                return@executes 1
            }
    }

    companion object {
        const val PERMISSION = "simpleworld.command.save"
    }
}
