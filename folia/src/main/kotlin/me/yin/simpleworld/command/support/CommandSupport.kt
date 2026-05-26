package me.yin.simpleworld.command.support

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CoroutineScope
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.Logger

class CommandSupport(
    val plugin: JavaPlugin,
    val logger: Logger,
    private val prefix: String,
    val scope: CoroutineScope,
) {

    fun prefixMessage(message: String = ""): Component {
        return Component.text("[$prefix] $message")
    }

    fun hasPermission(source: CommandSourceStack, permission: String): Boolean {
        val sender = source.sender
        return sender !is Player || sender.hasPermission(permission)
    }

    fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sender.sendMessage(prefixMessage("此命令仅限玩家执行"))
            return null
        }
        return sender
    }

    fun resolveTarget(sender: CommandSender, name: String): Player? {
        val target = plugin.server.getPlayerExact(name)
        if (target == null) {
            sender.sendMessage(prefixMessage("玩家 $name 不在线"))
        }
        return target
    }

    fun playerNameArgument(argumentName: String): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument(argumentName, StringArgumentType.word())
            .suggests { _, builder ->
                val remaining = builder.remaining
                for (player in plugin.server.onlinePlayers) {
                    val playerName = player.name
                    if (playerName.startsWith(remaining, ignoreCase = true)) {
                        builder.suggest(playerName)
                    }
                }
                builder.buildFuture()
            }
    }
}
