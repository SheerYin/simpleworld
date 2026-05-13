package me.yin.simpleworlds.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TeleportCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("teleport")
            .requires { support.hasPermission(it, PERMISSION) }
            .then(worldArgument()
                .executes { context -> teleportSelf(context) }
                .then(support.playerTargetArgument()
                    .requires { support.hasPermission(it, PERMISSION_TARGET) }
                    .executes { context -> teleportTarget(context) }
                )
            )
    }

    private fun teleportSelf(context: CommandContext<CommandSourceStack>): Int {
        val player = support.requirePlayer(context.source.sender) ?: return 0
        val worldName = StringArgumentType.getString(context, "world")
        val location = resolveSpawn(player, worldName) ?: return 0
        teleport(player, location)
        return 1
    }

    private fun teleportTarget(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val targetName = StringArgumentType.getString(context, "target")
        val target = support.resolveTarget(sender, targetName) ?: return 0
        val worldName = StringArgumentType.getString(context, "world")
        val location = resolveSpawn(sender, worldName) ?: return 0
        target.scheduler.run(support.plugin, { teleport(target, location) }, null)
        return 1
    }

    private fun resolveSpawn(sender: CommandSender, worldName: String): Location? {
        val world = worldsManager.worldByName[worldName]?.world ?: support.plugin.server.getWorld(worldName)
        if (world == null) {
            sender.sendMessage(support.message("世界 $worldName 不存在"))
            return null
        }
        return world.spawnLocation
    }

    private fun teleport(player: Player, location: Location) {
        player.leaveVehicle()
        for (passenger in player.passengers) {
            passenger.leaveVehicle()
        }
        player.teleportAsync(location)
    }

    private fun worldArgument(): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument("world", StringArgumentType.word())
            .suggests { _, builder ->
                val remaining = builder.remainingLowerCase
                for (world in support.plugin.server.worlds) {
                    val name = world.name
                    if (remaining.isEmpty() || name.startsWith(remaining, true)) {
                        builder.suggest(name)
                    }
                }
                builder.buildFuture()
            }
    }

    companion object {
        const val PERMISSION = "simpleworlds.command.teleport"
        const val PERMISSION_TARGET = "simpleworlds.command.teleport.target"
    }
}
