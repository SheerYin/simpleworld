package me.yin.simpleworlds.world

import me.yin.simpleworlds.SimpleWorlds
import me.yin.simpleworlds.model.WorldConfig
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType

class WorldsService(
    private val simpleWorlds: SimpleWorlds,
    private val worldsStore: WorldsStore,
) {

    fun createWorld(
        name: String,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): World? {
        val worldCreator = WorldCreator(name)
        if (seed != null) {
            worldCreator.seed(seed)
        }
        worldCreator.environment(worldEnvironment)
        if (worldType != null) {
            worldCreator.type(worldType)
        }
        if (chunkGenerator != null) {
            worldCreator.generator(chunkGenerator)
        }
        val world = worldCreator.createWorld()
        if (world != null) {
            worldsStore.worldByName[name] = WorldConfig(name, chunkGenerator)
        }
        return world
    }

    fun loadWorld(name: String, chunkGenerator: String? = null): World? {
        val worldCreator = WorldCreator(name)
        if (chunkGenerator != null) {
            worldCreator.generator(chunkGenerator)
        }
        val world = worldCreator.createWorld()
        if (world != null) {
            worldsStore.worldByName[name] = WorldConfig(name, chunkGenerator)
        }
        return world
    }

    fun unloadWorld(name: String): Boolean {
        val success = simpleWorlds.server.unloadWorld(name, true)
        if (success) {
            worldsStore.worldByName.remove(name)
        }
        return success
    }
}
