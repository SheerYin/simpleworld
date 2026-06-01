package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.CreateWorldResult
import me.yin.simpleworld.world.WorldManager
import org.bukkit.World
import org.bukkit.WorldType
import org.bukkit.command.CommandSender
import java.nio.file.Files

class CreateCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    private val plugin = support.plugin

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("create")
            .requires { support.hasPermission(it, support.permissionCreate) }
            .then(Commands.argument("name", StringArgumentType.word())
                .executes { context ->
                    val sender = context.source.sender
                    val worldName = StringArgumentType.getString(context, "name")
                    if (!precheck(sender, worldName)) return@executes 0

                    sender.sendMessage(support.prefixMessage("世界 $worldName 创建中…"))
                    support.scope.launch {
                        try {
                            handleResult(sender, worldName, worldManager.tryCreateWorld(worldName))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("创建失败", e)
                            sender.sendMessage(support.prefixMessage("世界 $worldName 创建失败：${e.message}"))
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

                        sender.sendMessage(support.prefixMessage("世界 $worldName 创建中…"))
                        support.scope.launch {
                            try {
                                handleResult(sender, worldName, worldManager.tryCreateWorld(worldName, seed))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                support.logger.error("创建失败", e)
                                sender.sendMessage(support.prefixMessage("世界 $worldName 创建失败：${e.message}"))
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

                            sender.sendMessage(support.prefixMessage("世界 $worldName 创建中…"))
                            support.scope.launch {
                                try {
                                    handleResult(sender, worldName, worldManager.tryCreateWorld(worldName, seed, environment))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    support.logger.error("创建失败", e)
                                    sender.sendMessage(support.prefixMessage("世界 $worldName 创建失败：${e.message}"))
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

                                sender.sendMessage(support.prefixMessage("世界 $worldName 创建中…"))
                                support.scope.launch {
                                    try {
                                        handleResult(sender, worldName, worldManager.tryCreateWorld(worldName, seed, environment, type))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        support.logger.error("创建失败", e)
                                        sender.sendMessage(support.prefixMessage("世界 $worldName 创建失败：${e.message}"))
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

                                    sender.sendMessage(support.prefixMessage("世界 $worldName 创建中…"))
                                    support.scope.launch {
                                        try {
                                            handleResult(
                                                sender,
                                                worldName,
                                                worldManager.tryCreateWorld(worldName, seed, environment, type, generator),
                                            )
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            support.logger.error("创建失败", e)
                                            sender.sendMessage(support.prefixMessage("世界 $worldName 创建失败：${e.message}"))
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

    private suspend fun handleResult(sender: CommandSender, worldName: String, result: CreateWorldResult) {
        when (result) {
            is CreateWorldResult.Success -> {
                worldManager.save()
                sender.sendMessage(support.prefixMessage("世界 $worldName 创建完成"))
            }
            CreateWorldResult.AlreadyLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 已经加载"))
            }
            CreateWorldResult.ExistsUnloaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 已存在配置，请使用 load 加载"))
            }
            CreateWorldResult.Failed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 创建失败"))
            }
            CreateWorldResult.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界操作或保存加载在进行中，请稍后再试"))
            }
        }
    }

    private fun precheck(sender: CommandSender, worldName: String): Boolean {
        if (plugin.server.getWorld(worldName) != null) {
            sender.sendMessage(support.prefixMessage("世界 $worldName 已经加载"))
            return false
        }
        if (worldManager.unloadedWorlds.containsKey(worldName)) {
            sender.sendMessage(support.prefixMessage("世界 $worldName 已存在配置，请使用 load 加载"))
            return false
        }
        val path = plugin.server.worldContainer.toPath().resolve(worldName).resolve("level.dat")
        if (Files.exists(path)) {
            sender.sendMessage(support.prefixMessage("世界 $worldName 存在磁盘"))
            return false
        }
        return true
    }
}
