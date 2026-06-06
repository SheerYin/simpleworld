package me.yin.simpleworld

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import me.yin.simpleworld.command.SimpleWorldCommand
import me.yin.simpleworld.listener.AllListener
import me.yin.simpleworld.world.WorldManager
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class SimpleWorld : JavaPlugin() {

    var worldManager: WorldManager? = null
    var scope: CoroutineScope? = null

    // 世界配置要到第一个全局 tick 才加载完成；在此之前由 AllListener 挡下登录玩家
    @Volatile
    var ready = false
        private set

    @Volatile
    private var worldConfigLoaded = false

    // onDisable 时等待保存完成的最长时间
    @Volatile
    var shutdownTimeout = 10.seconds

    val isFolia: Boolean = listOf(
        "io.papermc.paper.threadedregions.RegionizedServer",
        "io.papermc.paper.threadedregions.scheduler.FoliaGlobalRegionScheduler",
    ).any { className ->
        try {
            Class.forName(className)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override fun onEnable() {
        val logger = slF4JLogger
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val worldManager = WorldManager(this, logger, json, scope)
        this.scope = scope
        this.worldManager = worldManager
        
        server.pluginManager.registerEvents(AllListener(this), this)

        scope.launch {
            try {
                ready = false
                worldConfigLoaded = false
                worldManager.load()
                worldConfigLoaded = true
                worldManager.startAutoSave()
                ready = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("世界配置加载失败，禁用插件", e)
                ready = false
                if (isEnabled) {
                    server.globalRegionScheduler.run(this@SimpleWorld) {
                        server.pluginManager.disablePlugin(this@SimpleWorld)
                    }
                }
            }
        }

        SimpleWorldCommand(this, logger, prefix, scope, worldManager).register()

        logger.info("Enabled {} {}", prefix, pluginMeta.version)
    }

    override fun onDisable() {
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled {} {}", prefix, pluginMeta.version)
        if (worldConfigLoaded) {
            slF4JLogger.info("开始保存,最多等待 {}", shutdownTimeout)
            try {
                runBlocking {
                    withTimeout(shutdownTimeout) { worldManager?.shutdown() }
                }
            } catch (e: TimeoutCancellationException) {
                slF4JLogger.error("shutdown 超时 {},放弃", shutdownTimeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                slF4JLogger.error("关闭失败", e)
            }
        } else {
            slF4JLogger.warn("世界配置未成功加载，跳过保存")
        }
        scope?.cancel()
        this.scope = null
        this.worldManager = null
        worldConfigLoaded = false
    }

    suspend fun <T> runGlobalRegionAndWait(block: () -> T): T {
        if (server.isGlobalTickThread || !isEnabled) {
            return block()
        }
        return suspendCancellableCoroutine { continuation ->
            val scheduledTask = server.globalRegionScheduler.run(this) { _ ->
                try {
                    continuation.resume(block())
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
            continuation.invokeOnCancellation { scheduledTask.cancel() }
        }
    }

    suspend fun <T> runRegionAndWait(world: World, chunkX: Int, chunkZ: Int, block: () -> T): T {
        if (server.isOwnedByCurrentRegion(world, chunkX, chunkZ) || !isEnabled) {
            return block()
        }
        return suspendCancellableCoroutine { continuation ->
            val scheduledTask = server.regionScheduler.run(this, world, chunkX, chunkZ) { _ ->
                try {
                    continuation.resume(block())
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
            continuation.invokeOnCancellation { scheduledTask.cancel() }
        }
    }
}