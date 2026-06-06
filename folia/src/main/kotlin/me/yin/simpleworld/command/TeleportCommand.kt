package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.command.support.PositionSupport
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TeleportCommand(
    private val support: CommandSupport,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("teleport")
            .requires { support.hasPermission(it, support.permissionTeleport) }
            .then(support.playerNameArgument("target")
                .then(worldArgument()
                    .executes { context -> handle(context, hasPosition = false) }
                    .then(positionArgument()
                        .executes { context -> handle(context, hasPosition = true) }
                    )
                )
            )
    }

    private fun handle(context: CommandContext<CommandSourceStack>, hasPosition: Boolean): Int {
        val sender = context.source.sender
        val targetName = StringArgumentType.getString(context, "target")
        val target = support.resolveTarget(sender, targetName) ?: return 0
        if (target != sender && !support.hasPermission(context.source, support.permissionTeleportTarget)) {
            sender.sendMessage(support.prefixMessage("没有权限传送其他玩家"))
            return 0
        }
        val location = resolveLocation(sender, context, hasPosition) ?: return 0
        target.scheduler.run(support.plugin, { teleport(target, location) }, null)
        return 1
    }

    private fun resolveLocation(
        sender: CommandSender,
        context: CommandContext<CommandSourceStack>,
        hasPosition: Boolean,
    ): Location? {
        val worldKey = StringArgumentType.getString(context, "world")
        val key = runCatching { NamespacedKey.fromString(worldKey) }.getOrNull()
        val world = if (key == null) null else support.plugin.server.getWorld(key)
        if (world == null) {
            sender.sendMessage(support.prefixMessage("世界 $worldKey 不存在"))
            return null
        }
        if (!hasPosition) {
            return world.spawnLocation
        }
        val positionText = StringArgumentType.getString(context, "position")
        return parsePosition(sender, world, positionText)
    }

    private fun parsePosition(sender: CommandSender, world: World, positionText: String): Location? {
        val location = PositionSupport.parseLocation(world, positionText)
        if (location == null) {
            sender.sendMessage(support.prefixMessage("坐标 $positionText 格式错误"))
        }
        return location
    }

    private fun teleport(player: Player, location: Location) {
        player.leaveVehicle()
        for (passenger in player.passengers) {
            passenger.leaveVehicle()
        }
        player.teleportAsync(location)
    }

    private fun worldArgument(): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument("world", StringArgumentType.string())
            .suggests { _, builder ->
                val remaining = builder.remainingLowerCase
                for (world in support.plugin.server.worlds) {
                    val key = world.key.toString()
                    if (key.contains(remaining, true)) {
                        builder.suggest("\"$key\"")
                    }
                }
                builder.buildFuture()
            }
    }

    private fun positionArgument(): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument("position", StringArgumentType.string())
            .suggests { context, builder ->
                val worldKey = StringArgumentType.getString(context, "world")
                val key = runCatching { NamespacedKey.fromString(worldKey) }.getOrNull()
                val world = if (key == null) null else support.plugin.server.getWorld(key)
                if (world != null) {
                    val location = world.spawnLocation
                    builder.suggest("\"${location.x},${location.y},${location.z},${location.yaw},${location.pitch}\"")
                }
                builder.buildFuture()
            }
    }
}
