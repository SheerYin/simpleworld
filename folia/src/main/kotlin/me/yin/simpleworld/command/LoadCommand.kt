package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.LoadWorldResult
import me.yin.simpleworld.world.WorldManager
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import java.nio.file.Files
import kotlin.io.path.isDirectory

class LoadCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    private val plugin = support.plugin
    private val coroutineScope = support.scope

    @Volatile
    var loadSuggestionSemaphore = Semaphore(2)

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("load")
            .requires { support.hasPermission(it, support.permissionLoad) }
            .then(Commands.argument("key", StringArgumentType.string())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    coroutineScope.future {
                        loadSuggestionSemaphore.withPermit {
                            val keys = mutableSetOf<NamespacedKey>()
                            val dimensions = plugin.server.levelDirectory.resolve("dimensions")
                            if (dimensions.isDirectory()) {
                                Files.list(dimensions).use { namespacePaths ->
                                    namespacePaths
                                        .filter { it.isDirectory() }
                                        .forEach { namespacePath ->
                                            val namespace = namespacePath.fileName.toString()
                                            Files.list(namespacePath).use { dimensionPaths ->
                                                dimensionPaths
                                                    .filter { it.isDirectory() }
                                                    .forEach { path ->
                                                        val dimension = path.fileName.toString()
                                                        val key = runCatching { NamespacedKey.fromString("$namespace:$dimension") }
                                                            .getOrNull()
                                                        if (key != null && plugin.server.getWorld(key) == null) {
                                                            keys += key
                                                        }
                                                    }
                                            }
                                        }
                                }
                            }
                            for (key in keys.map { it.toString() }.sorted()) {
                                if (key.contains(remaining, true)) {
                                    builder.suggest("\"$key\"")
                                }
                            }
                            builder.build()
                        }
                    }
                }
                .executes { context ->
                    val sender = context.source.sender
                    val worldKey = precheck(sender, StringArgumentType.getString(context, "key"))
                        ?: return@executes 0

                    sender.sendMessage(support.prefixMessage("世界 $worldKey 加载中…"))
                    support.scope.launch {
                        try {
                            handleResult(sender, worldKey, worldManager.tryLoadWorld(worldKey))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("加载失败", e)
                            sender.sendMessage(support.prefixMessage("世界 $worldKey 加载失败：${e.message}"))
                        }
                    }
                    return@executes 1
                }
                .then(Commands.argument("chunk_generator", StringArgumentType.string())
                    .executes { context ->
                        val sender = context.source.sender
                        val worldKey = precheck(sender, StringArgumentType.getString(context, "key"))
                            ?: return@executes 0
                        val generator = StringArgumentType.getString(context, "chunk_generator")

                        sender.sendMessage(support.prefixMessage("世界 $worldKey 加载中…"))
                        support.scope.launch {
                            try {
                                handleResult(sender, worldKey, worldManager.tryLoadWorld(worldKey, generator))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                support.logger.error("加载失败", e)
                                sender.sendMessage(support.prefixMessage("世界 $worldKey 加载失败：${e.message}"))
                            }
                        }
                        return@executes 1
                    }
                )
        )
    }

    private suspend fun handleResult(sender: CommandSender, worldKey: NamespacedKey, result: LoadWorldResult) {
        when (result) {
            is LoadWorldResult.Success -> {
                worldManager.save()
                sender.sendMessage(support.prefixMessage("世界 $worldKey 加载完成"))
            }
            LoadWorldResult.AlreadyLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 已经加载"))
            }
            LoadWorldResult.Failed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 加载失败"))
            }
            LoadWorldResult.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界操作或保存加载在进行中，请稍后再试"))
            }
            LoadWorldResult.Unsupported -> {
                sender.sendMessage(support.prefixMessage("当前服务端（Folia）不支持加载世界"))
            }
        }
    }

    private fun precheck(sender: CommandSender, worldKey: String): NamespacedKey? {
        val key = runCatching { NamespacedKey.fromString(worldKey) }.getOrNull()
        if (key == null) {
            sender.sendMessage(support.prefixMessage("世界 $worldKey 不是合法 key"))
            return null
        }
        if (plugin.server.getWorld(key) != null) {
            sender.sendMessage(support.prefixMessage("世界 $key 已经加载"))
            return null
        }
        if (!plugin.server.levelDirectory.resolve("dimensions").resolve(key.namespace).resolve(key.key).isDirectory()) {
            sender.sendMessage(support.prefixMessage("没有找到世界 $key 的磁盘数据"))
            return null
        }
        return key
    }
}
