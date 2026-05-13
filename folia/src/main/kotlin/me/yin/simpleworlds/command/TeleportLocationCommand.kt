package me.yin.simpleworlds.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.entity.Player

class TeleportLocationCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager
) {

    private val server = support.plugin.server

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("teleportlocation")
            .requires { support.hasPermission(it, "simpleworlds.command.teleportlocation") }
            .then(Commands.argument("location", StringArgumentType.string())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    for (world in server.worlds) {
                        val worldName = world.name
                        if (remaining.isEmpty() || worldName.startsWith(remaining, true)) {
                            val l = world.spawnLocation
                            builder.suggest("${worldName},${l.x},${l.y},${l.z},${l.yaw},${l.pitch}")
                        }
                    }
                    builder.buildFuture()
                }
                .executes { context ->
                    val player = support.requirePlayer(context.source.sender) ?: return@executes 0
                    val text = StringArgumentType.getString(context, "location")
                    val location = parseLocationText(text)
                    if (location == null) {
                        support.sendMessage(player, Component.text("坐标 $text 不是有效的"))
                        return@executes 0
                    }
                    teleport(player, location)
                    1
                }
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests { _, builder ->
                        val remaining = builder.remainingLowerCase
                        for (online in server.onlinePlayers) {
                            val name = online.name
                            if (remaining.isEmpty() || name.startsWith(remaining, true)) {
                                builder.suggest(name)
                            }
                        }
                        builder.buildFuture()
                    }
                    .executes { context ->
                        val sender = context.source.sender
                        val text = StringArgumentType.getString(context, "location")
                        val targetName = StringArgumentType.getString(context, "player")

                        val target = server.getPlayerExact(targetName)
                        if (target == null) {
                            support.sendMessage(sender, Component.text("玩家 $targetName 不在线"))
                            return@executes 0
                        }
                        val location = parseLocationText(text)
                        if (location == null) {
                            support.sendMessage(sender, Component.text("坐标 $text 不是有效的"))
                            return@executes 0
                        }
                        teleport(target, location)
                        1
                    }
                )
            )
    }

    private fun teleport(player: Player, location: Location) {
        player.leaveVehicle()
        for (passenger in player.passengers) {
            passenger.leaveVehicle()
        }
        player.teleportAsync(location)
    }

    private fun parseLocationText(text: String): Location? {
        val parts = text.split(",").map { it.trim() }
        val size = parts.size
        if (size != 4 && size != 6) return null

        val world = server.getWorld(parts[0]) ?: return null
        val x = parts[1].toDoubleOrNull() ?: return null
        val y = parts[2].toDoubleOrNull() ?: return null
        val z = parts[3].toDoubleOrNull() ?: return null
        if (size == 4) {
            return Location(world, x, y, z, 0f, 0f)
        }
        val yaw = parts[4].toFloatOrNull() ?: 0f
        val pitch = parts[5].toFloatOrNull() ?: 0f
        return Location(world, x, y, z, yaw, pitch)
    }
}
