package me.yin.simpleworlds.command.support

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class CommandSupport(val plugin: JavaPlugin) {

    private val messagePrefix = "[简单世界] "

    fun message(text: String): Component {
        return Component.text(messagePrefix + text)
    }

    fun hasPermission(source: CommandSourceStack, permission: String): Boolean {
        val sender = source.sender
        return sender !is Player || sender.hasPermission(permission)
    }

    fun sendMessage(audience: Audience, component: Component) {
        audience.sendMessage(component)
    }

    fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sender.sendMessage(message("此命令仅限玩家执行"))
            return null
        }
        return sender
    }

    fun resolveTarget(sender: CommandSender, name: String): Player? {
        val target = plugin.server.getPlayerExact(name)
        if (target == null) {
            sender.sendMessage(message("玩家 $name 不在线"))
        }
        return target
    }

    fun playerTargetArgument(): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument("target", StringArgumentType.word())
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
