package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.WorldsManager
import net.kyori.adventure.text.Component

class UnloadCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager,
) {

    private val plugin = support.plugin

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("unload")
            .requires { support.hasPermission(it, PERMISSION) }
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

                    sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 卸载中…")))
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        try {
                            worldsManager.unloadWorld(worldName)
                            runBlocking { worldsManager.save() }
                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 卸载完成")))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("卸载失败", e)
                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 卸载失败：${e.message}")))
                        }
                    }
                    return@executes 1
                }
            )
    }

    companion object {
        const val PERMISSION = "simpleworld.command.unload"
    }
}
