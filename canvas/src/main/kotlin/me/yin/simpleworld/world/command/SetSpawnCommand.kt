package me.yin.simpleworld.world.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.SimpleWorld
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.command.support.PositionSupport
import me.yin.simpleworld.world.permission.WorldPermissions
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.command.CommandSender

class SetSpawnCommand(
    private val plugin: SimpleWorld,
    private val support: CommandSupport,
    private val permissions: WorldPermissions,
) {

    fun setSpawnBuilder(name: String = "setspawn"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { it.sender.hasPermission(permissions.setSpawnCommand) }
            .executes { context -> setFromPlayer(context) }
            .then(worldArgument()
                .then(positionArgument()
                    .executes { context -> setFromArgument(context) }
                )
            )
    }

    private fun setFromPlayer(context: CommandContext<CommandSourceStack>): Int {
        val player = support.requirePlayer(context.source.sender) ?: return 0
        val location = player.location
        plugin.server.globalRegionScheduler.run(plugin) {
            location.world.setSpawnLocation(location)
        }
        player.sendMessage(
            support.prefixMessage("已将世界 ${location.world.key} 的出生点设为当前位置")
        )
        return 1
    }

    private fun setFromArgument(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val world = resolveWorld(sender, context) ?: return 0
        val locationText = StringArgumentType.getString(context, "position")
        val location = parsePosition(sender, world, locationText) ?: return 0
        plugin.server.globalRegionScheduler.run(plugin) {
            world.setSpawnLocation(location)
        }
        sender.sendMessage(
            support.prefixMessage("已将世界 ${world.key} 的出生点设为 ${location.x}, ${location.y}, ${location.z}")
        )
        return 1
    }

    private fun resolveWorld(sender: CommandSender, context: CommandContext<CommandSourceStack>): World? {
        val worldKey = StringArgumentType.getString(context, "world")
        val key = runCatching { NamespacedKey.fromString(worldKey) }.getOrNull()
        val world = if (key == null) null else plugin.server.getWorld(key)
        if (world == null) {
            sender.sendMessage(support.prefixMessage("世界 $worldKey 不存在"))
        }
        return world
    }

    private fun parsePosition(sender: CommandSender, world: World, positionText: String): Location? {
        val location = PositionSupport.parseLocation(world, positionText)
        if (location == null) {
            sender.sendMessage(support.prefixMessage("坐标 $positionText 格式错误"))
        }
        return location
    }

    private fun worldArgument(): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument("world", StringArgumentType.string())
            .suggests { _, builder ->
                val remaining = builder.remainingLowerCase
                for (world in plugin.server.worlds) {
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
                val world = if (key == null) null else plugin.server.getWorld(key)
                if (world != null) {
                    val location = world.spawnLocation
                    builder.suggest("\"${location.x},${location.y},${location.z},${location.yaw},${location.pitch}\"")
                }
                builder.buildFuture()
            }
    }
}
