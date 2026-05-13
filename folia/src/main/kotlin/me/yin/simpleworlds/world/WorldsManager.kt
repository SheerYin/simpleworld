package me.yin.simpleworlds.world

import me.yin.simpleworlds.SimpleWorlds
import me.yin.simpleworlds.configuration.WorldsConfiguration
import me.yin.simpleworlds.model.WorldState
import org.bukkit.Difficulty
import org.bukkit.GameRule
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType

class WorldsManager(val simpleWorlds: SimpleWorlds, val worldsConfiguration: WorldsConfiguration) {

    val server = simpleWorlds.server

    val worldByName = hashMapOf<String, WorldState>()
    val sortedWorlds = arrayListOf<WorldState>()

    private var unmanagedSections = mapOf<String, WorldsConfiguration.Worlds.WorldSection>()

    private fun add(worldState: WorldState) {
        worldByName[worldState.name] = worldState
        val index = sortedWorlds.binarySearch { it.name.compareTo(worldState.name) }
        if (index < 0) {
            val insertionPoint = -(index + 1)
            sortedWorlds.add(insertionPoint, worldState)
        }
    }

    private fun remove(name: String): WorldState? {
        val worldState = worldByName.remove(name) ?: return null
        val index = sortedWorlds.binarySearch { it.name.compareTo(name) }
        if (index >= 0) {
            sortedWorlds.removeAt(index)
        }
        return worldState
    }

    fun load() {
        val worlds = worldsConfiguration.load()
        val unmanaged = hashMapOf<String, WorldsConfiguration.Worlds.WorldSection>()

        for ((name, worldSection) in worlds.worlds) {
            val world = server.getWorld(name)
            if (world == null) {
                // Folia 不允许运行时 createWorld，先把条目留住，save 时原样写回
                simpleWorlds.slF4JLogger.warn("世界 $name 未被服务端加载，跳过（请在 bukkit.yml 注册）")
                unmanaged[name] = worldSection
                continue
                // 原 Paper 路径：未加载则尝试 createWorld
                // val environment = if (worldSection.environment.isEmpty()) {
                //     World.Environment.NORMAL
                // } else {
                //     World.Environment.valueOf(worldSection.environment)
                // }
                // val type = worldSection.type?.let { WorldType.getByName(it) }
                // world = createWorld(name, worldSection.seed, environment, type, worldSection.generator)
                // if (world == null) continue
            }
            if (worldSection.difficulty.isNotEmpty()) {
                world.difficulty = Difficulty.valueOf(worldSection.difficulty)
            }

            val gameRules = hashMapOf<String, String>()
            for ((key, value) in worldSection.gameRule) {
                val namespacedKey = NamespacedKey.fromString(key) ?: continue
                val gameRule = Registry.GAME_RULE.get(namespacedKey) ?: continue
                val typeClass = gameRule.type

                if (typeClass == Int::class.javaObjectType) {
                    @Suppress("UNCHECKED_CAST")
                    world.setGameRule(gameRule as GameRule<Int>, value.toInt())
                } else if (typeClass == Boolean::class.javaObjectType) {
                    @Suppress("UNCHECKED_CAST")
                    world.setGameRule(gameRule as GameRule<Boolean>, value.toBoolean())
                }
                gameRules[key] = value
            }

            val worldState = WorldState(name, world, worldSection.type, worldSection.generator, gameRules)
            add(worldState)
        }

        unmanagedSections = unmanaged
    }

    fun save() {
        val worlds = WorldsConfiguration.Worlds()

        // 先放未加载的原始条目，避免被抹掉
        worlds.worlds.putAll(unmanagedSections)

        // 再用内存里管理中的世界覆盖
        for ((name, worldState) in worldByName) {
            val world = worldState.world
            val worldSection = WorldsConfiguration.Worlds.WorldSection(
                world.seed,
                world.environment.name,
                worldState.type,
                worldState.chunkGenerator,
                world.difficulty.name,
                worldState.gameRules
            )
            worlds.worlds[name] = worldSection
        }

        worldsConfiguration.save(worlds)
    }

    fun createWorld(
        name: String,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null
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
            val worldState = WorldState(name, world, worldType?.name, chunkGenerator)
            add(worldState)
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
            val worldState = WorldState(name, world, null, chunkGenerator)
            add(worldState)
        }
        return world
    }

    fun unloadWorld(name: String) {
        remove(name)
        server.unloadWorld(name, true)
    }
}
