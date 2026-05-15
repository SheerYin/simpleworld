package me.yin.simpleworlds.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.runBlocking
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsService
import me.yin.simpleworlds.world.WorldsStore
import net.kyori.adventure.text.Component

class UnloadCommand(
    private val support: CommandSupport,
    private val worldsStore: WorldsStore,
    private val worldsService: WorldsService,
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
                            worldsService.unloadWorld(worldName)
                            runBlocking { worldsStore.save() }
                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 卸载完成")))
                        } catch (e: Throwable) {
                            support.logger.error("卸载失败", e)
                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 卸载失败：${e.message}")))
                        }
                    }
                    return@executes 1
                }
            )
    }

    companion object {
        const val PERMISSION = "simpleworlds.command.unload"
    }
}
