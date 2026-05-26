package me.yin.simpleworld.model

import kotlinx.serialization.Serializable

@Serializable
class WorldSection(
    val seed: Long? = null,
    val environment: String = "",
    val generator: String? = null,
    val difficulty: String = "",
    val spawn: Position? = null,
    val gameRule: MutableMap<String, String> = mutableMapOf(),
)
