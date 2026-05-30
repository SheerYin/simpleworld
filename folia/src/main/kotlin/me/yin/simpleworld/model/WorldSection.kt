package me.yin.simpleworld.model

import kotlinx.serialization.Serializable

@Serializable
data class WorldSection(
    val load: Boolean = true,
    val seed: Long? = null,
    val environment: String = "NORMAL",
    val generator: String? = null,
    val difficulty: String? = null,
    val spawn: Position? = null,
    val gameRule: Map<String, String> = emptyMap(),
)
