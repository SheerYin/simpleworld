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

    // onDisable 时等待保存完成的最长时间
    @Volatile
    var shutdownTimeout = 10.seconds

    val isFolia: Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        true
    } catch (e: ClassNotFoundException) {
        false
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
                worldManager.load()
                worldManager.startAutoSave()
            } finally {
                // 无论加载成功与否都放行，避免加载异常把玩家永久挡在门外
                ready = true
            }
        }

        SimpleWorldCommand(this, logger, prefix, scope, worldManager).register()

        logger.info("Enabled {} {}", prefix, pluginMeta.version)
    }

    override fun onDisable() {
        val prefix = pluginMeta.loggerPrefix ?: pluginMeta.name
        slF4JLogger.info("Disabled {} {}", prefix, pluginMeta.version)
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
        scope?.cancel()
        this.scope = null
        this.worldManager = null
    }

    /**
     * 在全局区域线程上执行 [block] 并返回其结果。
     *
     * - 已在全局区域线程，或插件正在关闭（onDisable 时 isEnabled 为 false，调度器会拒绝注册任务）
     *   → 直接内联执行；
     * - 否则派到全局区域线程，挂起等待（不阻塞当前线程）。
     *
     * 内联那条分支让本函数“可重入”：在全局 tick 线程上再次调用也不会因
     * “任务排到下一 tick + 当前 tick 被阻塞”而自锁。
     */
    suspend fun <T> runGlobalRegion(block: () -> T): T {
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
}
