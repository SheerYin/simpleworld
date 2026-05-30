package me.yin.simpleworld.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.yin.simpleworld.command.support.CommandSupport
import me.yin.simpleworld.model.WorldSection
import me.yin.simpleworld.world.WorldManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.World
import org.bukkit.command.CommandSender

class ListCommand(
    private val support: CommandSupport,
    private val worldManager: WorldManager,
) {

    fun root(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("list")
            .requires { support.hasPermission(it, PERMISSION) }
            .executes { context ->
                handle(context.source.sender)
                return@executes 1
            }
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes { context ->
                    handle(context.source.sender, IntegerArgumentType.getInteger(context, "page"))
                    return@executes 1
                }
            )
    }

    private fun handle(sender: CommandSender, page: Int = 1, pageSize: Int = 10) {
        val loadedWorlds = support.plugin.server.worlds.associateBy { it.name }
        val entries = (loadedWorlds.keys + worldManager.unloadedWorlds.keys)
            .toSet()
            .sorted()
            .mapNotNull { name ->
                val world = loadedWorlds[name]
                if (world != null) {
                    WorldListEntry.Loaded(world)
                } else {
                    val section = worldManager.unloadedWorlds[name] ?: return@mapNotNull null
                    WorldListEntry.Unloaded(name, section)
                }
            }
        val count = entries.size
        if (count == 0) {
            sender.sendMessage(support.prefixMessage("没有世界"))
            return
        }

        val totalPages = ((count + pageSize - 1) / pageSize).coerceAtLeast(1)
        if (page !in 1..totalPages) {
            sender.sendMessage(support.prefixMessage("页码 $page 错误！请输入 1 到 $totalPages 页码"))
            return
        }

        sender.sendMessage(
            Component.text("所有世界 ").append(Component.text("（共 $count 个）", NamedTextColor.GRAY))
        )

        val generators = worldManager.generators
        val start = (page - 1) * pageSize
        val end = (start + pageSize).coerceAtMost(count)
        entries.subList(start, end).forEach { entry ->
            when (entry) {
                is WorldListEntry.Loaded -> {
                    val world = entry.world
                    val name = world.name
                    val seed = world.seed
                    @Suppress("DEPRECATION")
                    val type = world.worldType?.name ?: "Default | Unknown"
                    val generator = generators[name] ?: "Default | Unknown"
                    val difficulty = world.difficulty.name
                    val environment = world.environment.name

                    val hover = Component.text("种子: $seed\n环境: $environment\n类型: $type\n生成器: $generator\n难度: $difficulty")
                    val worldPart = Component.text(" - ")
                        .append(Component.text(name).hoverEvent(HoverEvent.showText(hover)))
                    val teleportPart = Component.text(" [传送]", NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(Component.text("点击传送", NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.runCommand("/simpleworld teleport ${sender.name} $name"))

                    sender.sendMessage(worldPart.append(teleportPart))
                }
                is WorldListEntry.Unloaded -> {
                    val name = entry.name
                    val section = entry.section
                    val seed = section.seed?.toString() ?: "Default | Unknown"
                    val generator = section.generator ?: "Default | Unknown"
                    val difficulty = section.difficulty ?: "Default | Unknown"
                    val hover = Component.text(
                        "种子: $seed\n环境: ${section.environment}\n生成器: $generator\n难度: $difficulty\n状态: 未加载"
                    )
                    val worldPart = Component.text(" - ")
                        .append(Component.text(name, NamedTextColor.RED).hoverEvent(HoverEvent.showText(hover)))

                    sender.sendMessage(worldPart)
                }
            }
        }

        val previousPage = (page - 1).coerceAtLeast(1)
        val nextPage = (page + 1).coerceAtMost(totalPages)

        val previousBtn = Component.text("[上一页]", NamedTextColor.GREEN)
            .hoverEvent(HoverEvent.showText(Component.text("点击前往上一页", NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.runCommand("/simpleworld list $previousPage"))
        val nextBtn = Component.text("[下一页]", NamedTextColor.GREEN)
            .hoverEvent(HoverEvent.showText(Component.text("点击前往下一页", NamedTextColor.GRAY)))
            .clickEvent(ClickEvent.runCommand("/simpleworld list $nextPage"))

        val footer = Component.empty()
            .append(previousBtn)
            .append(Component.text(" [$page/$totalPages] "))
            .append(nextBtn)
        sender.sendMessage(footer)
    }

    companion object {
        const val PERMISSION = "simpleworld.command.list"
    }

    private sealed interface WorldListEntry {
        data class Loaded(val world: World) : WorldListEntry
        data class Unloaded(val name: String, val section: WorldSection) : WorldListEntry
    }
}
