# Vault Rush 游戏与开发规格

版本：1.0.2  
目标平台：Minecraft Java 1.21.11，Leaf 1.21.11（Paper API 兼容）  
运行时：Java 21

## 1. 游戏目标

Vault Rush 是一局约 10 分钟的两队短局竞技游戏。红队和蓝队争夺中央宝库定时生成的宝石，把宝石带回本队交付点即可得分。玩家死亡时会在死亡位置留下携带中的宝石，双方都可以抢夺。达到目标分数立即获胜；时间结束时按比分判定，比分相同则平局。

MVP 不包含 RPG 等级、永久属性、经济、数据库统计或自动地图生成。v1.0.2 为独占竞技场世界提供基于首次改动日志的赛后方块恢复，但不提供磁盘世界备份、服务器崩溃恢复或外部编辑回滚。插件提供 Java 箱子式主菜单和可选的 Floodgate Bedrock 中文 SimpleForm；Java 主菜单不再以聊天栏按钮作为正常入口。仍然不使用 NMS、CraftBukkit 内部类或 Leaf 私有 API。

## 2. 规则

- 参赛队伍固定为红队和蓝队。
- 玩家入队后由服务端交替分配队伍，人数差最多为 1。
- 一名玩家只能在一个队列或一场比赛中。
- 玩家必须在入队前选择本局职业；职业与 queue 同步原子写入，比赛开始后锁定，并转移到 `PlayerSession`。关闭职业选择界面不会入队。
- 职业仅属于当前回合，不提供等级、永久属性、数据库或跨局成长；离场、结束、强停、重载和停服后清空。
- 玩家加入服务器时始终收到中文 `/vr menu` 提示；自动打开菜单仍由 `settings.menu.auto-open-on-join` 独立控制。
- 中央宝库按 `gem-spawn-interval-seconds` 生成宝石，中央同时存在的宝石不超过 `max-vault-gems`。
- 宝石是带有 PersistentDataContainer 标记的自定义物品，不能仅凭材质判断。
- 参赛者拾取宝石后，宝石计入携带数而不会占用玩家背包。
- 玩家只能在自己队伍的交付点存入宝石；存入时一次性将全部携带数转为队伍分数。移动、传送、拾取宝石、交互和进入下一 tick 都会使用实际目标位置检查交付；水平范围由 `vault-radius` 控制，垂直范围由 `deposit-vertical-radius` 控制。
- 死亡会取消该模式的普通物品掉落，并按 `death-gems`（小于等于 0 表示全部）在死亡位置生成携带宝石；玩家随后在本队出生点重生。
- 比赛结束时移除本插件标记的宝石实体，不触碰普通方块掉落或其他插件实体。
- 结束后恢复玩家比赛前的位置、背包、盔甲、副手、生命、饥饿、经验、游戏模式、药水效果、飞行和原有计分板。

## 3. 状态机

```text
WAITING -> COUNTDOWN -> RUNNING -> ENDING -> WAITING
             |              |          ^
             +--------------+----------+
```

- `WAITING`：接受入队。
- `COUNTDOWN`：人数达到最小值后倒计时；人数不足时取消并回到 `WAITING`。
- `RUNNING`：传送参赛者、发放套装、生成宝石、计时和处理得分。
- `ENDING`：停止任务、广播结果、清理宝石并恢复玩家；随后回到 `WAITING`。

管理员强制停止会从 `COUNTDOWN` 或 `RUNNING` 进入 `ENDING`。

## 4. Arena 配置

每个 arena 位于 `config.yml` 的 `arenas.<id>` 下：

```yaml
arenas:
  meadow:
    enabled: false
    world: world
    lobby: {world: world, x: 0.5, y: 80.0, z: 0.5, yaw: 0, pitch: 0}
    red-spawn: {world: world, x: -20.5, y: 70.0, z: 0.5, yaw: 90, pitch: 0}
    blue-spawn: {world: world, x: 20.5, y: 70.0, z: 0.5, yaw: -90, pitch: 0}
    red-deposit: {world: world, x: -18.5, y: 70.0, z: 0.5, yaw: 0, pitch: 0}
    blue-deposit: {world: world, x: 18.5, y: 70.0, z: 0.5, yaw: 0, pitch: 0}
    vault: {world: world, x: 0.5, y: 70.0, z: 0.5, yaw: 0, pitch: 0}
```

