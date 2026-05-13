package me.yin.simpleworlds.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import net.kyori.adventure.text.Component
import org.bukkit.World
import org.bukkit.WorldType
import org.bukkit.command.CommandSender
import java.nio.file.Files

class CreateCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager
) {

    private val server = support.plugin.server

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("create")
            .requires { support.hasPermission(it, "simpleworlds.command.create") }
            .then(Commands.argument("name", StringArgumentType.word())
                .executes { context ->
                    val sender = context.source.sender
                    val worldName = StringArgumentType.getString(context, "name")
                    if (!precheck(sender, worldName)) return@executes 0

                    support.sendMessage(sender, Component.text("世界 $worldName 创建中…"))
                    worldsManager.createWorld(worldName)
                    worldsManager.save()
                    support.sendMessage(sender, Component.text("世界 $worldName 创建完成"))
                    1
                }
                .then(Commands.argument("seed", StringArgumentType.word())
                    .suggests { _, builder ->
                        builder.suggest("null")
                        builder.buildFuture()
                    }
                    .executes { context ->
                        val sender = context.source.sender
                        val worldName = StringArgumentType.getString(context, "name")
                        if (!precheck(sender, worldName)) return@executes 0
                        val seed = StringArgumentType.getString(context, "seed").toLongOrNull()

                        support.sendMessage(sender, Component.text("世界 $worldName 创建中…"))
                        worldsManager.createWorld(worldName, seed)
                        worldsManager.save()
                        support.sendMessage(sender, Component.text("世界 $worldName 创建完成"))
                        1
                    }
                    .then(Commands.argument("environment", StringArgumentType.word())
                        .suggests { _, builder ->
                            val remaining = builder.remainingLowerCase
                            for (environment in World.Environment.entries) {
                                val name = environment.name
                                if (remaining.isEmpty() || name.startsWith(remaining, true)) {
                                    builder.suggest(name)
                                }
                            }
                            builder.buildFuture()
                        }
                        .executes { context ->
                            val sender = context.source.sender
                            val worldName = StringArgumentType.getString(context, "name")
                            if (!precheck(sender, worldName)) return@executes 0
                            val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                            val environment = World.Environment.valueOf(StringArgumentType.getString(context, "environment"))

                            support.sendMessage(sender, Component.text("世界 $worldName 创建中…"))
                            worldsManager.createWorld(worldName, seed, environment)
                            worldsManager.save()
                            support.sendMessage(sender, Component.text("世界 $worldName 创建完成"))
                            1
                        }
                        .then(Commands.argument("type", StringArgumentType.word())
                            .suggests { _, builder ->
                                val remaining = builder.remainingLowerCase
                                for (type in WorldType.entries) {
                                    val name = type.name
                                    if (remaining.isEmpty() || name.startsWith(remaining, true)) {
                                        builder.suggest(name)
                                    }
                                }
                                builder.buildFuture()
                            }
                            .executes { context ->
                                val sender = context.source.sender
                                val worldName = StringArgumentType.getString(context, "name")
                                if (!precheck(sender, worldName)) return@executes 0
                                val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                                val environment = World.Environment.valueOf(StringArgumentType.getString(context, "environment"))
                                val type = WorldType.valueOf(StringArgumentType.getString(context, "type"))

                                support.sendMessage(sender, Component.text("世界 $worldName 创建中…"))
                                worldsManager.createWorld(worldName, seed, environment, type)
                                worldsManager.save()
                                support.sendMessage(sender, Component.text("世界 $worldName 创建完成"))
                                1
                            }
                            .then(Commands.argument("chunk_generator", StringArgumentType.string())
                                .executes { context ->
                                    val sender = context.source.sender
                                    val worldName = StringArgumentType.getString(context, "name")
                                    if (!precheck(sender, worldName)) return@executes 0
                                    val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                                    val environment = World.Environment.valueOf(StringArgumentType.getString(context, "environment"))
                                    val type = WorldType.valueOf(StringArgumentType.getString(context, "type"))
                                    val generator = StringArgumentType.getString(context, "chunk_generator")

                                    support.sendMessage(sender, Component.text("世界 $worldName 创建中…"))
                                    worldsManager.createWorld(worldName, seed, environment, type, generator)
                                    worldsManager.save()
                                    support.sendMessage(sender, Component.text("世界 $worldName 创建完成"))
                                    1
                                }
                            )
                        )
                    )
                )
            )
    }

    private fun precheck(sender: CommandSender, worldName: String): Boolean {
        if (server.getWorld(worldName) != null) {
            support.sendMessage(sender, Component.text("世界 $worldName 已经加载"))
            return false
        }
        val path = server.worldContainer.toPath().resolve(worldName).resolve("level.dat")
        if (Files.exists(path)) {
            support.sendMessage(sender, Component.text("世界 $worldName 存在磁盘"))
            return false
        }
        return true
    }
}
