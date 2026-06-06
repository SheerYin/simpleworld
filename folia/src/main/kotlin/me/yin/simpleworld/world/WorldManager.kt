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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import me.yin.simpleworld.SimpleWorld
import me.yin.simpleworld.model.NamespacedKeySerializer
import me.yin.simpleworld.model.Position
import me.yin.simpleworld.model.WorldConfiguration
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

    // doSave 时 key 匹配该正则的世界会被忽略；null 表示不忽略任何世界
    @Volatile
    var ignoreWorldRegex: Regex? = null

    // 自动保存的间隔
    @Volatile
    var saveInterval = 30.minutes

    @Volatile
    var loadedGenerators = ConcurrentHashMap<NamespacedKey, String>()
        private set

    @Volatile
    var unloadedWorlds = ConcurrentHashMap<NamespacedKey, WorldConfiguration>()
        private set

    private val path: Path = plugin.dataPath.resolve("worlds.json")

    private val mutex = Mutex()

    private var saveJob: Job? = null

    private val configurationsSerializer = MapSerializer(NamespacedKeySerializer, WorldConfiguration.serializer())

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

    suspend fun createWorld(
        key: NamespacedKey,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): CreateWorldResult = mutex.withLock {
        plugin.runGlobalRegionAndWait {
            doCreateWorld(key, seed, worldEnvironment, worldType, chunkGenerator)
        }
    }

    suspend fun tryCreateWorld(
        key: NamespacedKey,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): CreateWorldResult {
        if (!mutex.tryLock()) return CreateWorldResult.Busy
        try {
            return plugin.runGlobalRegionAndWait {
                doCreateWorld(key, seed, worldEnvironment, worldType, chunkGenerator)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun doCreateWorld(
        key: NamespacedKey,
        seed: Long? = null,
        worldEnvironment: World.Environment = World.Environment.NORMAL,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): CreateWorldResult {
        // Folia 上 WorldCreator.createWorld() 会抛 UnsupportedOperationException
        if (plugin.isFolia) {
            return CreateWorldResult.Unsupported
        }
        if (plugin.server.getWorld(key) != null) {
            return CreateWorldResult.AlreadyLoaded
        }
        if (unloadedWorlds.containsKey(key)) {
            return CreateWorldResult.ExistsUnloaded
        }
        if (worldEnvironment == World.Environment.CUSTOM) {
            return CreateWorldResult.Failed
        }
        val worldCreator = WorldCreator.ofKey(key)
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
            val createdKey = world.key
            unloadedWorlds.remove(createdKey)
            if (chunkGenerator == null) {
                loadedGenerators.remove(createdKey)
            } else {
                loadedGenerators[createdKey] = chunkGenerator
            }
        }
        if (world != null) {
            return CreateWorldResult.Success(world)
        } else {
            return CreateWorldResult.Failed
        }
    }

    suspend fun loadWorld(key: NamespacedKey, chunkGenerator: String? = null): LoadWorldResult = mutex.withLock {
        plugin.runGlobalRegionAndWait {
            doLoadWorld(key, chunkGenerator)
        }
    }

    suspend fun tryLoadWorld(key: NamespacedKey, chunkGenerator: String? = null): LoadWorldResult {
        if (!mutex.tryLock()) return LoadWorldResult.Busy
        try {
            return plugin.runGlobalRegionAndWait {
                doLoadWorld(key, chunkGenerator)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun doLoadWorld(key: NamespacedKey, chunkGenerator: String? = null): LoadWorldResult {
        // Folia 上 WorldCreator.createWorld() 会抛 UnsupportedOperationException
        if (plugin.isFolia) {
            return LoadWorldResult.Unsupported
        }
        if (plugin.server.getWorld(key) != null) {
            return LoadWorldResult.AlreadyLoaded
        }
        val configuration = unloadedWorlds[key]
        val environmentText = configuration?.environment
        val environment = if (environmentText.isNullOrEmpty()) {
            when (key.toString()) {
                "minecraft:overworld" -> World.Environment.NORMAL
                "minecraft:the_nether" -> World.Environment.NETHER
                "minecraft:the_end" -> World.Environment.THE_END
                else -> World.Environment.NORMAL
            }
        } else {
            val resolved = runCatching { World.Environment.valueOf(environmentText) }.getOrNull()
            if (resolved == null) {
                logger.warn("世界 {} 的环境 {} 无效，跳过自动加载", key, environmentText)
                return LoadWorldResult.Failed
            }
            resolved
        }
        if (environment == World.Environment.CUSTOM) {
            logger.warn("世界 {} 的环境为 CUSTOM，Bukkit/Paper 不允许用 WorldCreator 创建该维度，跳过自动加载", key)
            return LoadWorldResult.Failed
        }
        val worldCreator = WorldCreator.ofKey(key)
        worldCreator.environment(environment)
        if (!configureWorldType(worldCreator, key, configuration)) {
            return LoadWorldResult.Failed
        }
        val seed = configuration?.seed
        if (seed != null) {
            worldCreator.seed(seed)
        }
        if (chunkGenerator != null) {
            worldCreator.generator(chunkGenerator)
        } else if (configuration?.generator != null) {
            worldCreator.generator(configuration.generator)
        }
        val world = worldCreator.createWorld()
        if (world != null) {
            val loadedKey = world.key
            unloadedWorlds.remove(loadedKey)
            val generator = chunkGenerator ?: configuration?.generator
            if (generator == null) {
                loadedGenerators.remove(loadedKey)
            } else {
                loadedGenerators[loadedKey] = generator
            }
            if (configuration != null) {
                applyWorldConfiguration(world, configuration)
            }
        }
        if (world != null) {
            return LoadWorldResult.Success(world)
        } else {
            return LoadWorldResult.Failed
        }
    }

    suspend fun unloadWorld(key: NamespacedKey): UnloadWorldResult = mutex.withLock {
        plugin.runGlobalRegionAndWait {
            doUnloadWorld(key)
        }
    }

    suspend fun tryUnloadWorld(key: NamespacedKey): UnloadWorldResult {
        if (!mutex.tryLock()) return UnloadWorldResult.Busy
        try {
            return plugin.runGlobalRegionAndWait {
                doUnloadWorld(key)
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun doUnloadWorld(key: NamespacedKey): UnloadWorldResult {
        // Folia 架构上不支持运行时卸载世界：RegionizedServer 没有 removeWorld，
        // 世界绑定在区域 tick 线程上，CraftServer.unloadWorld 在 Folia 直接抛异常。
        if (plugin.isFolia) {
            return UnloadWorldResult.Unsupported
        }
        val world = plugin.server.getWorld(key)
            ?: return UnloadWorldResult.NotLoaded
        val configuration = configurationFromWorld(world, loadOnStartup = false)
        val success = plugin.server.unloadWorld(world, true)
        if (success) {
            loadedGenerators.remove(world.key)
            unloadedWorlds[world.key] = configuration
        }
        return if (success) {
            UnloadWorldResult.Success
        } else {
            UnloadWorldResult.Failed
        }
    }

    private suspend fun doLoad() {
        val configurations: Map<NamespacedKey, WorldConfiguration> = if (!path.exists()) {
            emptyMap()
        } else {
            val text = Files.readString(path)
            if (text.isBlank()) {
                emptyMap()
            } else {
                json.decodeFromString(configurationsSerializer, text)
            }
        }

        plugin.runGlobalRegionAndWait {
            val loadedGeneratorMap = ConcurrentHashMap<NamespacedKey, String>()
            val unloadedWorldMap = ConcurrentHashMap<NamespacedKey, WorldConfiguration>()
            for ((worldKey, configuration) in configurations) {
                val generator = configuration.generator

                // getWorld 已存在 -> 不写入 unloadedWorlds；loadOnStartup = true 时应用配置
                val existing = plugin.server.getWorld(worldKey)
                if (existing != null) {
                    if (generator != null) {
                        loadedGeneratorMap[worldKey] = generator
                    }
                    if (configuration.loadOnStartup) {
                        applyWorldConfiguration(existing, configuration)
                    }
                    continue
                }

                // loadOnStartup = false：既不创建世界，也不应用配置；仅在未加载时保留完整配置
                if (!configuration.loadOnStartup) {
                    unloadedWorldMap[worldKey] = configuration
                    continue
                }

                val world = if (plugin.isFolia) {
                    null
                } else {
                    val environmentText = configuration.environment
                    if (environmentText.isNullOrEmpty()) {
                        logger.warn("世界 {} 未记录可创建环境，跳过 WorldCreator 加载", worldKey)
                        unloadedWorldMap[worldKey] = configuration
                        continue
                    }
                    val environment = runCatching { World.Environment.valueOf(environmentText) }.getOrNull()
                    if (environment == null) {
                        logger.warn("世界 {} 的环境 {} 无效，跳过自动加载", worldKey, environmentText)
                        unloadedWorldMap[worldKey] = configuration
                        continue
                    }
                    if (environment == World.Environment.CUSTOM) {
                        logger.warn("世界 {} 的环境为 CUSTOM，Bukkit/Paper 不允许用 WorldCreator 创建该维度，跳过自动加载", worldKey)
                        unloadedWorldMap[worldKey] = configuration
                        continue
                    }
                    val worldCreator = WorldCreator.ofKey(worldKey)
                    val seed = configuration.seed
                    if (seed != null) {
                        worldCreator.seed(seed)
                    }
                    worldCreator.environment(environment)
                    if (!configureWorldType(worldCreator, worldKey, configuration)) {
                        unloadedWorldMap[worldKey] = configuration
                        continue
                    }
                    if (generator != null) {
                        worldCreator.generator(generator)
                    }
                    val created = worldCreator.createWorld()
                    if (created != null) {
                        if (generator != null) {
                            loadedGeneratorMap[created.key] = generator
                        }
                        unloadedWorldMap.remove(created.key)
                    }
                    created
                }

                if (world != null) {
                    applyWorldConfiguration(world, configuration)
                } else {
                    // 自动加载失败：保留完整配置，避免下一次保存丢失
                    unloadedWorldMap[worldKey] = configuration
                }
            }
            loadedGenerators = loadedGeneratorMap
            unloadedWorlds = unloadedWorldMap
        }
    }

    private fun configureWorldType(worldCreator: WorldCreator, worldKey: NamespacedKey, configuration: WorldConfiguration?): Boolean {
        val worldTypeText = configuration?.bukkitWorldType
        if (worldTypeText.isNullOrEmpty()) {
            return true
        }
        val worldType = runCatching { WorldType.valueOf(worldTypeText) }.getOrNull()
        if (worldType == null) {
            logger.warn("世界 {} 的 Bukkit 类型 {} 无效，跳过 WorldCreator 加载", worldKey, worldTypeText)
            return false
        }
        worldCreator.type(worldType)
        return true
    }

    private fun applyWorldConfiguration(world: World, configuration: WorldConfiguration) {
        val difficulty = configuration.difficulty
        if (difficulty != null) {
            val resolvedDifficulty = runCatching { Difficulty.valueOf(difficulty) }.getOrNull()
            if (resolvedDifficulty == null) {
                logger.warn("世界 {} 的难度 {} 无效，跳过", world.key, difficulty)
            } else {
                world.difficulty = resolvedDifficulty
            }
        }
        val spawn = configuration.spawn
        if (spawn != null) {
            world.setSpawnLocation(
                Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch)
            )
        }
        for ((key, value) in configuration.gameRules) {
            val gameRule = Registry.GAME_RULE.get(key)
            if (gameRule == null) {
                logger.warn("世界 {} 的游戏规则 {} 不存在，跳过", world.key, key)
                continue
            }
            val typeClass = gameRule.type

            if (typeClass == Int::class.javaObjectType) {
                val resolvedValue = value.toIntOrNull()
                if (resolvedValue == null) {
                    logger.warn("世界 {} 的游戏规则 {} 值 {} 不是整数，跳过", world.key, key, value)
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                world.setGameRule(gameRule as GameRule<Int>, resolvedValue)
            } else if (typeClass == Boolean::class.javaObjectType) {
                val resolvedValue = when (value.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
                if (resolvedValue == null) {
                    logger.warn("世界 {} 的游戏规则 {} 值 {} 不是布尔值，跳过", world.key, key, value)
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                world.setGameRule(gameRule as GameRule<Boolean>, resolvedValue)
            } else {
                logger.warn("世界 {} 的游戏规则 {} 类型 {} 不支持，跳过", world.key, key, typeClass.name)
            }
        }
    }

    private fun configurationFromWorld(world: World, loadOnStartup: Boolean): WorldConfiguration {
        val gameRulesMap = mutableMapOf<NamespacedKey, String>()
        for (rule in Registry.GAME_RULE) {
            val value = try {
                world.getGameRuleValue(rule)
            } catch (_: IllegalArgumentException) {
                continue
            }
            if (value != rule.defaultValue) {
                gameRulesMap[rule.key] = value.toString()
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
        @Suppress("DEPRECATION")
        val bukkitWorldType = world.worldType?.name

        return WorldConfiguration(
            loadOnStartup = loadOnStartup,
            displayName = world.name,
            seed = world.seed,
            environment = world.environment.name,
            bukkitWorldType = bukkitWorldType,
            generator = loadedGenerators[world.key],
            difficulty = world.difficulty.name,
            spawn = spawn,
            gameRules = gameRulesMap,
        )
    }

    private suspend fun doSave() {
        val configurations = mutableMapOf<NamespacedKey, WorldConfiguration>()

        // 已加载世界从 World 取实时状态；未加载世界从 unloadedWorlds 保留完整配置
        // （忽略匹配 ignoreWorldRegex 的世界）
        plugin.runGlobalRegionAndWait {
            for (world in plugin.server.worlds) {
                val key = world.key
                if (ignoreWorldRegex?.matches(key.toString()) == true) continue

                configurations[key] = configurationFromWorld(world, loadOnStartup = true)
            }

            for ((key, configuration) in unloadedWorlds) {
                val keyText = key.toString()
                if (ignoreWorldRegex?.matches(keyText) == true) continue
                if (plugin.server.getWorld(key) != null) continue

                configurations[key] = configuration.copy(loadOnStartup = false)
            }
        }

        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("${path.fileName}.temporary")
        Files.writeString(
            temporary,
            json.encodeToString(configurationsSerializer, configurations),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.SYNC,
        )
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
