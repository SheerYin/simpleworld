package me.yin.simpleworld.world.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.canvasmc.canvas.WorldUnloadResult
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.UnloadWorldEvent
import me.yin.simpleworld.world.manager.WorldManager
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender

class UnloadCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    private val plugin = support.plugin

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("unload")
            .requires { support.hasPermission(it, support.permissionUnload) }
            .then(Commands.argument("key", StringArgumentType.string())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    for (world in plugin.server.worlds) {
                        val key = world.key.toString()
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

                    worldManager.unloadWorld(worldKey) { result ->
                        handleResult(sender, worldKey, result)
                    }
                    sender.sendMessage(support.prefixMessage("世界 $worldKey 卸载已提交"))
                    return@executes 1
                }
        )
    }

    private fun handleResult(sender: CommandSender, worldKey: NamespacedKey, result: UnloadWorldEvent) {
        when (result) {
            is UnloadWorldEvent.Completed -> {
                if (result.result.isSuccess) {
                    worldManager.trySaveWorlds()
                    sender.sendMessage(support.prefixMessage("世界 $worldKey 卸载完成"))
                } else {
                    sender.sendMessage(support.prefixMessage("世界 $worldKey 卸载失败：${formatFailure(result.result)}"))
                }
            }
            UnloadWorldEvent.NotLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 未加载"))
            }
            is UnloadWorldEvent.FailedWithException -> {
                support.logger.error("卸载失败", result.exception)
                sender.sendMessage(support.prefixMessage("世界 $worldKey 卸载失败：${result.exception.message}"))
            }
        }
    }

    private fun formatFailure(reason: WorldUnloadResult): String {
        return when (reason) {
            WorldUnloadResult.FAIL_PLAYERS_JOINING -> "有玩家正在加入该世界"
            WorldUnloadResult.FAIL_PLAYERS_PRESENT -> "世界内仍有玩家"
            WorldUnloadResult.FAIL_ALREADY_UNLOADING -> "世界已经在卸载中"
            WorldUnloadResult.FAIL_IS_OVERWORLD -> "不能卸载主世界"
            WorldUnloadResult.FAIL_UNLOAD_EVENT -> "卸载事件被取消"
            WorldUnloadResult.FAIL_IS_SHUTDOWN -> "服务器正在关闭"
            WorldUnloadResult.FAIL_UNKNOWN -> "未知错误"
            WorldUnloadResult.SUCCESS -> "无"
        }
    }
}
