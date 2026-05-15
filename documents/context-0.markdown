# Reload、持久化与并发的讨论笔记

> **本文档仅作参考，不是具体实现方案**。
> 这是开发过程中关于 reload、持久化与并发权衡的讨论记录，用于沉淀思路、辅助决策。
> 具体到本项目的实现细节，需另行评估再做选择。

---

## 1. 问题起点

在 Paper/Folia 插件中实现需要持久化的功能（如 home、spawn、玩家数据）时，常见的设计是：

- 内存里维护一份可变状态（如 `Map<UUID, List<Home>>`）
- 使用 Kotlin Serialization 等机制序列化为 JSON 写入磁盘
- 每次变更立即 save → IO 在主线程，性能差
- 改为「脏标记 + 定时器 + 异步写入」 → 引入并发问题
- 一旦支持 reload，并发问题进一步复杂化

核心矛盾：**异步 IO 是为了不阻塞主线程，但 reload 期间，主线程的命令处理和异步加载操作可能并发访问同一份内存数据。**

---

## 2. Paper（单线程模型）下的解法

### 2.1 关键事实

Paper 的主线程本身就是一个**免费的、覆盖全游戏状态的全局 mutex**：

- 玩家命令、事件回调、`runTask` 调度的任务、tick 更新都在主线程串行执行
- 你的内存 Map 永远只被主线程触碰，**不需要并发数据结构、不需要锁**

### 2.2 推荐模式：async-load / sync-apply

让异步线程只做纯 CPU/IO 工作，**真正修改内存的动作调度回主线程**：

```kotlin
fun reloadAsync() {
    plugin.server.scheduler.runTaskAsynchronously(plugin) {
        val loaded = readAndDeserialize()       // 异步：纯 IO + CPU
        plugin.server.scheduler.runTask(plugin) {
            homes.clear()
            homes.putAll(loaded)                // 主线程：变更内存
        }
    }
}
```

### 2.3 还要堵的两个坑

1. **reload 前先 flush 脏数据**：否则 reload 会用磁盘内容覆盖未落盘的内存修改。
2. **save 和 load 不能在文件层面打架**：
   - 文件层面：原子写（先写 `.tmp`，再 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`）
   - 任务层面：单线程 ExecutorService 让 save / load 串行

### 2.4 同步 reload 是更省心的选项

一个 home 文件通常几 KB 到几百 KB，反序列化 + putAll 在主线程跑一次大概率 < 20ms，玩家完全无感。

- **save 必须异步**（高频）
- **load/reload 可以同步**（低频，管理员主动触发）

---

## 3. Folia（多线程模型）下的解法

### 3.1 关键区别

Folia 把主线程拆成了多个 region 线程，**「主线程」概念不存在了**：

- 玩家 A 的 /sethome 在 region 1 线程，玩家 B 在 region 2 线程 → 真并发访问同一张 Map
- `runTask` 切回主线程的写法废了
- Paper 那把「看不见的全局 mutex」消失了

### 3.2 推荐方案：不可变快照 + AtomicReference

```kotlin
class HomeManager {
    private val ref = AtomicReference<Map<UUID, List<Home>>>(emptyMap())
    private val dirty = AtomicBoolean(false)

    fun getHomes(uuid: UUID): List<Home> =
        ref.get()[uuid] ?: emptyList()

    fun setHome(uuid: UUID, home: Home) {
        ref.updateAndGet { old ->
            val list = (old[uuid] ?: emptyList()) + home
            old + (uuid to list)
        }
        dirty.set(true)
    }

    fun reload() {
        Bukkit.getAsyncScheduler().runNow(plugin) {
            if (dirty.get()) saveBlocking(ref.get())
            val loaded = readAndDeserialize()
            ref.set(loaded)
        }
    }
}
```

- 读路径无锁，写路径用 `updateAndGet` 做 CAS
- reload 是一次原子 set，**没有「clear 到一半被 put」的窗口**
- 代价是每次写都重建顶层 Map（home 数据量小，可以忽略）

### 3.3 备选方案对比

| 方案 | 优点 | 缺点 |
|---|---|---|
| AtomicReference + 不可变 Map | 无锁、reload 原子、读写都简单 | 写操作要重建 Map（小数据量无所谓） |
| ConcurrentHashMap + `compute` | 写效率高 | clear + putAll 不是原子的，reload 有撕裂 |
| ReadWriteLock | 能保 reload 期间写不丢 | region 线程会被阻塞，拖累 tick |

---

## 4. mutex 与单线程 Executor 的等价性

「单线程 Executor 就是 mutex 的另一种实现形式」。

### 4.1 两种等价写法

```kotlin
// 写法 A：显式锁
private val ioLock = ReentrantLock()
fun saveBlocking() { ioLock.withLock { writeFile() } }

