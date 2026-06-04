package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.RemoveWorldResult
import me.yin.simpleworld.world.WorldManager
import org.bukkit.command.CommandSender

class RemoveCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("remove")
            .requires { support.hasPermission(it, support.permissionRemove) }
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    val names = worldManager.unloadedWorlds.keys.sorted()
                    for (name in names) {
                        if (remaining.isEmpty() || name.startsWith(remaining, true)) {
                            builder.suggest(name)
                        }
                    }
                    builder.buildFuture()
                }
                .executes { context ->
                    val sender = context.source.sender
                    val worldName = StringArgumentType.getString(context, "name")

                    sender.sendMessage(support.prefixMessage("正在移除世界 $worldName 的配置记录…"))
                    support.scope.launch {
                        try {
                            handleResult(sender, worldName, worldManager.tryRemoveWorld(worldName))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("移除失败", e)
                            sender.sendMessage(support.prefixMessage("世界 $worldName 配置记录移除失败：${e.message}"))
                        }
                    }
                    return@executes 1
                }
            )
    }

    private fun handleResult(sender: CommandSender, worldName: String, result: RemoveWorldResult) {
        when (result) {
            RemoveWorldResult.Success -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 的配置记录已移除"))
            }
            RemoveWorldResult.Loaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 已加载，请先卸载后再移除配置记录"))
            }
            RemoveWorldResult.NotFound -> {
                sender.sendMessage(support.prefixMessage("没有找到世界 $worldName 的未加载配置记录"))
            }
            RemoveWorldResult.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界操作或保存加载在进行中，请稍后再试"))
            }
        }
    }
}
