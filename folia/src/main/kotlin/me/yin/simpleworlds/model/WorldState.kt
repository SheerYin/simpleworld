package me.yin.simpleworlds.model

import org.bukkit.World

class WorldState(
    val name: String,
    val world: World,
    val type: String?,
    var chunkGenerator: String? = null,
    val gameRules: HashMap<String, String> = hashMapOf()
)