所有点必须位于同一个已加载世界，且设置完整后才能启用。

## 5. 命令

玩家命令：

- `/vr join [arena]`：打开指定 arena 的职业选择界面；选择成功后才原子加入队列。
- `/vr jobs [arena]`：打开五职业选择界面。
- `/vr job <assault|scout|guardian|engineer|illusionist> [arena]`：命令备用入口，直接选择职业并加入。
- `/vr leave`：离开队列或比赛。
- `/vr list`：列出 arena 状态、队列人数和比分。
- `/vr status [arena]`：查看一个 arena 的状态。
- `/vr menu`：重新打开中文玩家主菜单。Java 玩家看到 27 格箱子式 Inventory GUI；安装并正确配置 Floodgate 后的 Bedrock 玩家看到 SimpleForm。主菜单按钮分别执行 `/vr join`、`/vr leave`、`/vr list`、`/vr status` 和 `/vr shop`，普通命令仍可直接输入。
- `/vr shop`：在己方交付点打开战术商店；Java 使用 27 格箱子 GUI，Bedrock 使用中文 SimpleForm。
- `/vr shop buy <speed|jump|fireball|shield|damage-boost|smoke>`：聊天备用购买入口。

管理员命令（`vaultrush.admin`）：

- `/vr admin create <id>`
- `/vr admin delete <id>`
- `/vr admin set <id> <lobby|red-spawn|blue-spawn|red-deposit|blue-deposit|vault>`：将当前位置写入配置；竞技场处于 `COUNTDOWN` 或 `RUNNING` 时拒绝修改，避免比赛中的交付目标漂移。
- `/vr admin enable <id>`、`/vr admin disable <id>`
- `/vr admin start <id>`：立即开始已达到最小人数的队列。
- `/vr admin stop <id>`：停止比赛并恢复玩家。
- `/vr admin reload`：重新加载配置和 arena。

## 6. 配置约束

- `min-players` 至少为 2。
- 有效最大参赛人数为 `min(max-players, team-size * 2)`。
- `score-to-win`、比赛时间和倒计时必须为非负值；插件会对不合理值使用安全下限。
- `gem-material`、套装材质必须是 Bukkit `Material` 名称。
- 默认方块材质为 `WHITE_WOOL`，不要使用抽象的 `WOOL`。

## 7. 代码结构与线程模型

- `VaultRushPlugin`：生命周期和依赖组装。
- `ArenaManager`、`ArenaDefinition`、`ArenaMatch`：配置、状态和比赛数据。
- `QueueService`、`MatchController`：入队、倒计时、开始、结束和任务管理。
- `GemService`、`VaultService`：宝石实体、携带数和交付逻辑。
- `ScoreboardService`：仅维护本插件创建的比赛计分板。
- `PlayerListener`：拾取、移动交付、死亡、重生、退出和加入恢复。
- `CleanupService`：停服和异常路径的统一清理。
- `PlayerSnapshot`、`LocationCodec`：玩家状态和位置快照。
- `PlayerMenuService`、`MenuAction`：中文主菜单内容、Java 箱子 GUI、Bedrock 表单分流和命令分发。
- `MainMenuInventoryHolder`：独立于战术商店的主菜单持有者，保存玩家 UUID、菜单 generation/token 和 Inventory 引用。
- `BedrockMenuBridge`、`FloodgateMenuBridge`：可选 Floodgate 表单适配；没有 Floodgate 时使用空桥接并让 Java 玩家使用箱子主菜单。

