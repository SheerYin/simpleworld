package me.yin.simpleworld.world

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.yin.simpleworld.SimpleWorld
import me.yin.simpleworld.model.Position
import me.yin.simpleworld.model.WorldSection
import org.bukkit.Difficulty
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

sealed interface CreateWorldResult {
    data class Success(val world: World) : CreateWorldResult
    data object AlreadyLoaded : CreateWorldResult
    data object ExistsUnloaded : CreateWorldResult
    data object Failed : CreateWorldResult
    data object Busy : CreateWorldResult

    // Folia 不支持运行时创建世界（WorldCreator.createWorld 抛 UnsupportedOperationException）
    data object Unsupported : CreateWorldResult
}

sealed interface LoadWorldResult {
    data class Success(val world: World) : LoadWorldResult
    data object AlreadyLoaded : LoadWorldResult
    data object Failed : LoadWorldResult
    data object Busy : LoadWorldResult

    // Folia 不支持运行时加载世界（WorldCreator.createWorld 抛 UnsupportedOperationException）
    data object Unsupported : LoadWorldResult
}

sealed interface UnloadWorldResult {
    data object Success : UnloadWorldResult
    data object NotLoaded : UnloadWorldResult
    data object Failed : UnloadWorldResult
    data object Busy : UnloadWorldResult

    // Folia 不支持运行时卸载世界（RegionizedServer 没有 removeWorld）
    data object Unsupported : UnloadWorldResult
}

