package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.UnloadWorldResult
import me.yin.simpleworld.world.WorldManager
import org.bukkit.command.CommandSender

class UnloadCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    private val plugin = support.plugin

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("unload")
            .requires { support.hasPermission(it, support.permissionUnload) }
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    for (world in plugin.server.worlds) {
                        val name = world.name
                        if (remaining.isEmpty() || name.startsWith(remaining, true)) {
                            builder.suggest(name)
                        }
                    }
                    builder.buildFuture()
                }
                .executes { context ->
                    val sender = context.source.sender
                    val worldName = StringArgumentType.getString(context, "name")

                    sender.sendMessage(support.prefixMessage("世界 $worldName 卸载中…"))
                    support.scope.launch {
                        try {
                            handleResult(sender, worldName, worldManager.tryUnloadWorld(worldName))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("卸载失败", e)
                            sender.sendMessage(support.prefixMessage("世界 $worldName 卸载失败：${e.message}"))
                        }
                    }
                    return@executes 1
                }
            )
    }

    private suspend fun handleResult(sender: CommandSender, worldName: String, result: UnloadWorldResult) {
        when (result) {
            UnloadWorldResult.Success -> {
                worldManager.save()
                sender.sendMessage(support.prefixMessage("世界 $worldName 卸载完成"))
            }
            UnloadWorldResult.NotLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 未加载"))
            }
            UnloadWorldResult.Failed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 卸载失败"))
            }
            UnloadWorldResult.Unsupported -> {
                sender.sendMessage(support.prefixMessage("当前服务端（Folia）不支持运行时卸载世界"))
            }
            UnloadWorldResult.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界操作或保存加载在进行中，请稍后再试"))
            }
        }
    }
}
