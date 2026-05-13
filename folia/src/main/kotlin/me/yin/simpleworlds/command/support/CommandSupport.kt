package me.yin.simpleworlds.command.support

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class CommandSupport(val plugin: JavaPlugin) {

    fun hasPermission(source: CommandSourceStack, permission: String): Boolean {
        val sender = source.sender
        return sender !is Player || sender.hasPermission(permission)
    }

    fun sendMessage(audience: Audience, component: Component) {
        audience.sendMessage(component)
    }

    fun requirePlayer(sender: CommandSender): Player? {
        if (sender !is Player) {
            sender.sendMessage(Component.text("此命令仅限玩家执行", NamedTextColor.RED))
            return null
        }
        return sender
    }
}
