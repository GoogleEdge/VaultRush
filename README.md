# Vault Rush

> 两队争夺中央宝库生成的宝石，把宝石带回己方交付点，为队伍赢得分数。

**Vault Rush 1.0.2** 是一个面向 Minecraft Java 服务器的短局团队竞技小游戏插件，玩法类似“宝石争夺”版的 BedWars：红队和蓝队争夺中央宝库、保护己方携带者，并在敌方拦截前完成交付。

| 项目 | 支持范围 |
|---|---|
| Minecraft | Java 1.21.11 |
| 目标服务端 | Leaf 1.21.11、兼容的 Paper API 服务端 |
| Java | **Java 21**（硬性要求） |
| 菜单 | Java 27 格箱子 GUI；可选 Bedrock SimpleForm |
| 许可证 | MIT，见 [`LICENSE`](LICENSE) |

> 这里的 “Vault” 指游戏中的中央宝库，不需要安装 Vault 经济插件。

## 功能概览

- 红队/蓝队争夺中央宝库中的宝石，并在己方交付点一次性交付得分。
- WAITING → COUNTDOWN → RUNNING → ENDING 的完整比赛流程。
- 目标分立即获胜；时间结束按比分判定；同分为平局。
- 五种本局职业：突击手、侦察者、守护者、工程师、幻术师。
- 六种战术商店道具：迅捷、跳跃、位移烈焰弹、战术护盾、伤害增益和烟雾干扰。
- Java 玩家使用中文箱子菜单；正确配置 Geyser/Floodgate 后，Bedrock 玩家可使用中文 SimpleForm。
- 每位玩家拥有持久的“物品栏”菜单物品，右键即可打开主菜单。
- 竞技场世界独占保护：比赛开始前禁止改图，比赛中只允许本局参赛者建造和挖掘，结束后恢复已记录的地图改动。
- 比赛结束时恢复玩家的背包、盔甲、副手、位置、生命、经验、模式、效果、飞行状态和原有计分板。
- 不提供 RPG 等级、永久属性、跨局经济、数据库统计或跨服务器数据。

## 运行要求

- Java 21。
- Minecraft Java 1.21.11。
- Leaf 1.21.11，或能够提供所需 Paper API 的兼容服务端。
- Geyser 和 Floodgate **可选**：只有需要 Bedrock 玩家 SimpleForm 时才安装。Floodgate 是软依赖，不会被打入插件 JAR；不需要单独安装 Cumulus 插件。
- 本插件使用 Paper API 事件，因此不宣传为 Vanilla、Spigot 或所有 Bukkit 服务端兼容。

默认配置中的 `arenas` 为空。安装 JAR 不会自动生成地图或可玩的竞技场，必须先由管理员配置至少一个竞技场。

## 安装

1. 使用 Java 21 启动 Leaf/Paper 服务端。
2. 将 `VaultRush-1.0.2.jar` 放入服务器的 `plugins/` 目录。
3. 重启服务器。
4. 编辑 `plugins/VaultRush/config.yml`，配置并启用至少一个竞技场。
5. 如果使用 Bedrock 玩家，安装并正确配置 Geyser 与 Floodgate。

生产服务器不建议使用 Bukkit `/reload`。Vault Rush 提供的 `/vr admin reload` 只重新加载本插件配置和竞技场数据，适合修改游戏参数。

## 快速开始：管理员

先在目标竞技场世界中站到对应位置，依次执行：

```text
/vr admin create meadow
/vr admin set meadow lobby
/vr admin set meadow red-spawn
/vr admin set meadow blue-spawn
/vr admin set meadow red-deposit
/vr admin set meadow blue-deposit
/vr admin set meadow vault
/vr admin enable meadow
```

六个位置必须位于同一个已加载世界。启用后的竞技场会独占整个世界，同一世界不能同时启用两个竞技场。建议使用专用竞技场世界，不要使用生存世界、主城或其他玩法地图。

竞技场正在倒计时、比赛中或结算时不能修改位置；管理员需要先执行：

```text
/vr admin disable meadow
```

完成地图维护后，再重新执行 `/vr admin enable meadow`。

## 快速开始：玩家

```text
/vr list
/vr join meadow
```

`/vr join` 会先打开职业选择界面。选择职业后才会加入队列；直接关闭界面不会入队。比赛开始后：

