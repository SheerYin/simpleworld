package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.world.LoadWorldResult
import me.yin.simpleworld.world.WorldManager
import org.bukkit.command.CommandSender
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

class LoadCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    private val plugin = support.plugin

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("load")
            .requires { support.hasPermission(it, PERMISSION) }
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    val worldContainer = plugin.server.worldContainer.toPath()
                    val loaded = plugin.server.worlds.map { it.name }.toSet()

                    CompletableFuture.supplyAsync {
                        try {
                            Files.list(worldContainer).use { stream ->
                                stream
                                    .filter { Files.isDirectory(it) }
                                    .filter { Files.exists(it.resolve("level.dat")) }
                                    .map { it.name }
                                    .filter { it !in loaded }
                                    .filter { it.startsWith(remaining, true) }
                                    .forEach { builder.suggest(it) }
                            }
                        } catch (e: Exception) {
                            support.logger.warn("列出世界目录失败", e)
                        }
                        builder.build()
                    }
                }
                .executes { context ->
                    val sender = context.source.sender
                    val worldName = StringArgumentType.getString(context, "name")
                    if (!precheck(sender, worldName)) return@executes 0

                    sender.sendMessage(support.prefixMessage("世界 $worldName 加载中…"))
                    support.scope.launch {
                        try {
                            handleResult(sender, worldName, worldManager.tryLoadWorld(worldName))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            support.logger.error("加载失败", e)
                            sender.sendMessage(support.prefixMessage("世界 $worldName 加载失败：${e.message}"))
                        }
                    }
                    return@executes 1
                }
                .then(Commands.argument("chunk_generator", StringArgumentType.string())
                    .executes { context ->
                        val sender = context.source.sender
                        val worldName = StringArgumentType.getString(context, "name")
                        if (!precheck(sender, worldName)) return@executes 0
                        val generator = StringArgumentType.getString(context, "chunk_generator")

                        sender.sendMessage(support.prefixMessage("世界 $worldName 加载中…"))
                        support.scope.launch {
                            try {
                                handleResult(sender, worldName, worldManager.tryLoadWorld(worldName, generator))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                support.logger.error("加载失败", e)
                                sender.sendMessage(support.prefixMessage("世界 $worldName 加载失败：${e.message}"))
                            }
                        }
                        return@executes 1
                    }
                )
            )
    }

    private suspend fun handleResult(sender: CommandSender, worldName: String, result: LoadWorldResult) {
        when (result) {
            is LoadWorldResult.Success -> {
                worldManager.save()
                sender.sendMessage(support.prefixMessage("世界 $worldName 加载完成"))
            }
            LoadWorldResult.AlreadyLoaded -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 已经加载"))
            }
            LoadWorldResult.Failed -> {
                sender.sendMessage(support.prefixMessage("世界 $worldName 加载失败"))
            }
            LoadWorldResult.Busy -> {
                sender.sendMessage(support.prefixMessage("已有世界操作或保存加载在进行中，请稍后再试"))
            }
        }
    }

    private fun precheck(sender: CommandSender, worldName: String): Boolean {
        if (plugin.server.getWorld(worldName) != null) {
            sender.sendMessage(support.prefixMessage("世界 $worldName 已经加载"))
            return false
        }
        val path = plugin.server.worldContainer.toPath().resolve(worldName).resolve("level.dat")
        if (Files.notExists(path)) {
            sender.sendMessage(support.prefixMessage("$worldName 不是世界，无法加载"))
            return false
        }
        return true
    }

    companion object {
        const val PERMISSION = "simpleworld.command.load"
    }
}