Java 主菜单固定为 27 格箱子。按钮放在槽位 `10、11、12、14、15`，依次对应 `JOIN、LEAVE、LIST、STATUS、SHOP`；其余槽位使用不可取出的装饰物填充。按钮 ItemStack 在 PersistentDataContainer 中写入插件专用 `menu_action` 标识，点击处理必须同时验证主菜单 holder、owner UUID、当前打开的 Inventory、generation/token、顶部槽位和有限的 action 白名单，不能只信任显示名称或槽位。所有顶部和底部点击均取消，触及顶部的拖拽也取消；有效点击关闭并失效当前菜单后执行对应的 `/vr` 命令。

所有 Bukkit 世界、实体、Inventory 和玩家 API 调用都在主线程进行。每个比赛持有并取消自己的倒计时、比赛计时和宝石生成任务；禁止使用无法追踪的重复调度任务。玩家加入后延迟一个 tick 打开主菜单；Bedrock 表单回调必须重新切回主线程后才能执行命令。重复打开时旧 Inventory 和旧 generation 必须失效，关闭、退出、踢出、比赛开始/结束、配置重载和插件禁用时必须清理主菜单 GUI 与表单回调。

Floodgate 是可选的 `compileOnly` 依赖，插件使用 `softdepend` 控制加载顺序，不会把 Floodgate、Geyser 或 Cumulus 类打进 JAR。直接引用可选 API 的代码必须隔离在桥接实现中，服务器没有 Floodgate 时也必须正常加载。

## 8. 五职业系统

### 8.1 选择、数据和界面

- `JobType` 是五项有限枚举：`assault`、`scout`、`guardian`、`engineer`、`illusionist`，并提供稳定 ID、中文名、图标以及 ID/index 白名单解析。
- `ArenaMatch` 在等待/倒计时阶段用 `queuedJobs` 保存 UUID 到职业的映射。queue 加入、离开和 reset 必须同步处理职业映射；比赛开始前复制映射，构造带职业的 `PlayerSession`，随后清空 queue 和 queuedJobs。
- Java 职业界面是 27 格箱子，职业图标位于 `10、11、12、14、15`，使用 holder owner、arena ID、Inventory identity、generation、顶部槽位和 PDC `job_id` 验证。
- Bedrock 职业界面使用五按钮中文 SimpleForm；回调切回 Bukkit 主线程并重新验证 generation、在线、权限、arena、比赛状态、队列容量和玩家状态。
- 主菜单、战术商店和职业选择界面互斥；打开其他界面会使旧 Java Inventory 和 Bedrock 回调失效。

### 8.2 默认职业能力

| ID | 职业 | 被动 | 主动 | 默认冷却 |
|---|---|---|---|---:|
| `assault` | 突击手 | 对同场敌人的伤害 ×1.10 | 冲锋：水平 1.25、向上 0.45 | 24 秒 |
| `scout` | 侦察者 | 拾取宝石后速度 I 3 秒，被动冷却 10 秒 | 24 格内同场敌人发光 5 秒；无目标不冷却 | 30 秒 |
| `guardian` | 守护者 | 来自同场敌人的伤害 ×0.85 | 抗性提升 I 4 秒 | 30 秒 |
| `engineer` | 工程师 | 开局和重生额外获得 16 个基础建筑方块 | 急迫 II 8 秒 | 25 秒 |
| `illusionist` | 幻术师 | 拾取宝石后隐身 2 秒，被动冷却 15 秒 | 隐身 5 秒 | 25 秒 |

- 职业主动物品使用 PDC 保存 job ID、matchId、owner UUID；仅当前 RUNNING session 的拥有者可使用，成功后物品不消耗。
- 失败、冷却或侦察脉冲无目标时不启动新冷却。能力只使用 PotionEffect、速度向量和 `PlayerSession` 时间戳，不新增重复调度任务。
- 职业攻防倍率只用于同场敌对玩家：先取消队友伤害，再计算突击手倍率、商店伤害加成、守护者倍率和商店护盾倍率；环境伤害不触发职业攻防倍率，但有效的战术护盾仍可降低环境伤害。
- 合法宝石拾取后才触发侦察者/幻术师被动。比赛开始和重生的统一装备流程负责发放职业物品与工程师方块。