class WorldManager(
    private val plugin: SimpleWorld,
    private val logger: Logger,
    private val json: Json,
    private val scope: CoroutineScope,
) {

    // doSave 时名称匹配该正则的世界会被忽略；null 表示不忽略任何世界
    @Volatile
    var ignoreWorldRegex: Regex? = null

    // 自动保存的间隔
    @Volatile
    var saveInterval = 30.minutes

    @Volatile
    var generators = ConcurrentHashMap<String, String>()
        private set

    @Volatile
    var unloadedWorlds = ConcurrentHashMap<String, WorldSection>()
        private set

    private val path: Path = plugin.dataPath.resolve("worlds.json")

    private val mutex = Mutex()

    private var saveJob: Job? = null

    suspend fun load() = mutex.withLock { doLoad() }

    suspend fun tryLoad(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            doLoad()
        } finally {
            mutex.unlock()
        }
        return true
    }

    suspend fun save() = mutex.withLock { doSave() }

    suspend fun trySave(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            doSave()
        } finally {
            mutex.unlock()
        }
        return true
    }

    suspend fun shutdown() {
        saveJob?.cancelAndJoin()
        saveJob = null
        save()
    }

    fun startAutoSave() {
        saveJob = scope.launch {
            while (isActive) {
                delay(saveInterval)
                try {
                    save()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("保存失败", e)
                }
            }
        }
    }

    private fun recordGenerator(worldName: String, generator: String?) {
        if (generator == null) {
            generators.remove(worldName)
        } else {
            generators[worldName] = generator
        }
    }

    suspend fun createWorld(
        name: String,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): CreateWorldResult = mutex.withLock {
        plugin.runGlobalRegion {
            doCreateWorld(name, seed, worldEnvironment, worldType, chunkGenerator)
        }
    }

    suspend fun tryCreateWorld(
        name: String,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): CreateWorldResult {
        if (!mutex.tryLock()) return CreateWorldResult.Busy
        try {
            return plugin.runGlobalRegion {
                doCreateWorld(name, seed, worldEnvironment, worldType, chunkGenerator)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun doCreateWorld(
        name: String,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): CreateWorldResult {
        // Folia 上 WorldCreator.createWorld() 会抛 UnsupportedOperationException
        if (plugin.isFolia) {
            return CreateWorldResult.Unsupported
        }
        if (plugin.server.getWorld(name) != null) {
            return CreateWorldResult.AlreadyLoaded
        }
        if (unloadedWorlds.containsKey(name)) {
            return CreateWorldResult.ExistsUnloaded
        }
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
            unloadedWorlds.remove(world.name)
            recordGenerator(world.name, chunkGenerator)
        }
        if (world != null) {
            return CreateWorldResult.Success(world)
        } else {
            return CreateWorldResult.Failed
        }
    }

    suspend fun loadWorld(name: String, chunkGenerator: String? = null): LoadWorldResult = mutex.withLock {
        plugin.runGlobalRegion {
            doLoadWorld(name, chunkGenerator)
        }
    }

    suspend fun tryLoadWorld(name: String, chunkGenerator: String? = null): LoadWorldResult {
        if (!mutex.tryLock()) return LoadWorldResult.Busy
        try {
            return plugin.runGlobalRegion {
                doLoadWorld(name, chunkGenerator)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun doLoadWorld(name: String, chunkGenerator: String? = null): LoadWorldResult {
        // Folia 上 WorldCreator.createWorld() 会抛 UnsupportedOperationException
        if (plugin.isFolia) {
            return LoadWorldResult.Unsupported
        }
        if (plugin.server.getWorld(name) != null) {
            return LoadWorldResult.AlreadyLoaded
        }
        val worldCreator = WorldCreator(name)
        if (chunkGenerator != null) {
            worldCreator.generator(chunkGenerator)
        }
        val world = worldCreator.createWorld()
        if (world != null) {
            unloadedWorlds.remove(world.name)
            recordGenerator(world.name, chunkGenerator)
        }
        if (world != null) {
            return LoadWorldResult.Success(world)
        } else {
            return LoadWorldResult.Failed
        }
    }

    suspend fun unloadWorld(name: String): UnloadWorldResult = mutex.withLock {
        plugin.runGlobalRegion {
            doUnloadWorld(name)
        }
    }

    suspend fun tryUnloadWorld(name: String): UnloadWorldResult {
        if (!mutex.tryLock()) return UnloadWorldResult.Busy
        try {
            return plugin.runGlobalRegion {
                doUnloadWorld(name)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun doUnloadWorld(name: String): UnloadWorldResult {
        // Folia 架构上不支持运行时卸载世界：RegionizedServer 没有 removeWorld，
        // 世界绑定在区域 tick 线程上，CraftServer.unloadWorld 在 Folia 直接抛异常。
        if (plugin.isFolia) {
            return UnloadWorldResult.Unsupported
        }
        val world = plugin.server.getWorld(name)
            ?: return UnloadWorldResult.NotLoaded
        val section = sectionFromWorld(world, load = false)
        val success = plugin.server.unloadWorld(name, true)
        if (success) {
            unloadedWorlds[name] = section
        }
        return if (success) {
            UnloadWorldResult.Success
        } else {
            UnloadWorldResult.Failed
        }
    }

    private suspend fun doLoad() {
        val sections: Map<String, WorldSection> = if (!path.exists()) {
            emptyMap()
        } else {
            val text = Files.readString(path)
            if (text.isBlank()) {
                emptyMap()
            } else {
                json.decodeFromString(text)
            }
        }

        plugin.runGlobalRegion {
            val generatorMap = ConcurrentHashMap<String, String>()
            val unloadedWorldMap = ConcurrentHashMap<String, WorldSection>()
            for ((worldName, section) in sections) {
                val generator = section.generator
                if (generator != null) {
                    generatorMap[worldName] = generator
                }

                // getWorld 已存在 -> 不写入 unloadedWorlds；load = true 时应用配置
                val existing = plugin.server.getWorld(worldName)
                if (existing != null) {
                    if (section.load) {
                        applyConfig(existing, section)
                    }
                    continue
                }

                // load = false：既不创建世界，也不应用配置；仅在未加载时保留完整配置
                if (!section.load) {
                    unloadedWorldMap[worldName] = section
                    continue
                }

                val world = if (plugin.isFolia) {
                    null
                } else {
                    val worldCreator = WorldCreator(worldName)
                    val seed = section.seed
                    if (seed != null) {
                        worldCreator.seed(seed)
                    }
                    worldCreator.environment(
                        if (section.environment.isNotEmpty()) {
                            World.Environment.valueOf(section.environment)
                        } else {
                            World.Environment.NORMAL
                        }
                    )
                    if (generator != null) {
                        worldCreator.generator(generator)
                    }
                    val created = worldCreator.createWorld()
                    if (created != null) {
                        unloadedWorldMap.remove(created.name)
                    }
                    created
                }

                if (world != null) {
                    applyConfig(world, section)
                } else {
                    // 自动加载失败：保留完整配置，避免下一次保存丢失
                    unloadedWorldMap[worldName] = section
                }
            }
            generators = generatorMap
            unloadedWorlds = unloadedWorldMap
        }
    }

    private fun applyConfig(world: World, section: WorldSection) {
        val difficulty = section.difficulty
        if (difficulty != null) {
            world.difficulty = Difficulty.valueOf(difficulty)
        }
        val spawn = section.spawn
        if (spawn != null) {
            world.setSpawnLocation(
                Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch)
            )
        }
        for ((key, value) in section.gameRule) {
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
        }
    }

    private fun sectionFromWorld(world: World, load: Boolean): WorldSection {
        val gameRulesMap = mutableMapOf<String, String>()
        for (rule in Registry.GAME_RULE) {
            val value = try {
                world.getGameRuleValue(rule)
            } catch (_: IllegalArgumentException) {
                continue
            }
            if (value != rule.defaultValue) {
                gameRulesMap[rule.key.asString()] = value.toString()
            }
        }

        val spawnLocation = world.spawnLocation
        val spawn = Position(
            x = spawnLocation.x,
            y = spawnLocation.y,
            z = spawnLocation.z,
            yaw = spawnLocation.yaw,
            pitch = spawnLocation.pitch,
        )

        return WorldSection(
            load = load,
            seed = world.seed,
            environment = world.environment.name,
            generator = generators[world.name],
            difficulty = world.difficulty.name,
            spawn = spawn,
            gameRule = gameRulesMap,
        )
    }

    private suspend fun doSave() {
        val sections = mutableMapOf<String, WorldSection>()

        // 已加载世界从 World 取实时状态；未加载世界从 unloadedWorlds 保留完整配置
        // （忽略匹配 ignoreWorldRegex 的世界）
        plugin.runGlobalRegion {
            for (world in plugin.server.worlds) {
                val worldName = world.name
                if (ignoreWorldRegex?.matches(worldName) == true) continue

                sections[worldName] = sectionFromWorld(world, load = true)
            }

            for ((worldName, section) in unloadedWorlds) {
                if (ignoreWorldRegex?.matches(worldName) == true) continue
                if (plugin.server.getWorld(worldName) != null) continue

                sections[worldName] = section.copy(load = false)
            }
        }

        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("${path.fileName}.temporary")
        Files.writeString(
            temporary,
            json.encodeToString(sections),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.SYNC,
        )
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
