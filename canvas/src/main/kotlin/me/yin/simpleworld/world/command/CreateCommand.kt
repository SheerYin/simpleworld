package me.yin.simpleworld.world.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.SimpleWorld
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.CreateWorldEvent
import me.yin.simpleworld.world.manager.WorldManager
import me.yin.simpleworld.world.permission.WorldPermissions
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldType
import org.bukkit.command.CommandSender
import org.slf4j.Logger
import kotlin.io.path.isDirectory

class CreateCommand(
    private val plugin: SimpleWorld,
    private val logger: Logger,
    private val support: CommandSupport,
    private val permissions: WorldPermissions,
    private val worldManager: WorldManager,
) {

    private val creatableEnvironments = World.Environment.entries.filter { it != World.Environment.CUSTOM }

    fun createBuilder(name: String = "create"): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { it.sender.hasPermission(permissions.createCommand) }
            .then(Commands.argument("key", StringArgumentType.string())
                .executes { context ->
                    val sender = context.source.sender
                    val worldKey = precheck(sender, StringArgumentType.getString(context, "key"))
                        ?: return@executes 0

                    worldManager.createWorld(worldKey) { result ->
                        handleResult(sender, worldKey, result)
                    }
                    sender.sendMessage(support.prefixMessage("世界 $worldKey 创建已提交"))
                    return@executes 1
                }
                .then(Commands.argument("seed", StringArgumentType.word())
                    .suggests { _, builder ->
                        builder.suggest("null")
                        builder.buildFuture()
                    }
                    .executes { context ->
                        val sender = context.source.sender
                        val worldKey = precheck(sender, StringArgumentType.getString(context, "key"))
                            ?: return@executes 0
                        val seed = StringArgumentType.getString(context, "seed").toLongOrNull()

                        worldManager.createWorld(worldKey, seed) { result ->
                            handleResult(sender, worldKey, result)
                        }
                        sender.sendMessage(support.prefixMessage("世界 $worldKey 创建已提交"))
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
                            val worldKey = precheck(sender, StringArgumentType.getString(context, "key"))
                                ?: return@executes 0
                            val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                            val environment = StringArgumentType.getString(context, "environment")

                            worldManager.createWorld(worldKey, seed, environment) { result ->
                                handleResult(sender, worldKey, result)
                            }
                            sender.sendMessage(support.prefixMessage("世界 $worldKey 创建已提交"))
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
                                val worldKey = precheck(sender, StringArgumentType.getString(context, "key"))
                                    ?: return@executes 0
                                val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                                val environment = StringArgumentType.getString(context, "environment")
                                val type = StringArgumentType.getString(context, "type")

                                worldManager.createWorld(worldKey, seed, environment, type) { result ->
                                    handleResult(sender, worldKey, result)
                                }
                                sender.sendMessage(support.prefixMessage("世界 $worldKey 创建已提交"))
                                return@executes 1
                            }
                            .then(Commands.argument("chunk_generator", StringArgumentType.string())
                                .executes { context ->
                                    val sender = context.source.sender
                                    val worldKey = precheck(sender, StringArgumentType.getString(context, "key"))
                                        ?: return@executes 0
                                    val seed = StringArgumentType.getString(context, "seed").toLongOrNull()
                                    val environment = StringArgumentType.getString(context, "environment")
                                    val type = StringArgumentType.getString(context, "type")
                                    val generator = StringArgumentType.getString(context, "chunk_generator")

                                    worldManager.createWorld(worldKey, seed, environment, type, generator) { result ->
                                        handleResult(sender, worldKey, result)
                                    }
                                    sender.sendMessage(support.prefixMessage("世界 $worldKey 创建已提交"))
                                    return@executes 1
                                }
                            )
                        )
                    )
                )
        )
    }

    private fun handleResult(sender: CommandSender, worldKey: NamespacedKey, result: CreateWorldEvent) {
        when (result) {
            is CreateWorldEvent.Created -> {
                sender.sendMessage(support.prefixMessage("世界 ${result.world.key} 创建完成"))
            }
            CreateWorldEvent.AlreadyLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 已经加载"))
            }
            CreateWorldEvent.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界状态更新正在进行，请稍后再试"))
            }
            is CreateWorldEvent.InvalidEnvironment -> {
                sender.sendMessage(support.prefixMessage("环境 ${result.value} 不能用于创建世界"))
            }
            is CreateWorldEvent.InvalidWorldType -> {
                sender.sendMessage(support.prefixMessage("Bukkit 类型 ${result.value} 不能用于创建世界"))
            }
            CreateWorldEvent.Failed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败"))
            }
            is CreateWorldEvent.FailedWithException -> {
                logger.error("创建世界失败", result.exception)
                sender.sendMessage(support.prefixMessage("世界 $worldKey 创建失败：${result.exception.message}"))
            }
        }
    }

    private fun precheck(sender: CommandSender, worldName: String): NamespacedKey? {
        val key = runCatching { NamespacedKey.fromString(worldName) }.getOrNull()
        if (key == null) {
            sender.sendMessage(support.prefixMessage("世界 $worldName 不是合法 key"))
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
