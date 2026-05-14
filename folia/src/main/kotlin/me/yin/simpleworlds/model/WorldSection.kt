package me.yin.simpleworlds.model

import kotlinx.serialization.Serializable

@Serializable
class WorldSection(
    val seed: Long? = null,
    val environment: String = "",
    val generator: String? = null,
    val difficulty: String = "",
    val spawn: SpawnLocation? = null,
    val playerVersusPlayer: Boolean? = null,
    val gameRule: HashMap<String, String> = hashMapOf(),
) {
    @Serializable
    class SpawnLocation(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float = 0f,
        val pitch: Float = 0f,
    )
}
