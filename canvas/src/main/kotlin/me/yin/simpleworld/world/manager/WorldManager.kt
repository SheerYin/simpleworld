package me.yin.simpleworld.world.manager

import io.canvasmc.canvas.WorldUnloadResult
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
import me.yin.simpleworld.world.model.WorldConfiguration
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

sealed interface UnloadWorldEvent {
    data class Completed(val result: WorldUnloadResult) : UnloadWorldEvent
    data object NotLoaded : UnloadWorldEvent
    data class FailedWithException(val exception: Exception) : UnloadWorldEvent
}

sealed interface CreateWorldEvent {
    data class Created(val world: World) : CreateWorldEvent
    data object AlreadyLoaded : CreateWorldEvent
    data object Busy : CreateWorldEvent
    data class InvalidEnvironment(val value: String) : CreateWorldEvent
    data class InvalidWorldType(val value: String) : CreateWorldEvent
    data object Failed : CreateWorldEvent
    data class FailedWithException(val exception: Exception) : CreateWorldEvent
}

sealed interface LoadWorldEvent {
    data class Loaded(val world: World) : LoadWorldEvent
    data object AlreadyLoaded : LoadWorldEvent
    data object Busy : LoadWorldEvent
    data class InvalidEnvironment(val value: String) : LoadWorldEvent
    data class InvalidWorldType(val value: String) : LoadWorldEvent
    data object Failed : LoadWorldEvent
    data class FailedWithException(val exception: Exception) : LoadWorldEvent
}

sealed interface RemoveUnloadedWorldEvent {
    data object Removed : RemoveUnloadedWorldEvent
    data object NotFound : RemoveUnloadedWorldEvent
    data class FailedWithException(val exception: Exception) : RemoveUnloadedWorldEvent
}

private sealed interface WorldEnvironmentResolution {
    data class Resolved(val environment: World.Environment?) : WorldEnvironmentResolution
    data object Missing : WorldEnvironmentResolution
    data class Invalid(val value: String) : WorldEnvironmentResolution
    data object Custom : WorldEnvironmentResolution
}

private sealed interface WorldTypeResolution {
    data class Resolved(val worldType: WorldType?) : WorldTypeResolution
    data class Invalid(val value: String) : WorldTypeResolution
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

    fun load() {
        tryLoadWorlds()
    }

