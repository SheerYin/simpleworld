package me.yin.simpleworld.world.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.future.future
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.yin.simpleworld.world.command.support.CommandSupport
import me.yin.simpleworld.world.manager.LoadWorldEvent
import me.yin.simpleworld.world.manager.WorldManager
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

                    worldManager.loadWorld(worldKey) { result ->
                        handleResult(sender, worldKey, result)
                    }
                    sender.sendMessage(support.prefixMessage("世界 $worldKey 加载已提交"))
                    return@executes 1
                }
        )
    }

    private fun handleResult(sender: CommandSender, worldKey: NamespacedKey, result: LoadWorldEvent) {
        when (result) {
            is LoadWorldEvent.Loaded -> {
                sender.sendMessage(support.prefixMessage("世界 ${result.world.key} 加载完成"))
            }
            LoadWorldEvent.AlreadyLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 已经加载"))
            }
            LoadWorldEvent.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界状态更新正在进行，请稍后再试"))
            }
            is LoadWorldEvent.InvalidEnvironment -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 的环境 ${result.value} 不能用于加载"))
            }
            is LoadWorldEvent.InvalidWorldType -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 的 Bukkit 类型 ${result.value} 不能用于加载"))
            }
            LoadWorldEvent.Failed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldKey 加载失败"))
            }
            is LoadWorldEvent.FailedWithException -> {
                support.logger.error("加载世界失败", result.exception)
                sender.sendMessage(support.prefixMessage("世界 $worldKey 加载失败：${result.exception.message}"))
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