## 9. 战术商店与比赛规则

### 9.1 战术币

- 战术币属于单局个人状态，不写入数据库，比赛开始时为 0，比赛结束、强制停止、配置重载或插件禁用时清零。
- 玩家最终击杀同场敌队玩家奖励 3 战术币；环境死亡、自杀、无击杀者死亡和队友伤害不奖励，不实现助攻奖励。
- 玩家在己方交付点成功交付宝石后，每颗宝石奖励 1 战术币；必须先完成合法交付，再按实际交付数量奖励。
- 击杀和交付奖励数值由配置控制，余额永远不得小于 0。

### 9.2 商店访问与购买事务

- 商店只在比赛处于 `RUNNING` 状态时开放。
- 玩家必须站在自己队伍的交付点范围内，才能通过 `/vr shop` 打开商店或执行购买；敌方交付点和其他位置无效。
- Java 玩家使用独立的 27 格箱子式 GUI；通过 Geyser/Floodgate 连接的 Bedrock 玩家使用中文 SimpleForm。Floodgate 不存在时 Java 箱子菜单仍可用；商店表单发送失败时才提供商店专用中文聊天列表与 `/vr shop buy <id>` 备用入口。主菜单不再把 Java 聊天按钮作为正常入口。
- 每次 Java 点击、Bedrock 表单回调和命令购买都必须重新验证玩家、matchId、比赛状态和己方交付位置。
- 购买按“资格与位置 → 余额 → 每局次数 → 冷却 → 扣款与计数 → 发放道具”的顺序原子执行；失败不扣币、不计次数、不启动冷却。
- 所有价格、持续时间、强度、冷却和每局购买次数均可配置。

### 9.3 默认商店道具

| ID | 道具 | 默认价格 | 效果 | 冷却 | 每局上限 |
|---|---|---:|---|---:|---:|
| `speed` | 迅捷 | 5 | 速度 II，10 秒 | 20 秒 | 3 |
| `jump` | 跳跃 | 4 | 跳跃提升 II，10 秒 | 20 秒 | 3 |
| `fireball` | 位移烈焰弹 | 8 | 沿视线水平分量前冲并向上约 8 格，不爆炸、不伤人、不点火 | 12 秒 | 3 |
| `shield` | 战术护盾 | 7 | 8 秒内降低 30% 受到的伤害 | 25 秒 | 2 |
| `damage-boost` | 伤害增益 | 9 | 8 秒内对敌人额外造成 3 点伤害 | 25 秒 | 2 |
| `smoke` | 烟雾干扰 | 6 | 8 格内敌人受到默认 8 秒 Blindness II，队友不受影响 | 20 秒 | 3 |

- 购买后获得带 VaultRush PersistentDataContainer 标记的战术物品；不得仅凭材质识别。
- 右键消耗一件道具并激活效果。普通同材质物品不触发商店效果。
- 护盾和伤害增益只根据当前 `PlayerSession` 的有效期生效；护盾降低 PvP 与环境伤害，伤害增益仅用于同场敌方玩家。死亡、退出或比赛结束后不得继续生效。
- 烟雾只影响同一场比赛中的敌方玩家，不造成伤害、火焰或方块变化。
- 位移烈焰弹直接设置使用者速度并播放视觉/声音反馈，不生成破坏性火球实体。

### 9.4 PvP 与地图保护

- 同场敌队玩家之间保留普通近战和投射物 PvP。
- 同队玩家之间的直接、投射物和 Paper DamageSource 可追溯到玩家的间接伤害全部取消。
- RUNNING 状态的参赛者可以放置和破坏方块（包括基础套装中的羊毛），用于搭路和战术建造。
- 活跃竞技场中的爆炸不得改变方块，点火事件会被取消；战术烈焰弹不得造成实体伤害、火焰扩散或爆炸破坏。
- 每个启用竞技场独占完整世界。WAITING、COUNTDOWN、ENDING 阶段所有玩家禁止改图；RUNNING 时仅本局参赛者可放置和破坏方块。
- 插件以首次改动日志记录玩家合法修改，在获胜、超时、平局、强停、reload 或 disable 时原地恢复方块、BlockData 与受支持的容器状态；玩家无需退出服务器。
- 活塞、流体、火焰、自然生长、实体改方块及原生容器变化会在竞技场世界中被阻止，避免产生无法可靠回滚的连锁状态。达到配置的日志安全上限后，新的方块改动会被拒绝。
- 规则只作用于 VaultRush 活跃比赛和参赛者，不应影响无关世界、无关玩家或其他插件实体。