    fun tryLoadWorlds() {
        val configurations: Map<NamespacedKey, WorldConfiguration> = try {
            if (!path.exists()) {
                emptyMap()
            } else {
                val text = Files.readString(path)
                if (text.isBlank()) {
                    emptyMap()
                } else {
                    json.decodeFromString(configurationsSerializer, text)
                }
            }
        } catch (exception: Exception) {
            logger.error("世界配置读取失败", exception)
            return
        }

        plugin.globalRegionScheduler {
            try {
                if (!mutex.tryLock()) {
                    logger.warn("世界配置加载跳过：已有世界状态更新正在进行")
                    return@globalRegionScheduler
                }
                try {
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

                        val environment = when (
                            val resolution = resolveWorldEnvironment(
                                key = worldKey,
                                environmentText = configuration.environment,
                                fallbackToVanillaEnvironment = false,
                            )
                        ) {
                            is WorldEnvironmentResolution.Resolved -> resolution.environment
                            WorldEnvironmentResolution.Missing -> {
                                logger.warn("世界 {} 未记录可创建环境，跳过 WorldCreator 加载", worldKey)
                                unloadedWorldMap[worldKey] = configuration
                                continue
                            }
                            is WorldEnvironmentResolution.Invalid -> {
                                logger.warn("世界 {} 的环境 {} 无效，跳过自动加载", worldKey, resolution.value)
                                unloadedWorldMap[worldKey] = configuration
                                continue
                            }
                            WorldEnvironmentResolution.Custom -> {
                                logger.warn("世界 {} 的环境为 CUSTOM，Bukkit/Paper 不允许用 WorldCreator 创建该维度，跳过自动加载", worldKey)
                                unloadedWorldMap[worldKey] = configuration
                                continue
                            }
                        }

                        val resolvedWorldType = when (val resolution = resolveWorldType(configuration.bukkitWorldType)) {
                            is WorldTypeResolution.Resolved -> resolution.worldType
                            is WorldTypeResolution.Invalid -> {
                                logger.warn("世界 {} 的 Bukkit 类型 {} 无效，跳过 WorldCreator 加载", worldKey, resolution.value)
                                unloadedWorldMap[worldKey] = configuration
                                continue
                            }
                        }

                        val world = doCreateWorld(
                            key = worldKey,
                            seed = configuration.seed,
                            worldEnvironment = environment,
                            worldType = resolvedWorldType,
                            chunkGenerator = generator,
                        )
                        if (world != null) {
                            if (generator != null) {
                                loadedGeneratorMap[world.key] = generator
                            }
                            unloadedWorldMap.remove(world.key)
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
                    logger.info("世界配置加载完成")
                } finally {
                    mutex.unlock()
                }
            } catch (exception: Exception) {
                logger.error("世界配置加载任务执行失败", exception)
            }
        }
    }

    fun trySaveWorlds() {
        // 已加载世界从 World 取实时状态；未加载世界从 unloadedWorlds 保留完整配置
        // （忽略匹配 ignoreWorldRegex 的世界）
        plugin.globalRegionScheduler {
            try {
                if (!mutex.tryLock()) {
                    logger.warn("世界配置保存跳过：已有世界状态更新正在进行")
                    return@globalRegionScheduler
                }
                try {
                    doSaveWorlds()
                } finally {
                    mutex.unlock()
                }
            } catch (exception: Exception) {
                logger.error("世界配置保存失败", exception)
            }
        }
    }

    suspend fun saveWorlds() {
        try {
            mutex.withLock {
                doSaveWorlds()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("世界配置保存失败", exception)
        }
    }

    suspend fun shutdown() {
        saveJob?.cancelAndJoin()
        saveJob = null
        saveWorlds()
    }

    fun startAutoSave() {
        saveJob = scope.launch {
            while (isActive) {
                delay(saveInterval)
                try {
                    trySaveWorlds()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("保存失败", e)
                }
            }
        }
    }

    private fun doSaveWorlds() {
        val configurations = mutableMapOf<NamespacedKey, WorldConfiguration>()
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
        logger.info("世界配置保存完成")
    }

    fun createWorld(
        key: NamespacedKey,
        seed: Long? = null,
        worldEnvironmentText: String? = null,
        worldTypeText: String? = null,
        chunkGenerator: String? = null,
        callback: ((CreateWorldEvent) -> Unit)? = null,
    ) {
        plugin.globalRegionScheduler {
            try {
                if (!mutex.tryLock()) {
                    logger.warn("世界 {} 创建跳过：已有世界状态更新正在进行", key)
                    callback?.invoke(CreateWorldEvent.Busy)
                    return@globalRegionScheduler
                }
                try {
                    if (plugin.server.getWorld(key) != null) {
                        logger.info("世界 {} 已经加载，跳过创建", key)
                        callback?.invoke(CreateWorldEvent.AlreadyLoaded)
                        return@globalRegionScheduler
                    }

                    val worldEnvironment = if (worldEnvironmentText.isNullOrEmpty()) {
                        null
                    } else {
                        when (
                            val resolution = resolveWorldEnvironment(
                                key = key,
                                environmentText = worldEnvironmentText,
                                fallbackToVanillaEnvironment = false,
                            )
                        ) {
                            is WorldEnvironmentResolution.Resolved -> resolution.environment
                            WorldEnvironmentResolution.Missing -> null
                            is WorldEnvironmentResolution.Invalid -> {
                                logger.warn("世界 {} 的环境 {} 无效，跳过创建", key, resolution.value)
                                callback?.invoke(CreateWorldEvent.InvalidEnvironment(resolution.value))
                                return@globalRegionScheduler
                            }
                            WorldEnvironmentResolution.Custom -> {
                                logger.warn("世界 {} 的环境为 CUSTOM，Bukkit/Paper 不允许用 WorldCreator 创建该维度，跳过创建", key)
                                callback?.invoke(CreateWorldEvent.InvalidEnvironment(World.Environment.CUSTOM.name))
                                return@globalRegionScheduler
                            }
                        }
                    }

                    val worldType = when (val resolution = resolveWorldType(worldTypeText)) {
                        is WorldTypeResolution.Resolved -> resolution.worldType
                        is WorldTypeResolution.Invalid -> {
                            logger.warn("世界 {} 的 Bukkit 类型 {} 无效，跳过创建", key, resolution.value)
                            callback?.invoke(CreateWorldEvent.InvalidWorldType(resolution.value))
                            return@globalRegionScheduler
                        }
                    }

                    val world = doCreateWorld(
                        key = key,
                        seed = seed,
                        worldEnvironment = worldEnvironment,
                        worldType = worldType,
                        chunkGenerator = chunkGenerator,
                    )
                    if (world != null) {
                        val createdKey = world.key
                        if (chunkGenerator != null) {
                            loadedGenerators[createdKey] = chunkGenerator
                        }
                        logger.info("世界 {} 创建完成", createdKey)
                        callback?.invoke(CreateWorldEvent.Created(world))
                    } else {
                        logger.warn("世界 {} 创建失败", key)
                        callback?.invoke(CreateWorldEvent.Failed)
                    }
                } finally {
                    mutex.unlock()
                }
            } catch (exception: Exception) {
                logger.error("世界 {} 创建任务执行失败", key, exception)
                callback?.invoke(CreateWorldEvent.FailedWithException(exception))
            }
        }
    }

    private fun doCreateWorld(
        key: NamespacedKey,
        seed: Long? = null,
        worldEnvironment: World.Environment? = null,
        worldType: WorldType? = null,
        chunkGenerator: String? = null,
    ): World? {
        val worldCreator = WorldCreator.ofKey(key)
        if (seed != null) {
            worldCreator.seed(seed)
        }
        if (worldEnvironment != null) {
            worldCreator.environment(worldEnvironment)
        }
        if (worldType != null) {
            worldCreator.type(worldType)
        }
        if (chunkGenerator != null) {
            worldCreator.generator(chunkGenerator)
        }
        return worldCreator.createWorld()
    }

    fun loadWorld(
        key: NamespacedKey,
        callback: ((LoadWorldEvent) -> Unit)? = null,
    ) {
        plugin.globalRegionScheduler {
            try {
                if (!mutex.tryLock()) {
                    logger.warn("世界 {} 加载跳过：已有世界状态更新正在进行", key)
                    callback?.invoke(LoadWorldEvent.Busy)
                    return@globalRegionScheduler
                }
                try {
                    if (plugin.server.getWorld(key) != null) {
                        logger.info("世界 {} 已经加载，跳过加载", key)
                        callback?.invoke(LoadWorldEvent.AlreadyLoaded)
                        return@globalRegionScheduler
                    }

                    val configuration = unloadedWorlds[key]
                    val environment = when (
                        val resolution = resolveWorldEnvironment(
                            key = key,
                            environmentText = configuration?.environment,
                            fallbackToVanillaEnvironment = true,
                        )
                    ) {
                        is WorldEnvironmentResolution.Resolved -> resolution.environment
                        WorldEnvironmentResolution.Missing -> {
                            logger.warn("世界 {} 未记录可创建环境，跳过加载", key)
                            callback?.invoke(LoadWorldEvent.Failed)
                            return@globalRegionScheduler
                        }
                        is WorldEnvironmentResolution.Invalid -> {
                            logger.warn("世界 {} 的环境 {} 无效，跳过加载", key, resolution.value)
                            callback?.invoke(LoadWorldEvent.InvalidEnvironment(resolution.value))
                            return@globalRegionScheduler
                        }
                        WorldEnvironmentResolution.Custom -> {
                            logger.warn("世界 {} 的环境为 CUSTOM，Bukkit/Paper 不允许用 WorldCreator 加载该维度，跳过加载", key)
                            callback?.invoke(LoadWorldEvent.InvalidEnvironment(World.Environment.CUSTOM.name))
                            return@globalRegionScheduler
                        }
                    }

                    val resolvedWorldType = when (val resolution = resolveWorldType(configuration?.bukkitWorldType)) {
                        is WorldTypeResolution.Resolved -> resolution.worldType
                        is WorldTypeResolution.Invalid -> {
                            logger.warn("世界 {} 的 Bukkit 类型 {} 无效，跳过加载", key, resolution.value)
                            callback?.invoke(LoadWorldEvent.InvalidWorldType(resolution.value))
                            return@globalRegionScheduler
                        }
                    }

                    val generator = configuration?.generator
                    val world = doCreateWorld(
                        key = key,
                        seed = configuration?.seed,
                        worldEnvironment = environment,
                        worldType = resolvedWorldType,
                        chunkGenerator = generator,
                    )
                    if (world != null) {
                        val loadedKey = world.key
                        if (configuration != null) {
                            unloadedWorlds.remove(loadedKey)
                        }
                        if (generator == null) {
                            loadedGenerators.remove(loadedKey)
                        } else {
                            loadedGenerators[loadedKey] = generator
                        }
                        if (configuration != null) {
                            applyWorldConfiguration(world, configuration)
                        }
                        logger.info("世界 {} 加载完成", loadedKey)
                        callback?.invoke(LoadWorldEvent.Loaded(world))
                    } else {
                        logger.warn("世界 {} 加载失败", key)
                        callback?.invoke(LoadWorldEvent.Failed)
                    }
                } finally {
                    mutex.unlock()
                }
            } catch (exception: Exception) {
                logger.error("世界 {} 加载任务执行失败", key, exception)
                callback?.invoke(LoadWorldEvent.FailedWithException(exception))
            }
        }
    }

    fun unloadWorld(
        key: NamespacedKey,
        callback: ((UnloadWorldEvent) -> Unit)? = null,
    ) {
        plugin.globalRegionScheduler {
            val world = plugin.server.getWorld(key)
            if (world == null) {
                logger.info("世界 {} 未加载，跳过卸载", key)
                callback?.invoke(UnloadWorldEvent.NotLoaded)
                return@globalRegionScheduler
            }

            val worldKey = world.key
            val configuration = configurationFromWorld(world, loadOnStartup = false)
            try {
                plugin.server.unloadWorldAsync(world, true) { result ->
                    if (result.isSuccess) {
                        scope.launch {
                            mutex.withLock {
                                loadedGenerators.remove(worldKey)
                                unloadedWorlds[worldKey] = configuration
                            }
                            callback?.invoke(UnloadWorldEvent.Completed(result))
                        }
                    } else {
                        callback?.invoke(UnloadWorldEvent.Completed(result))
                    }
                }
            } catch (exception: Exception) {
                logger.error("世界 {} 卸载调度失败", key, exception)
                callback?.invoke(UnloadWorldEvent.FailedWithException(exception))
            }
        }
    }

    fun removeUnloadedWorld(
        key: NamespacedKey,
        callback: ((RemoveUnloadedWorldEvent) -> Unit)? = null,
    ) {
        scope.launch {
            try {
                val removed = mutex.withLock {
                    unloadedWorlds.remove(key) != null
                }
                if (removed) {
                    callback?.invoke(RemoveUnloadedWorldEvent.Removed)
                } else {
                    callback?.invoke(RemoveUnloadedWorldEvent.NotFound)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.error("世界 {} 未加载配置移除失败", key, exception)
                callback?.invoke(RemoveUnloadedWorldEvent.FailedWithException(exception))
            }
        }
    }

    private fun resolveWorldEnvironment(
        key: NamespacedKey,
        environmentText: String?,
        fallbackToVanillaEnvironment: Boolean,
    ): WorldEnvironmentResolution {
        if (environmentText.isNullOrEmpty()) {
            if (!fallbackToVanillaEnvironment) {
                return WorldEnvironmentResolution.Missing
            }
            val environment = when (key.toString()) {
                "minecraft:overworld" -> World.Environment.NORMAL
                "minecraft:the_nether" -> World.Environment.NETHER
                "minecraft:the_end" -> World.Environment.THE_END
                else -> null
            }
            return WorldEnvironmentResolution.Resolved(environment)
        }

        val environment = runCatching { World.Environment.valueOf(environmentText) }.getOrNull()
            ?: return WorldEnvironmentResolution.Invalid(environmentText)
        if (environment == World.Environment.CUSTOM) {
            return WorldEnvironmentResolution.Custom
        }
        return WorldEnvironmentResolution.Resolved(environment)
    }

    private fun resolveWorldType(worldTypeText: String?): WorldTypeResolution {
        if (worldTypeText.isNullOrEmpty()) {
            return WorldTypeResolution.Resolved(null)
        }

        val worldType = runCatching { WorldType.valueOf(worldTypeText) }.getOrNull()
            ?: return WorldTypeResolution.Invalid(worldTypeText)
        return WorldTypeResolution.Resolved(worldType)
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

}
