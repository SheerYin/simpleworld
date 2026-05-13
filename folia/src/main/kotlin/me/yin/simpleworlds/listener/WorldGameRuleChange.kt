package me.yin.simpleworlds.listener

import io.papermc.paper.event.world.WorldGameRuleChangeEvent
import me.yin.simpleworlds.world.WorldsManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class WorldGameRuleChange(val worldsManager: WorldsManager) : Listener {

    @EventHandler
    fun onWorldGameRuleChange(event: WorldGameRuleChangeEvent) {
        val world = event.world
        val worldState = worldsManager.map1[world.name] ?: return

        val key = event.gameRule.key.asString()
        val value = event.value
        worldState.gameRules[key] = value
    }
}
