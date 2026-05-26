package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.WorldsManager
import net.kyori.adventure.text.Component
import org.bukkit.World
import org.bukkit.WorldType
import org.bukkit.command.CommandSender
import java.nio.file.Files

class CreateCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager,
) {

    private val plugin = support.plugin

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("create")
            .requires { support.hasPermission(it, PERMISSION) }
            .then(Commands.argument("name", StringArgumentType.word())
                .executes { context ->
                    val sender = context.source.sender
                    val worldName = StringArgumentType.getString(context, "name")
                    if (!precheck(sender, worldName)) return@executes 0

                    sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建中…")))
                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                        try {
                            worldsManager.createWorld(worldName)
                            runBlocking { worldsManager.save() }
                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建完成")))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("创建失败", e)
                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建失败：${e.message}")))
                        }
                    }
                    return@executes 1
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

                        sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建中…")))
                        plugin.server.globalRegionScheduler.run(plugin) { _ ->
                            try {
                                worldsManager.createWorld(worldName, seed)
                                runBlocking { worldsManager.save() }
                                sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建完成")))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                support.logger.error("创建失败", e)
                                sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建失败：${e.message}")))
                            }
                        }
                        return@executes 1
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

                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建中…")))
                            plugin.server.globalRegionScheduler.run(plugin) { _ ->
                                try {
                                    worldsManager.createWorld(worldName, seed, environment)
                                    runBlocking { worldsManager.save() }
                                    sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建完成")))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    support.logger.error("创建失败", e)
                                    sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建失败：${e.message}")))
                                }
                            }
                            return@executes 1
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

                                sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建中…")))
                                plugin.server.globalRegionScheduler.run(plugin) { _ ->
                                    try {
                                        worldsManager.createWorld(worldName, seed, environment, type)
                                        runBlocking { worldsManager.save() }
                                        sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建完成")))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        support.logger.error("创建失败", e)
                                        sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建失败：${e.message}")))
                                    }
                                }
                                return@executes 1
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

                                    sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建中…")))
                                    plugin.server.globalRegionScheduler.run(plugin) { _ ->
                                        try {
                                            worldsManager.createWorld(worldName, seed, environment, type, generator)
                                            runBlocking { worldsManager.save() }
                                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建完成")))
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            support.logger.error("创建失败", e)
                                            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 创建失败：${e.message}")))
                                        }
                                    }
                                    return@executes 1
                                }
                            )
                        )
                    )
                )
            )
    }

    private fun precheck(sender: CommandSender, worldName: String): Boolean {
        if (plugin.server.getWorld(worldName) != null) {
            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 已经加载")))
            return false
        }
        val path = plugin.server.worldContainer.toPath().resolve(worldName).resolve("level.dat")
        if (Files.exists(path)) {
            sender.sendMessage(support.prefixMessage().append(Component.text("世界 $worldName 存在磁盘")))
            return false
        }
        return true
    }

    companion object {
        const val PERMISSION = "simpleworld.command.create"
    }
}
