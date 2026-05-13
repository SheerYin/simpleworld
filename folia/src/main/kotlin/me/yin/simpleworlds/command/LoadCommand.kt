package me.yin.simpleworlds.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworlds.command.support.CommandSupport
import me.yin.simpleworlds.world.WorldsManager
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

class LoadCommand(
    private val support: CommandSupport,
    private val worldsManager: WorldsManager
) {

    private val server = support.plugin.server

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("load")
            .requires { support.hasPermission(it, "simpleworlds.command.load") }
            .then(Commands.argument("name", StringArgumentType.word())
                .suggests { _, builder ->
                    val remaining = builder.remainingLowerCase
                    val worldContainer = server.worldContainer.toPath()
                    val loaded = server.worlds.map { it.name }.toSet()

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
                            e.printStackTrace()
                        }
                        builder.build()
                    }
                }
                .executes { context ->
                    val sender = context.source.sender
                    val worldName = StringArgumentType.getString(context, "name")
                    if (!precheck(sender, worldName)) return@executes 0

                    support.sendMessage(sender, Component.text("世界 $worldName 加载中…"))
                    worldsManager.loadWorld(worldName)
                    worldsManager.save()
                    support.sendMessage(sender, Component.text("世界 $worldName 加载完成"))
                    return@executes 1
                }
                .then(Commands.argument("chunk_generator", StringArgumentType.string())
                    .executes { context ->
                        val sender = context.source.sender
                        val worldName = StringArgumentType.getString(context, "name")
                        if (!precheck(sender, worldName)) return@executes 0
                        val generator = StringArgumentType.getString(context, "chunk_generator")

                        support.sendMessage(sender, Component.text("世界 $worldName 加载中…"))
                        worldsManager.loadWorld(worldName, generator)
                        worldsManager.save()
                        support.sendMessage(sender, Component.text("世界 $worldName 加载完成"))
                        return@executes 1
                    }
                )
            )
    }

    private fun precheck(sender: CommandSender, worldName: String): Boolean {
        if (server.getWorld(worldName) != null) {
            support.sendMessage(sender, Component.text("世界 $worldName 已经加载"))
            return false
        }
        val path = server.worldContainer.toPath().resolve(worldName).resolve("level.dat")
        if (Files.notExists(path)) {
            support.sendMessage(sender, Component.text("$worldName 不是世界，无法加载"))
            return false
        }
        return true
    }
}