// 写法 B：单线程 Executor
private val io = Executors.newSingleThreadExecutor()
fun save() = io.submit { writeFile() }
```

语义等价。Executor 在 IO 场景更顺手：

- 调用方不阻塞（fire and forget）
- 任务自动排队
- 拿到 `Future`，需要等就 `get()`
- 不会忘记 unlock，异常安全

### 4.2 但还有语义协调问题

锁住 IO 只解决了「文件读到一半被写」。还有更微妙的：

```
t0  内存是 V1
t1  自动保存触发，Executor 排队 [save V1]
t2  玩家 /sethome，内存变成 V2，dirty=true
t3  管理员 /reload，Executor 排队 [save V1, load]
t4  Executor 跑完 save V1，接着 load → 读到 V1 → ref.set(V1)
```

V2 丢了。修法是 reload **触发时立刻 snapshot 当前内存**：

```kotlin
fun reload() {
    val snapshot = ref.get()
    io.submit { writeFileAtomic(snapshot) }
    io.submit {
        val loaded = readFile()
        ref.set(loaded)
    }
}
```

不可变快照让排进队列的数据永远是「提交那一刻的真相」。

---

## 5. reload 的语义选择

### 5.1 两种思路

**A. 追求一致性（事务式）**
- reload 看成原子状态转换：从「内存状态 X」到「磁盘内容 D」
- 中间不能有撕裂；任何并发写要么排在 reload 前，要么排在后
- 实现：ReadWriteLock，reload 拿写锁
- 代价：setHome 等高频操作要走读锁；region 线程被阻塞会拖 tick

**B. 承认丢数据（信号式）**
- reload 看成「以磁盘为准」的信号
- 类似 `git reset --hard` / 游戏读档：执行期间的操作丢了符合预期
- 实现：什么都不加，reload 触发时 snapshot → save → load
- 代价：reload 期间的 setHome 会被 ref.set 覆盖

### 5.2 矛盾点

「reload 期间的写要保留」本身**自相矛盾**：

- 「从磁盘加载」= 用磁盘覆盖内存
- 「保留内存的新写入」= 不要被磁盘覆盖

中间地带是 merge 逻辑，而 merge 语义模糊（磁盘删的 key，内存有新写入，留还是不留？没有客观答案）。

### 5.3 结论

承认丢数据**反而更诚实**，它正确建模了 reload 的本质：低频、管理员主动触发、带「以磁盘为准」语义。

工程上：**用社交协议补足技术不上强保证的部分**：
- reload 前广播「正在重载，请暂停操作」
- 文档写明 reload 是 destructive
- 把「reload 后丢了几条 home」归类为「不要在玩家活跃时 reload」

---

## 6. 不支持 reload 的简化

如果**不实现** reload，并发问题大幅简化：

```kotlin
override fun onDisable() {
    io.shutdown()                                       // 不再接新任务
    io.awaitTermination(10, TimeUnit.SECONDS)           // 等 pending 跑完
    if (dirty.get()) writeFileAtomic(ref.get())         // 同步兜底
}
```

`shutdown()` + `awaitTermination` 自然解决了「stop 时正好有 save 在跑」的问题，根本不需要 mutex。

---

## 7. dual source of truth（双重真相源）

### 7.1 问题描述

「自动保存 + 管理员手动编辑文件」是经典的 **dual source of truth / lost update** 问题：

- 插件内存认为自己是 SoT
- 管理员认为磁盘是 SoT
- 两个 SoT 撞上，**谁后写谁赢**

这在很多领域反复出现：

- 协同编辑同一份文档（Google Doc 等）
- 应用缓存的配置 vs 配置文件被修改
- 数据库主备 split-brain
- IDE 打开的文件被外部程序改了

**本质**：两个写入者各自认为持有最新真相，缺乏协调机制。

### 7.2 业界处理思路

| 思路 | 说明 |
|---|---|
| 1. 约定单一 SoT | 文档化「运行时禁止手改文件」，靠社交协议 |
| 2. 显式协调点（reload 命令） | `save-now → 改文件 → reload`，把两次写在时间上错开 |
| 3. 文件监听 + 自动 reload | 用 `WatchService`，复杂且没解决根本问题 |
| 4. 乐观锁 / 版本号 | 文件存 version，save 前对比，类似数据库 OCC |
| 5. WAL / append-only | 追加变更日志，对结构化数据不合适 |

Bukkit 生态默认是 1 + 2 的组合。

---

## 8. 配置 vs 数据的边界

### 8.1 不要问「它是配置还是数据」，要问「它有几个写入者」

reload 是否可靠**不取决于内容性质，取决于写入者数量**：

| 例子 | 写入者 | reload 可靠性 |
|---|---|---|
| `config.yml` 功能开关 | 只有人工 | 完全可靠 |
| `messages.yml` 提示语 | 只有人工 | 完全可靠 |
| `home.json` 家点数据 | 只有插件（命令）| 双 SoT，不可靠 |
| 玩家血量 | 只有引擎 | 不存盘，无 reload 概念 |

- **单写入者** → 没有双 SoT → reload 干净
- **多写入者**（内存 + 人工）→ 有双 SoT → reload 注定不可靠

### 8.2 spawn 的边界情况

`spawn = (1.1511, 64, -0.81)` 既像配置又像数据：

- 像配置：极少改、是单条全局状态
- 像数据：坐标精度高，人不可能手敲；通常通过 `/setspawn` 产生

**spawn 是不是数据，取决于你怎么设计入口**：

| 设计 | 入口 | spawn 性质 | reload 是否可靠 |
|---|---|---|---|
| A. 当配置 | 只能编辑 `spawn.yml` | 配置 | 可靠 |
| B. 当数据 | `/setspawn` 命令 + 自动 save | 数据 | 不可靠 |
| C. 故意分两套 | `/setspawn` 命令直接写文件再 reload | 配置 | 可靠（磁盘是唯一 SoT） |

### 8.3 真正的分类原则

> **reload 是「以磁盘为唯一真相，刷新到内存」的操作。**

它不关心你是配置还是数据，只关心磁盘是不是唯一真相。

---

## 9. reload 的真实需求是什么？

reload 命令的存在不是为了「热改配置」，而是**「在不重启的情况下修改持久化状态」**——这个需求是普适的。

### 9.1 真实场景

- 玩家把 home 设到坏坐标（传送崩服），紧急修
- 经济通胀，全员余额减半
- 反作弊回滚某玩家的物品
- 从别的插件导入数据
- 备份还原

### 9.2 reload 是这个需求的「笨拙实现」

reload 把**文件暴露成了写入入口**：

```
管理员的隐含心智模型：
"我把磁盘当数据库，文本编辑器当 SQL 客户端，reload 当 COMMIT"