1. 前往中央宝库。
2. 拾取宝石并避开敌方拦截。
3. 返回自己队伍的交付点。
4. 交付携带的宝石，为队伍增加分数。
5. 重复收集和交付，直到达到目标分。

常用入口：

```text
/vr menu
/vr status
/vr leave
```

## 游戏规则

### 队伍和比赛阶段

- 每局固定为红队和蓝队。
- 玩家由服务器交替分配队伍，两队人数尽可能接近。
- 玩家不能同时加入多个队列或多场比赛。
- 比赛阶段依次为 `WAITING`、`COUNTDOWN`、`RUNNING`、`ENDING`。
- 达到 `score-to-win` 后立即结束并判定获胜。
- 时间结束时，分数较高的队伍获胜；分数相同则为平局。

### 宝石

- 宝石定期在中央宝库生成，生成间隔和同时存在数量由配置决定。
- 拾取宝石不会占用背包格，而是记录在本局状态和计分板中。
- 只有自己队伍的交付点可以得分，敌方交付点不会为你交付。
- 进入己方交付点后，携带的宝石会一次性计入队伍分数。
- 被击败时，携带宝石会按 `death-gems` 和 `drop-carried-gems` 配置在死亡地点生成掉落。
- 比赛结束时只清理本插件通过 PDC 标记的宝石实体，不批量删除普通掉落、生物或其他插件实体。

### PvP 和方块

- 敌队之间保留正常 PvP。
- 队友之间的直接伤害和投射物伤害会被取消。
- `WAITING`、`COUNTDOWN` 和 `ENDING` 阶段，任何玩家（包括 OP）都不能修改已启用竞技场世界。
- `RUNNING` 阶段只有本局参赛者可以放置和破坏方块，包括使用羊毛搭路。
- 爆炸、点火、活塞、流体、自然生长、原生容器和其他不安全的连锁地图变化会被阻止。

## 职业

职业只在当前回合有效，比赛开始后锁定。职业不会提供等级、永久成长或跨局存档。主动技能物品通过 PDC 标记，右键使用且不会消耗；冷却中或没有有效目标时不会错误启动新冷却。

| ID | 职业 | 被动 | 主动技能 | 默认冷却 |
|---|---|---|---|---:|
| `assault` | 突击手 | 对同场敌人伤害提高 10% | 冲锋：水平前冲并稍微跃起 | 24 秒 |
| `scout` | 侦察者 | 拾取宝石后速度 I 3 秒，被动冷却 10 秒 | 侦察脉冲：24 格内敌人发光 5 秒 | 30 秒 |
| `guardian` | 守护者 | 来自同场敌人的伤害降低 15% | 坚守护盾：抗性提升 I 4 秒 | 30 秒 |
| `engineer` | 工程师 | 开局和重生额外获得 16 个建筑方块 | 快速施工：急迫 II 8 秒 | 25 秒 |
| `illusionist` | 幻术师 | 拾取宝石后隐身 2 秒，被动冷却 15 秒 | 幻影：隐身 5 秒 | 25 秒 |

备用选择命令：

```text
/vr jobs [竞技场]
/vr job <assault|scout|guardian|engineer|illusionist> [竞技场]
```

## 战术商店

战术币是每局独立的个人货币，不会跨局保存。默认情况下，最终击杀同场敌人获得 3 币；每成功交付 1 颗宝石获得 1 币。环境死亡、自杀、队友伤害和助攻不提供奖励。

商店只在 `RUNNING` 阶段开放，并且玩家必须站在自己队伍的交付点范围内。Java 玩家使用 27 格箱子商店，Bedrock 玩家使用中文 SimpleForm。表单发送失败时可以使用 `/vr shop buy <ID>`。

| ID | 道具 | 默认价格 | 效果 | 冷却 / 每局上限 |
|---|---|---:|---|---|
| `speed` | 迅捷 | 5 | 速度 II，10 秒 | 20 秒 / 3 |
| `jump` | 跳跃 | 4 | 跳跃提升 II，10 秒 | 20 秒 / 3 |
| `fireball` | 位移烈焰弹 | 8 | 水平前冲并向上约 8 格；不爆炸、不点火、不伤人 | 12 秒 / 3 |
| `shield` | 战术护盾 | 7 | 8 秒内降低 30% 受到的伤害 | 25 秒 / 2 |
| `damage-boost` | 伤害增益 | 9 | 8 秒内对敌人额外造成 3 点伤害 | 25 秒 / 2 |
| `smoke` | 烟雾干扰 | 6 | 8 格内敌人获得 Blindness II 约 8 秒，不影响队友 | 20 秒 / 3 |

