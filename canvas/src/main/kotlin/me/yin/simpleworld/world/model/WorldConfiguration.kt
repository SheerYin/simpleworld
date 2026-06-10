package me.yin.simpleworld.world.model

import kotlinx.serialization.Serializable
import me.yin.simpleworld.model.NamespacedKeySerializer
import me.yin.simpleworld.model.Position
import org.bukkit.NamespacedKey

@Serializable
data class WorldConfiguration(
    val loadOnStartup: Boolean = true,
    val displayName: String? = null,
    val seed: Long? = null,
    val environment: String? = null,
    val bukkitWorldType: String? = null,
    val generator: String? = null,
    val difficulty: String? = null,
    val spawn: Position? = null,
    val gameRules: Map<@Serializable(with = NamespacedKeySerializer::class) NamespacedKey, String> = emptyMap(),
)
