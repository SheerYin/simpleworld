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

class TeleportCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager
) {

    private val server = support.plugin.server

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("teleport")
            .requires { support.hasPermission(it, "simpleworlds.command.teleport") }
            .then(Commands.argument("world", StringArgumentType.word())
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
                    val player = support.requirePlayer(context.source.sender) ?: return@executes 0
                    val worldName = StringArgumentType.getString(context, "world")
                    val world = server.getWorld(worldName)
                    if (world == null) {
                        support.sendMessage(player, Component.text("世界 $worldName 不存在"))
                        return@executes 0
                    }
                    teleport(player, world.spawnLocation)
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
                        val worldName = StringArgumentType.getString(context, "world")
                        val targetName = StringArgumentType.getString(context, "player")

                        val target = server.getPlayerExact(targetName)
                        if (target == null) {
                            support.sendMessage(sender, Component.text("玩家 $targetName 不在线"))
                            return@executes 0
                        }

                        val world = worldsManager.map1[worldName]?.world ?: server.getWorld(worldName)
                        if (world == null) {
                            support.sendMessage(sender, Component.text("世界 $worldName 不存在"))
                            return@executes 0
                        }
                        teleport(target, world.spawnLocation)
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
}