购买失败不会扣除战术币、增加购买次数或启动冷却。购买成功后道具进入比赛背包，手持后右键使用。

## 菜单、Geyser 与 Bedrock

### Java 和 Bedrock 菜单

- Java 玩家使用 27 格中文箱子主菜单。
- 正确安装并配置 Geyser/Floodgate 后，Bedrock 玩家使用中文 SimpleForm。
- 两种入口都提供加入游戏、离开游戏、查看竞技场、查看状态和战术商店。
- 没有 Floodgate 时，插件仍可加载，Java 玩家仍能使用箱子菜单和普通命令。
- `/vr menu` 始终是备用入口；商店的备用购买命令是 `/vr shop buy <ID>`。
- 代理网络使用 Geyser/Floodgate 时，管理员需要正确转发 Floodgate 数据，并在代理与后端使用匹配的 Floodgate 密钥。

### 持久“物品栏”菜单物品

插件会为每位玩家维护一个名为 **“物品栏”** 的菜单物品：

- 首次补发优先放在快捷栏第 9 格，也就是 `PlayerInventory` 的 slot 8。
- 如果 slot 8 已有玩家自己的普通物品，就寻找其他空的普通背包槽位。
- 36 个普通 storage 槽位全部占用时，不覆盖、不丢弃任何原物品，只发送背包已满提示；此时仍可使用 `/vr menu`。
- 右键合法物品会打开与 `/vr menu` 相同的主菜单，物品不会被消耗。
- 物品通过插件 PDC marker、玩家 UUID 和 token 识别，不依赖材质、名称或 lore；普通同材质物品不会触发菜单。
- 普通玩家可以在自己的普通 storage 内整理该物品，但不能把它丢弃、放置、放入其他容器、移入副手、盔甲槽、展示框或盔甲架，也不能让它作为死亡掉落转移。
- 插件会在加入、比赛开始、重生、比赛结束恢复、待恢复快照、重连和配置重载等生命周期中幂等维护它。
- 自动打开主菜单由 `settings.menu.auto-open-on-join` 控制；关闭自动打开不会关闭“物品栏”物品或 `/vr menu`。

## 命令与权限

`/vr` 是 `/vaultrush` 的快捷别名，以下命令可以使用任一根命令。

### 普通玩家：`vaultrush.play`

该权限默认对所有玩家开放。

```text
/vr join [arena]
/vr jobs [arena]
/vr job <assault|scout|guardian|engineer|illusionist> [arena]
/vr leave
/vr list
/vr status [arena]
/vr menu
/vr shop
/vr shop buy <speed|jump|fireball|shield|damage-boost|smoke>
```

### 管理员：`vaultrush.admin`

该权限默认仅 OP 拥有，并继承 `vaultrush.play`。

```text
/vr admin create <id>
/vr admin delete <id>
/vr admin set <id> <lobby|red-spawn|blue-spawn|red-deposit|blue-deposit|vault>
/vr admin enable <id>
/vr admin disable <id>
/vr admin start <id>
/vr admin stop <id>
/vr admin reload
```

## 配置

主要游戏参数位于 `config.yml` 的 `settings` 节点：

| 配置 | 默认值 | 作用 |
|---|---:|---|
| `min-players` / `max-players` | `2` / `8` | 最小开局人数和竞技场人数上限 |
| `team-size` | `4` | 单队人数上限 |
| `countdown-seconds` | `10` | 开局倒计时 |
| `match-duration-seconds` | `600` | 最长比赛时间 |
| `score-to-win` | `10` | 立即获胜的目标分数 |
| `gem-spawn-interval-seconds` | `8` | 宝石生成间隔 |
| `max-vault-gems` | `3` | 中央宝库同时存在的宝石上限 |
| `death-gems` | `1` | 死亡时最多掉落的携带宝石数；小于等于 0 表示全部掉落 |
| `vault-radius` | `3.0` | 交付点水平半径 |
| `deposit-vertical-radius` | `4.0` | 交付点垂直范围 |
| `settings.menu.auto-open-on-join` | `true` | 加入服务器时是否自动打开主菜单 |
| `settings.menu.item.material` | `NETHER_STAR` | “物品栏”菜单物品材质；空气材质会回退为下界之星 |

地图保护位于 `world-protection`：

