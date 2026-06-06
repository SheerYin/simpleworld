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
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldType
import org.bukkit.command.CommandSender
import kotlin.io.path.isDirectory

class CreateCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    private val plugin = support.plugin

    private val creatableEnvironments = World.Environment.entries.filter { it != World.Environment.CUSTOM }

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("create")
            .requires { support.hasPermission(it, support.permissionCreate) }
            .then(Commands.argument("name", StringArgumentType.word())
                .executes { context ->
                    val sender = context.source.sender
                    val worldKey = precheck(sender, StringArgumentType.getString(context, "name"))
                        ?: return@executes 0

                    sender.sendMessage(support.prefixMessage("世界 $worldKey 创建中…"))
                    support.scope.launch {
                        try {
                            handleResult(sender, worldKey, worldManager.tryCreateWorld(worldKey))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("创建失败", e)
                            sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败：${e.message}"))
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
                        val worldKey = precheck(sender, StringArgumentType.getString(context, "name"))
                            ?: return@executes 0
                        val seed = StringArgumentType.getString(context, "seed").toLongOrNull()

                        sender.sendMessage(support.prefixMessage("世界 $worldKey 创建中…"))
                        support.scope.launch {
                            try {
                                handleResult(sender, worldKey, worldManager.tryCreateWorld(worldKey, seed))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                support.logger.error("创建失败", e)
                                sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败：${e.message}"))
                            }
                        }
                        return@executes 1
                    }
                    .then(Commands.argument("environment", StringArgumentType.word())
                        .suggests { _, builder ->
                            val remaining = builder.remainingLowerCase
                            for (environment in creatableEnvironments) {
                                val name = environment.name
                                if (remaining.isEmpty() || name.startsWith(remaining, true)) {
                                    builder.suggest(name)
                                }
                            }
                            builder.buildFuture()
                        }
                        .executes { context ->
                            val sender = context.source.sender
                            val worldKey = precheck(sender, StringArgumentType.getString(context, "name"))
                                ?: return@executes 0
                            val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                            val environment = parseEnvironment(sender, StringArgumentType.getString(context, "environment"))
                                ?: return@executes 0

                            sender.sendMessage(support.prefixMessage("世界 $worldKey 创建中…"))
                            support.scope.launch {
                                try {
                                    handleResult(sender, worldKey, worldManager.tryCreateWorld(worldKey, seed, environment))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    support.logger.error("创建失败", e)
                                    sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败：${e.message}"))
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
                                val worldKey = precheck(sender, StringArgumentType.getString(context, "name"))
                                    ?: return@executes 0
                                val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                                val environment = parseEnvironment(sender, StringArgumentType.getString(context, "environment"))
                                    ?: return@executes 0
                                val type = WorldType.valueOf(StringArgumentType.getString(context, "type"))

                                sender.sendMessage(support.prefixMessage("世界 $worldKey 创建中…"))
                                support.scope.launch {
                                    try {
                                        handleResult(sender, worldKey, worldManager.tryCreateWorld(worldKey, seed, environment, type))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        support.logger.error("创建失败", e)
                                        sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败：${e.message}"))
                                    }
                                }
                                return@executes 1
                            }
                            .then(Commands.argument("chunk_generator", StringArgumentType.string())
                                .executes { context ->
                                    val sender = context.source.sender
                                    val worldKey = precheck(sender, StringArgumentType.getString(context, "name"))
                                        ?: return@executes 0
                                    val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                                    val environment = parseEnvironment(sender, StringArgumentType.getString(context, "environment"))
                                        ?: return@executes 0
                                    val type = WorldType.valueOf(StringArgumentType.getString(context, "type"))
                                    val generator = StringArgumentType.getString(context, "chunk_generator")

                                    sender.sendMessage(support.prefixMessage("世界 $worldKey 创建中…"))
                                    support.scope.launch {
                                        try {
                                            handleResult(
                                                sender,
                                                worldKey,
                                                worldManager.tryCreateWorld(worldKey, seed, environment, type, generator),
                                            )
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            support.logger.error("创建失败", e)
                                            sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败：${e.message}"))
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

    private suspend fun handleResult(sender: CommandSender, worldKey: NamespacedKey, result: CreateWorldResult) {
        when (result) {
            is CreateWorldResult.Success -> {
                worldManager.save()
                sender.sendMessage(support.prefixMessage("世界 $worldKey 创建完成"))
            }
            CreateWorldResult.AlreadyLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 已经加载"))
            }
            CreateWorldResult.ExistsUnloaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 已存在配置，请使用 load 加载"))
            }
            CreateWorldResult.Failed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败"))
            }
            CreateWorldResult.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界操作或保存加载在进行中，请稍后再试"))
            }
            CreateWorldResult.Unsupported -> {
                sender.sendMessage(support.prefixMessage("当前服务端（Folia）不支持创建世界"))
            }
        }
    }

    private fun parseEnvironment(sender: CommandSender, name: String): World.Environment? {
        val environment = runCatching { World.Environment.valueOf(name) }.getOrNull()
        if (environment == null || environment == World.Environment.CUSTOM) {
            sender.sendMessage(support.prefixMessage("环境 $name 不能用于创建世界"))
            return null
        }
        return environment
    }

    private fun precheck(sender: CommandSender, worldName: String): NamespacedKey? {
        val key = runCatching { NamespacedKey.minecraft(worldName) }.getOrNull()
        if (key == null) {
            sender.sendMessage(support.prefixMessage("世界 $worldName 不是合法名称"))
            return null
        }
        if (plugin.server.getWorld(key) != null) {
            sender.sendMessage(support.prefixMessage("世界 $key 已经加载"))
            return null
        }
        if (worldManager.unloadedWorlds.containsKey(key)) {
            sender.sendMessage(support.prefixMessage("世界 $key 已存在配置，请使用 load 加载"))
            return null
        }
        if (plugin.server.levelDirectory.resolve("dimensions").resolve(key.namespace).resolve(key.key).isDirectory()) {
            sender.sendMessage(support.prefixMessage("世界 $key 存在磁盘"))
            return null
        }
        return key
    }
}
