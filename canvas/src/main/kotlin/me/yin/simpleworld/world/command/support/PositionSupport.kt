package me.yin.simpleworld.world.command.support

import org.bukkit.Location
import org.bukkit.World

object PositionSupport {

    fun parseLocation(world: World, positionText: String): Location? {
        val parts = positionText.split(",").map { it.trim() }
        val size = parts.size
        if (size != 3 && size != 5) {
            return null
        }
        val x = parts[0].toDoubleOrNull()
        val y = parts[1].toDoubleOrNull()
        val z = parts[2].toDoubleOrNull()
        if (x == null || y == null || z == null) {
            return null
        }
        if (size == 3) {
            return Location(world, x, y, z, 0f, 0f)
        }
        val yaw = parts[3].toFloatOrNull()
            ?: return null
        val pitch = parts[4].toFloatOrNull()
            ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }
}
