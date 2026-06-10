package me.yin.simpleworld.world.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.RemoveUnloadedWorldEvent
import me.yin.simpleworld.world.manager.WorldManager
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender

class RemoveCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("remove")
            .requires { support.hasPermission(it, support.permissionRemove) }
            .then(Commands.argument("key", StringArgumentType.string())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    val keys = worldManager.unloadedWorlds.keys.map { it.toString() }.sorted()
                    for (key in keys) {
                        if (key.contains(remaining, true)) {
                            builder.suggest("\"$key\"")
                        }
                    }
                    builder.buildFuture()
                }
                .executes { context ->
                    val sender = context.source.sender
                    val worldKeyText = StringArgumentType.getString(context, "key")
                    val worldKey = runCatching { NamespacedKey.fromString(worldKeyText) }.getOrNull()
                    if (worldKey == null) {
                        sender.sendMessage(support.prefixMessage("世界 $worldKeyText 不是合法 key"))
                        return@executes 0
                    }

                    worldManager.removeUnloadedWorld(worldKey) { result ->
                        handleResult(sender, worldKey, result)
                    }
                    sender.sendMessage(support.prefixMessage("世界 $worldKey 配置移除已提交"))
                    return@executes 1
                }
        )
    }

    private fun handleResult(
        sender: CommandSender,
        worldKey: NamespacedKey,
        result: RemoveUnloadedWorldEvent,
    ) {
        when (result) {
            RemoveUnloadedWorldEvent.Removed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 的配置记录已移除"))
            }
            RemoveUnloadedWorldEvent.NotFound -> {
                sender.sendMessage(support.prefixMessage("没有找到世界 $worldKey 的未加载配置记录"))
            }
            is RemoveUnloadedWorldEvent.FailedWithException -> {
                support.logger.error("移除未加载世界配置失败", result.exception)
                sender.sendMessage(support.prefixMessage("世界 $worldKey 的配置记录移除失败：${result.exception.message}"))
            }
        }
    }
}
