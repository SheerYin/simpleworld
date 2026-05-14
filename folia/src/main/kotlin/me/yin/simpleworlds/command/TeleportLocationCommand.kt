package me.yin.simpleworlds.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TeleportLocationCommand(
    private val support: CommandSupport,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("teleportlocation")
            .requires { support.hasPermission(it, PERMISSION) }
            .then(locationArgument()
                .executes { context -> teleportSelf(context) }
                .then(support.playerTargetArgument()
                    .requires { support.hasPermission(it, PERMISSION_TARGET) }
                    .executes { context -> teleportTarget(context) }
                )
            )
    }

    private fun teleportSelf(context: CommandContext<CommandSourceStack>): Int {
        val player = support.requirePlayer(context.source.sender) ?: return 0
        val locationText = StringArgumentType.getString(context, "location")
        val location = resolveLocation(player, locationText) ?: return 0
        teleport(player, location)
        return 1
    }

    private fun teleportTarget(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val targetName = StringArgumentType.getString(context, "target")
        val target = support.resolveTarget(sender, targetName) ?: return 0
        val locationText = StringArgumentType.getString(context, "location")
        val location = resolveLocation(sender, locationText) ?: return 0
        target.scheduler.run(support.plugin, { teleport(target, location) }, null)
        return 1
    }

    private fun resolveLocation(sender: CommandSender, locationText: String): Location? {
        val parts = locationText.split(",").map { it.trim() }
        val size = parts.size
        if (size != 4 && size != 6) {
            sender.sendMessage(support.message("坐标 $locationText 格式错误"))
            return null
        }

        val world = support.plugin.server.getWorld(parts[0])
        if (world == null) {
            sender.sendMessage(support.message("世界 ${parts[0]} 不存在"))
            return null
        }
        val x = parts[1].toDoubleOrNull()
        val y = parts[2].toDoubleOrNull()
        val z = parts[3].toDoubleOrNull()
        if (x == null || y == null || z == null) {
            sender.sendMessage(support.message("坐标解析错误"))
            return null
        }
        if (size == 4) {
            return Location(world, x, y, z, 0f, 0f)
        }
        val yaw = parts[4].toFloatOrNull() ?: 0f
        val pitch = parts[5].toFloatOrNull() ?: 0f
        return Location(world, x, y, z, yaw, pitch)
    }

    private fun teleport(player: Player, location: Location) {
        player.leaveVehicle()
        for (passenger in player.passengers) {
            passenger.leaveVehicle()
        }
        player.teleportAsync(location)
    }

    private fun locationArgument(): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument("location", StringArgumentType.string())
            .suggests { _, builder ->
                val remaining = builder.remainingLowerCase
                for (world in support.plugin.server.worlds) {
                    val worldName = world.name
                    if (remaining.isEmpty() || worldName.startsWith(remaining, true)) {
                        val l = world.spawnLocation
                        builder.suggest("\"${worldName},${l.x},${l.y},${l.z},${l.yaw},${l.pitch}\"")
                    }
                }
                builder.buildFuture()
            }
    }

    companion object {
        const val PERMISSION = "simpleworlds.command.teleportlocation"
        const val PERMISSION_TARGET = "simpleworlds.command.teleportlocation.target"
    }
}