但数据库的 COMMIT 是事务性的，reload 没有任何事务保证。
```

reload 不是问题的解决方案，**它本身就是问题的征兆**——表明插件没有提供足够好的运行时修改接口。

### 9.3 工程演进方向

| 阶段 | 写入入口 | 管理员修改方式 |
|---|---|---|
| 原始期 | 文件 + reload | 编辑 JSON，喊 reload |
| 命令期 | 文件 + 管理命令 | `/admin sethome <player> <home>` |
| API 期 | 文件 + REST/RPC | 调内部 API |
| 数据库期 | SQL | 直接用 SQL 客户端 |

**数据库的存在，本质上就是把「data + reload」这个模式工业化了**：
- SQL 作为唯一写入入口（消除双 SoT）
- 事务作为并发原语（消除竞态）
- 不需要 reload，因为数据库本身就是 SoT

### 9.4 判断标准

设计某个持久化模块时，问自己：

> 如果**不实现** reload，管理员要修改这份数据有多痛？

- 几乎不痛（命令就行）→ 干脆别实现 reload
- 偶尔痛（批量、紧急回滚）→ 实现 reload，但文档明确这是低保证操作
- 经常痛 → **先去补管理命令**，不要靠 reload 兜底

**reload 的存在感越强，通常意味着管理面 API 越弱。它是症状，不是治疗。**

---

## 10. 关键决策树（参考）

```
要不要 reload？
├─ 不要：onDisable 走 shutdown + awaitTermination + 兜底 save。完事。
└─ 要：
   ├─ 内存：AtomicReference<不可变 Map>（Folia）或普通 Map（Paper）
   ├─ IO：单线程 Executor + 原子文件替换
   └─ reload 期间的写要不要保留？
      ├─ 不保留（推荐）：snapshot + 排 save + 排 load。中途的写被 ref.set 覆盖。
      └─ 必须保留：加 ReadWriteLock。但要清楚 region 线程可能阻塞。
```

---

## 11. 总结金句

- 「主线程是免费的全局 mutex，Folia 把它拆掉了」
- 「线程安全 ≠ reload 语义正确，这是两件事」
- 「mutex 必须有，只是写成 Executor 还是 Lock 是形式选择」
- 「reload 是『重启服务器』的便宜替代品，便宜不等于没限制」
- 「reload 在它声明的边界内是可靠的，超出边界用它就是误用」
- 「reload 是症状，不是治疗」
- 「单写入者 → reload 可靠；多写入者 → reload 注定有损」

---

*再次声明：以上内容是开发讨论中的思考与权衡，仅供参考。具体到项目的实现选择，需结合实际场景、数据量、玩家数、维护成本等因素另行决定。*
