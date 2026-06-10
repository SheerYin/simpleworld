package me.yin.simpleworld.model

import kotlinx.serialization.Serializable

@Serializable
class Position(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
)
