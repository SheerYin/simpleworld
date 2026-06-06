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
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender

class RemoveCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("remove")
            .requires { support.hasPermission(it, support.permissionRemove) }
            .then(Commands.argument("name", StringArgumentType.string())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase.trim('"')
                    val keys = worldManager.unloadedWorlds.keys.map { it.toString() }.sorted()
                    for (key in keys) {
                        if (remaining.isEmpty() || key.startsWith(remaining, true)) {
                            builder.suggest("\"$key\"")
                        }
                    }
                    builder.buildFuture()
                }
                .executes { context ->
                    val sender = context.source.sender
                    val worldKeyText = StringArgumentType.getString(context, "name")
                    val worldKey = NamespacedKey.fromString(worldKeyText)
                    if (worldKey == null) {
                        sender.sendMessage(support.prefixMessage("世界 $worldKeyText 不是合法 key"))
                        return@executes 0
                    }

                    sender.sendMessage(support.prefixMessage("正在移除世界 $worldKey 的配置记录…"))
                    support.scope.launch {
                        try {
                            handleResult(sender, worldKey, worldManager.tryRemoveWorld(worldKey))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("移除失败", e)
                            sender.sendMessage(support.prefixMessage("世界 $worldKey 配置记录移除失败：${e.message}"))
                        }
                    }
                    return@executes 1
                }
        )
    }

    private fun handleResult(sender: CommandSender, worldKey: NamespacedKey, result: RemoveWorldResult) {
        when (result) {
            RemoveWorldResult.Success -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 的配置记录已移除"))
            }
            RemoveWorldResult.Loaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 已加载，请先卸载后再移除配置记录"))
            }
            RemoveWorldResult.NotFound -> {
                sender.sendMessage(support.prefixMessage("没有找到世界 $worldKey 的未加载配置记录"))
            }
            RemoveWorldResult.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界操作或保存加载在进行中，请稍后再试"))
            }
        }
    }
}