### 9.5 清理要求

- 比赛结束、玩家离开、被踢、强制停止、重载和插件禁用时，关闭 Java 主菜单、战术商店 GUI 和 Bedrock 表单。
- 取消或使所有商店回调、冷却、效果和临时任务失效，清理插件标记的临时物品/实体。
- 清理完成后由 `PlayerSnapshot` 恢复玩家局前背包、属性、位置和计分板；商店状态不跨局保留。

## 10. 验收标准

1. `./gradlew clean build` 和 `./gradlew test` 成功。
2. JAR 包含 `plugin.yml`、`config.yml` 和插件类，不包含 Paper API 类。
3. Leaf 1.21.11 能加载插件且无链接错误。
4. 入队去重、满员拒绝、最小人数倒计时取消和队伍平衡正确。
5. 宝石生成、拾取、死亡掉落、争夺、己方交付、目标分获胜和超时判定正确。
6. 退出、踢出、死亡、强制停止和插件禁用后没有孤立任务或残留标记宝石。
7. 玩家比赛前状态能够恢复，普通物品、方块和其他插件实体不被清理。
8. 没有 Floodgate 时插件正常加载，Java 玩家可在加入时或通过 `/vr menu` 使用中文箱子主菜单；无权限、关闭菜单或自动打开关闭时，普通 `/vr` 命令仍可直接使用。
9. 安装并配置 Floodgate 时，在线 Bedrock 玩家可收到中文 SimpleForm，且不会同时打开 Java 箱子；Java 玩家使用箱子主菜单。表单关闭、过期或发送失败不会执行错误操作；商店表单失败时仅回退到商店专用聊天购买入口。
10. 击杀和成功交付按配置奖励战术币；环境死亡、自杀和队友不奖励；每局结束后余额、次数、冷却和效果全部重置。
11. 六种道具均遵守价格、冷却和购买次数；失败购买不改变余额或计数。
12. Java 玩家只能在己方交付点打开受保护的箱子 GUI；Bedrock 玩家使用中文商店表单；所有购买入口都会重新验证比赛和位置。
13. 敌队 PvP 正常，同队伤害取消；RUNNING 参赛者可以放置/破坏方块；爆炸和点火仍受保护，位移烈焰弹不改变地图且不伤害实体。
14. 玩家停在己方交付点时，移动、传送、拾取、交互或下一 tick 检查均能可靠完成交付；重载世界后按世界 UUID 和水平/垂直范围判断。
15. 位移烈焰弹默认使用水平前冲和固定向上速度，目标约 8 格；烟雾默认持续 8 秒并使用 Blindness II。
16. `/vr join` 在 Java 使用职业箱子菜单、Bedrock 使用 SimpleForm；关闭不入队，过期回调、队列满和比赛已开始时拒绝提交。
17. 五职业的被动、主动、冷却、伤害倍率、工程师方块和计分板职业显示正确；职业物品不能跨玩家或跨局使用。
18. 玩家入服后无论是否自动打开菜单，都收到中文 `/vr menu` 提示；所有新增消息有中文 fallback，不显示 literal key。
19. JAR 版本与文件名为 1.0.2，包含职业与世界保护类、默认配置，不包含 Paper/Bukkit/Floodgate/Geyser/Cumulus API 类。
20. 正常结束、超时、平局、RUNNING 强停、reload 和 disable 均恢复本局已记录地图改动；玩家保持在线。服务器崩溃、外部地图编辑器及绕过 Bukkit/Paper 事件的直接方块写入不在保证范围内。
