package me.yin.simpleworld.model

import kotlinx.serialization.Serializable
import org.bukkit.NamespacedKey

@Serializable
data class WorldSection(
    val load: Boolean = true,
    val name: String? = null,
    val seed: Long? = null,
    val environment: String? = null,
    val bukkitWorldType: String? = null,
    val generator: String? = null,
    val difficulty: String? = null,
    val spawn: Position? = null,
    val gameRule: Map<@Serializable(with = NamespacedKeySerializer::class) NamespacedKey, String> = emptyMap(),
)
