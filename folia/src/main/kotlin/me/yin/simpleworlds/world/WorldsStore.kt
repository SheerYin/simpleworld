package me.yin.simpleworlds.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.yin.simpleworlds.model.WorldSection
import org.bukkit.Difficulty
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

class WorldsStore(
    private val plugin: JavaPlugin,
    private val logger: Logger,
    private val json: Json,
    private val scope: CoroutineScope,
) {

    @Volatile
    var chunkGenerators = ConcurrentHashMap<String, String>()
        private set

    private val path: Path = plugin.dataPath.resolve("worlds.json")

    private val mutex = Mutex()

    private var saveJob: Job? = null

    suspend fun load() = mutex.withLock { doLoad() }

    fun tryLoad(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            doLoad()
        } finally {
            mutex.unlock()
        }
        return true
    }

    suspend fun save() = mutex.withLock { doSave() }

    fun trySave(): Boolean {
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
                delay(SAVE_INTERVAL)
                try {
                    save()
                } catch (e: Throwable) {
                    logger.error("保存失败", e)
                }
            }
        }
    }

    private fun doLoad() {
        val sections: Map<String, WorldSection> = if (!path.exists()) {
            emptyMap()
        } else {
            val text = Files.readString(path)
            if (text.isBlank()) emptyMap() else json.decodeFromString(text)
        }

        val newGenerators = ConcurrentHashMap<String, String>()
        for (world in plugin.server.worlds) {
            val section = sections[world.name] ?: continue
            applyConfig(world, section)
            if (section.generator != null) {
                newGenerators[world.name] = section.generator
            }
        }
        chunkGenerators = newGenerators
    }

    private fun doSave() {
        val sections = mutableMapOf<String, WorldSection>()
        for (world in plugin.server.worlds) {
            val gameRulesMap = mutableMapOf<String, String>()
            for (rule in Registry.GAME_RULE) {
                val value = world.getGameRuleValue(rule) ?: continue
                if (value != rule.defaultValue) {
                    gameRulesMap[rule.key.asString()] = value.toString()
                }
            }

            val spawnLocation = world.spawnLocation
            val spawn = WorldSection.SpawnLocation(
                x = spawnLocation.x,
                y = spawnLocation.y,
                z = spawnLocation.z,
                yaw = spawnLocation.yaw,
                pitch = spawnLocation.pitch,
            )

            sections[world.name] = WorldSection(
                seed = world.seed,
                environment = world.environment.name,
                generator = chunkGenerators[world.name],
                difficulty = world.difficulty.name,
                spawn = spawn,
                playerVersusPlayer = world.pvp,
                gameRule = gameRulesMap,
            )
        }
        Files.createDirectories(path.parent)
        Files.writeString(path, json.encodeToString(sections))
    }

    private fun applyConfig(world: World, section: WorldSection) {
        if (section.difficulty.isNotEmpty()) {
            world.difficulty = Difficulty.valueOf(section.difficulty)
        }
        val spawn = section.spawn
        if (spawn != null) {
            world.setSpawnLocation(
                Location(world, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch)
            )
        }
        if (section.playerVersusPlayer != null) {
            world.pvp = section.playerVersusPlayer
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

    companion object {
        val SAVE_INTERVAL = 30.minutes
    }
}
