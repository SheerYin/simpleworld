package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.command.support.CommandSupport
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender

class SetSpawnCommand(
    private val support: CommandSupport,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("setspawn")
            .requires { support.hasPermission(it, PERMISSION) }
            .executes { context -> setFromPlayer(context) }
            .then(worldArgument()
                .then(locationArgument()
                    .executes { context -> setFromArgument(context) }
                )
            )
    }

    private fun setFromPlayer(context: CommandContext<CommandSourceStack>): Int {
        val player = support.requirePlayer(context.source.sender) ?: return 0
        val location = player.location
        location.world.setSpawnLocation(location)
        player.sendMessage(
            support.prefixMessage("已将世界 ${location.world.name} 的出生点设为当前位置")
        )
        return 1
    }

    private fun setFromArgument(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        val worldName = StringArgumentType.getString(context, "world")
        val world = support.plugin.server.getWorld(worldName)
        if (world == null) {
            sender.sendMessage(support.prefixMessage("世界 $worldName 不存在"))
            return 0
        }
        val locationText = StringArgumentType.getString(context, "position")
        val location = resolveLocation(sender, world, locationText) ?: return 0
        world.setSpawnLocation(location)
        sender.sendMessage(
            support.prefixMessage("已将世界 ${world.name} 的出生点设为 ${location.x}, ${location.y}, ${location.z}")
        )
        return 1
    }

    private fun resolveLocation(sender: CommandSender, world: World, locationText: String): Location? {
        val parts = locationText.split(",").map { it.trim() }
        val size = parts.size
        if (size != 3 && size != 5) {
            sender.sendMessage(support.prefixMessage("坐标 $locationText 格式错误"))
            return null
        }
        val x = parts[0].toDoubleOrNull()
        val y = parts[1].toDoubleOrNull()
        val z = parts[2].toDoubleOrNull()
        if (x == null || y == null || z == null) {
            sender.sendMessage(support.prefixMessage("坐标解析错误"))
            return null
        }
        if (size == 3) {
            return Location(world, x, y, z, 0f, 0f)
        }
        val yaw = parts[3].toFloatOrNull() ?: 0f
        val pitch = parts[4].toFloatOrNull() ?: 0f
        return Location(world, x, y, z, yaw, pitch)
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

    private fun locationArgument(): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument("position", StringArgumentType.string())
            .suggests { context, builder ->
                val worldName = StringArgumentType.getString(context, "world")
                val world = support.plugin.server.getWorld(worldName)
                if (world != null) {
                    val l = world.spawnLocation
                    builder.suggest("\"${l.x},${l.y},${l.z},${l.yaw},${l.pitch}\"")
                }
                builder.buildFuture()
            }
    }

    companion object {
        const val PERMISSION = "simpleworld.command.setspawn"
    }
}
