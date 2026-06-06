package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.WorldManager
import org.bukkit.NamespacedKey

class RemoveCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("remove")
            .requires { support.hasPermission(it, support.permissionRemove) }
            .then(Commands.argument("name", StringArgumentType.string())
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
                    val worldKeyText = StringArgumentType.getString(context, "name")
                    val worldKey = runCatching { NamespacedKey.fromString(worldKeyText) }.getOrNull()
                    if (worldKey == null) {
                        sender.sendMessage(support.prefixMessage("世界 $worldKeyText 不是合法 key"))
                        return@executes 0
                    }

                    val removedConfiguration = worldManager.unloadedWorlds.remove(worldKey) != null
                    if (removedConfiguration) {
                        sender.sendMessage(support.prefixMessage("世界 $worldKey 的配置记录已移除"))
                    } else {
                        sender.sendMessage(support.prefixMessage("没有找到世界 $worldKey 的未加载配置记录"))
                    }
                    return@executes 1
                }
        )
    }
}
