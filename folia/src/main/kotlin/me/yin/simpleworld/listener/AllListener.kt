package me.yin.simpleworld.listener

import me.yin.simpleworld.SimpleWorld
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent

/**
 * 插件的事件监听器。
 *
 * 启动门禁：世界配置要到第一个全局 tick 才加载完成（见 [SimpleWorld.onEnable]），
 * 在 [SimpleWorld.ready] 置位前，挡下正在登录的玩家，避免他们撞上尚未套用配置的世界。
 *
 * 本事件在网络线程异步触发，这里只读一个 volatile 布尔、不碰任何世界对象，故无线程安全问题。
 */
class AllListener(private val plugin: SimpleWorld) : Listener {

    @EventHandler
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (plugin.ready) return
        // 仅在尚未被其它插件拒绝（如封禁、白名单）时才挡下，避免覆盖更具体的拒绝原因
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            Component.text("服务器正在启动，请稍候重连"),
        )
    }
}
