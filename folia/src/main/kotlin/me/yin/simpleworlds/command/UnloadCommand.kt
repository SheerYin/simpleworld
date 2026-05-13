package me.yin.simpleworlds.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import net.kyori.adventure.text.Component

class UnloadCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager
) {

    private val server = support.plugin.server

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("unload")
            .requires { support.hasPermission(it, "simpleworlds.command.unload") }
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    for (world in server.worlds) {
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

                    support.sendMessage(sender, Component.text("世界 $worldName 卸载中…"))
                    worldsManager.unloadWorld(worldName)
                    worldsManager.save()
                    support.sendMessage(sender, Component.text("世界 $worldName 卸载完成"))
                    1
                }
            )
    }
}
