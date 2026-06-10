package me.yin.simpleworld.world.command.support

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

    // 各命令的权限节点（可在运行时调整）
    @Volatile
    var permissionRoot = "simpleworld.command"

    @Volatile
    var permissionReload = "simpleworld.command.reload"

    @Volatile
    var permissionSave = "simpleworld.command.save"

    @Volatile
    var permissionTeleport = "simpleworld.command.teleport"

    @Volatile
    var permissionTeleportTarget = "simpleworld.command.teleport.target"

    @Volatile
    var permissionSetSpawn = "simpleworld.command.setspawn"

    @Volatile
    var permissionList = "simpleworld.command.list"

    @Volatile
    var permissionCreate = "simpleworld.command.create"

    @Volatile
    var permissionLoad = "simpleworld.command.load"

    @Volatile
    var permissionUnload = "simpleworld.command.unload"

    @Volatile
    var permissionRemove = "simpleworld.command.remove"

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