```yaml
world-protection:
  enabled: true
  max-recorded-blocks: 50000
  message-cooldown-ticks: 20
```

达到 `max-recorded-blocks` 后，插件会拒绝新的坐标改动，避免继续产生无法完整恢复的地图变化。职业、战术商店和消息文本也都可以在 `jobs`、`shop`、`messages` 节点中调整。

### 升级旧配置

`saveDefaultConfig()` 不会把新键自动合并进已有的 `plugins/VaultRush/config.yml`。升级前请备份配置并保留现有 `arenas`，然后手动合并当前版本新增或变化的节点：

- `world-protection`
- `settings.menu.auto-open-on-join`
- `settings.menu.item.material`
- `jobs`
- `shop`
- `messages.job-*`
- `messages.join-guidance`
- `messages.menu-*`
- `messages.menu-item-*`
- `messages.world-protection-*`
- 商店相关消息键

## 地图保护、清理与限制

- 启用竞技场后，整个竞技场世界都属于该场游戏的保护范围。
- 比赛正常结束、超时、平局、管理员强停、`/vr admin reload` 或插件停用时，插件会按首次改动日志逆序恢复已记录的方块变化，玩家不需要离开服务器。
- 玩家状态会通过快照恢复；商店余额、购买次数、冷却和临时增益不跨局保留。
- 只清理本插件通过 PersistentDataContainer 标记的宝石实体，不批量删除普通方块掉落、普通生物、其他插件实体或世界原有实体。
- 恢复机制只能覆盖 Bukkit/Paper 事件能够观察并由插件记录的正常改动。
- 服务器或 JVM 强制崩溃、断电、外部地图编辑器，以及其他插件绕过 Bukkit/Paper 事件直接写入方块的情况，不保证能够恢复；请继续使用常规磁盘备份。
- 容器和特殊 TileState 使用 Paper `BlockState` 尽力还原，正式部署前应在实际地图和 Leaf 环境中验收。
- pending restore 主要保存在运行时内存中；服务器完全停止后，不应把它当作持久化备份。

## 常见问题

### 安装后为什么没有比赛？

默认 `arenas` 为空。请创建竞技场、设置六个位置并执行 `/vr admin enable <id>`。插件不会自动生成地图。

### 为什么我不能加入？

请检查是否已经在队列或比赛中、竞技场是否已启用且世界已加载、队列是否已满，以及是否拥有 `vaultrush.play`。关闭职业选择界面而没有选择职业也不会入队。

### 为什么背包里没有“物品栏”？

普通背包可能没有空槽。插件不会覆盖原物品；腾出一个普通 storage 槽位后，在下一次加入、重生、比赛装备、快照恢复、配置重载或重新连接等维护时会尝试补发。期间可使用 `/vr menu`。

### 为什么拾取宝石后背包没有变化？

宝石属于本局游戏状态，不占用背包栏位。请查看计分板上的携带数量。

### 为什么在敌方交付点没有得分？

只能在自己队伍的交付点交付。请同时检查自己仍处于 `RUNNING` 比赛、世界正确，以及交付半径配置。

### 为什么商店打不开？

商店只在比赛进行中开放，并且玩家必须站在己方交付点。Bedrock 表单失败时可以使用 `/vr shop buy <ID>`。

### Bedrock 玩家看不到表单怎么办？

请确认 Geyser 和 Floodgate 已安装、启用且代理正确转发 Floodgate 数据。没有 Floodgate 时 Java 玩家仍可以使用箱子菜单；这不会把 Vault Rush 变成需要 Vault 经济插件的项目。

### 比赛结束后我的装备会怎样？

插件会尝试恢复比赛前的背包、盔甲、副手、位置、生命、饥饿、经验、游戏模式、药水效果、飞行状态和原有计分板。地图恢复受事件记录和服务器正常运行边界限制，建议保留备份。

## 构建与开发

在仓库根目录执行：

```bash
./gradlew clean test build
```

构建产物：

```text
build/libs/VaultRush-1.0.2.jar
```

Paper API 和 Floodgate API 使用 `compileOnly`，不会被打入插件 JAR；服务器需要自行提供对应运行环境。JAR 版本来自 `gradle.properties` 的 `pluginVersion`，当前为 `1.0.2`。

更多细节：

- [完整游戏与开发规格](SPEC.md)
- [玩家指南](PLAYER_GUIDE.md)
- [MIT License](LICENSE)
