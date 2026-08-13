# Vault Rush

Vault Rush 是一个适用于 Minecraft Java 1.21.11 / Leaf 1.21.11 的 Paper API 小游戏插件。两队争夺中央宝库生成的宝石，将宝石带回己方交付点得分。

## 需求

- Leaf 1.21.11 或兼容的 Paper API 服务端
- Java 21
- 不需要安装 Vault 经济插件；这里的 Vault 指游戏中的中央宝库
- Floodgate/Geyser 为可选组件；安装后 Bedrock 玩家可使用中文表单

插件不使用 NMS、CraftBukkit 内部类或 Leaf 私有 API，因此目标是保持 Paper/Leaf 兼容。

## 安装

1. 使用 Java 21 启动 Leaf 服务器一次。
2. 将 `build/libs/VaultRush-1.0.2.jar` 复制到服务器的 `plugins/` 目录。
3. 重启服务器，或在测试服使用插件管理工具加载。
4. 编辑 `plugins/VaultRush/config.yml`，创建并设置至少一个 arena。

不要在生产服使用 `/reload`；插件提供的 `/vr admin reload` 仅重新加载 Vault Rush 配置，适合修改 arena 和游戏参数。

## 构建

```bash
cd /root/mcplugin
./gradlew clean build
./gradlew test
```

产物：`build/libs/VaultRush-1.0.2.jar`。Paper API、Floodgate API 和 Cumulus 都是 `compileOnly` 依赖，不会被打入插件 JAR。

## Geyser 与 Bedrock 支持

安装并正确配置 Geyser 与 Floodgate 后，Bedrock 玩家加入服务器会看到中文 SimpleForm 菜单。Java 玩家加入服务器时会看到 27 格中文箱子式主菜单；如果没有 Floodgate，插件仍然正常加载并提供 Java 箱子菜单。

主菜单会在玩家加入服务器时自动显示，也可以手动重新打开：

```text
/vr menu
```

箱子或表单中的五个按钮会调用普通命令，因此命令始终是可靠的备用入口：

- 加入游戏：`/vr join`
- 离开游戏：`/vr leave`
- 查看竞技场：`/vr list`
- 查看状态：`/vr status`
- 战术商店：`/vr shop`

Java 玩家在己方交付点使用 `/vr shop` 会打开独立的 27 格战术商店箱子 GUI；Bedrock 玩家会收到带道具说明的中文 SimpleForm。商店表单发送失败时会显示商店聊天列表，仍可使用 `/vr shop buy <道具ID>` 购买；这属于商店备用入口，不是主菜单。

如果你还没有进入正在进行的比赛，或不在己方交付点，`/vr shop` 不会打开箱子，这是设计限制；插件会发送下一步提示。旧服务器的 `config.yml` 不会被 `saveDefaultConfig()` 自动合并，替换 JAR 后请手动补充新的 `jobs`、`messages.job-*`、`messages.join-guidance`、`messages.menu-*` 和商店消息键，并保留 `arenas` 配置。

Floodgate/Cumulus 不会被打包进插件。代理网络还需要按 Floodgate 文档打开 `send-floodgate-data`，并在代理与后端使用相同的 Floodgate 密钥。

## 配置 Arena

先在游戏中站到目标位置，依次执行：

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

所有位置应在同一个已加载世界。`enable` 会检查配置完整性和世界是否存在；检查失败时不会启用。每个已启用竞技场会独占其整个世界，同一世界不能同时启用两个竞技场。请使用专用竞技场世界，不要把生存世界、主城或其他玩法地图配置为已启用竞技场。

地图保护默认开启：WAITING、COUNTDOWN、ENDING 阶段任何玩家（包括 OP）都不能改图；RUNNING 阶段只有本局参赛者可以放置和挖掘。管理员维护地图前必须先执行 `/vr admin disable <id>`，维护完成后再启用。比赛正常结束、超时、平局、强停、`/vr admin reload` 或插件停用时，会在不踢出玩家、不卸载世界的情况下恢复本局已记录的方块变化。

游戏参数在 `config.yml` 的 `settings` 中：

- `min-players` / `max-players`：入场人数范围。
- `team-size`：单队上限。
- `countdown-seconds`：开始倒计时。
- `match-duration-seconds`：最长比赛时间。
- `score-to-win`：立即获胜的目标分。
- `gem-spawn-interval-seconds` / `max-vault-gems`：宝库生成节奏。
- `death-gems`：死亡时最多掉落的携带宝石；小于等于 0 表示全部掉落。
- `vault-radius`：交付点水平半径。
- `deposit-vertical-radius`：交付点垂直范围，默认 4 格。

已启用、倒计时、比赛进行或结算中的竞技场不能使用 `/vr admin set` 修改位置；管理员必须先 `/vr admin disable <id>`，避免独占世界归属和交付目标在运行期改变。
- `kit`：比赛开始和重生时发放的基础装备。

地图保护参数位于 `world-protection`：

- `enabled`：是否启用独占世界保护和赛后恢复，默认 `true`。
- `max-recorded-blocks`：单局最多记录的首次方块变化坐标，默认 `50000`；达到上限后会拒绝新的坐标改动，避免出现无法恢复的变化。
- `message-cooldown-ticks`：拒绝提示的聊天节流时间，默认 20 tick。

从旧版本升级时，`saveDefaultConfig()` 不会自动把新键合并进已有 `plugins/VaultRush/config.yml`。请先备份并保留 `arenas`，再手动加入 `world-protection` 节点及新的 `messages.world-protection-*` 文本。

## 五职业系统

执行 `/vr join [arena]` 后不会立即入队，而是先选择本局职业。Java 玩家使用受保护的 27 格箱子菜单，Bedrock 玩家使用 Floodgate 中文 SimpleForm；关闭选择界面不会入队。职业在比赛开始后锁定，离场、比赛结束、强停、重载或停服后清空，不提供等级、永久成长或数据库存档。

| ID | 职业 | 被动 | 主动技能 | 默认冷却 |
|---|---|---|---|---:|
| `assault` | 突击手 | 对同场敌人伤害提高 10% | 冲锋：水平前冲并稍微跃起 | 24 秒 |
| `scout` | 侦察者 | 拾取宝石后速度 I 3 秒（被动冷却 10 秒） | 侦察脉冲：24 格内敌人发光 5 秒 | 30 秒 |
| `guardian` | 守护者 | 来自同场敌人的伤害降低 15% | 坚守护盾：抗性提升 I 4 秒 | 30 秒 |
| `engineer` | 工程师 | 开局和重生额外获得 16 个建筑方块 | 快速施工：急迫 II 8 秒 | 25 秒 |
| `illusionist` | 幻术师 | 拾取宝石后隐身 2 秒（被动冷却 15 秒） | 幻影：隐身 5 秒 | 25 秒 |

比赛开始和重生后，背包会获得带 PDC 标记的职业主动技能物品；手持该物品右键即可使用，物品不会消耗。技能在冷却、验证失败或侦察脉冲没有目标时不会错误启动新冷却。计分板显示当前职业。

命令备用入口：`/vr jobs [arena]` 打开职业选择，`/vr job <职业ID> [arena]` 直接选择并加入。所有职业数值位于 `config.yml` 的 `jobs` 节点。

## 战术商店

战术币是每局独立的个人货币，不持久化：击杀同场敌人默认获得 3 币，每成功交付 1 颗宝石默认获得 1 币，不计算助攻。商店仅在比赛进行中开放，且玩家必须站在己方交付点范围内。

| ID | 道具 | 默认价格 | 默认效果 | 冷却 / 每局上限 |
|---|---|---:|---|---|
| `speed` | 迅捷 | 5 | 速度 II，10 秒 | 20 秒 / 3 |
| `jump` | 跳跃 | 4 | 跳跃提升 II，10 秒 | 20 秒 / 3 |
| `fireball` | 位移烈焰弹 | 8 | 水平前冲并向上约 8 格，不产生爆炸 | 12 秒 / 3 |
| `shield` | 战术护盾 | 7 | 8 秒降低 30% 伤害 | 25 秒 / 2 |
| `damage-boost` | 伤害增益 | 9 | 8 秒增加 3 点对敌伤害 | 25 秒 / 2 |
| `smoke` | 烟雾干扰 | 6 | 8 格内敌人 Blindness II 约 8 秒 | 20 秒 / 3 |

购买后道具进入比赛背包，右键消耗。所有价格、持续时间、强度、冷却、次数上限和货币奖励都在 `config.yml` 的 `shop` 节点配置。失败购买不会扣币或增加次数。

比赛中保留敌队 PvP，但取消队友直接和投射物伤害。RUNNING 状态只有本局参赛者可以放置和破坏方块，包括基础套装中的羊毛；非参赛者不能改动独占竞技场世界。爆炸不会破坏方块，点火、活塞、流体、自然生长、原生容器和其他不安全的连锁地图变化会被禁止。位移烈焰弹只设置使用者速度，目标约向上 8 格，不生成火球实体、不点火、不伤人。回合结束后插件按首次改动日志逆序恢复方块并清理本插件宝石实体，普通方块掉落和其他插件实体不会被批量删除；玩家保持在线。余额、购买次数、冷却和临时增益在死亡/离场或回合结束时按规则失效，比赛结束后恢复赛前状态。

## 命令与权限

普通玩家（`vaultrush.play`，默认所有人）：

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

管理员（`vaultrush.admin`，默认 OP）：

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

## 游戏流程

1. 玩家通过 Java 箱子菜单或 Bedrock SimpleForm 选择职业；职业与玩家队列记录原子写入，人数达到下限后进入倒计时。
2. 玩家被交替分配到红队和蓝队，插件保存比赛前状态并传送至出生点。
3. 中央宝库生成带有插件标记的宝石，拾取后计入玩家携带数。
4. 携带宝石的玩家死亡时会在死亡点掉落宝石，并在本队出生点重生。
5. 玩家进入本队交付点后，携带宝石一次性转为队伍分数；移动、传送、拾取、交互和下一 tick 检查都会尝试交付，停在交付点也不会因为没有继续移动而卡住。
6. 达到目标分或时间结束后结算，清理本插件实体、恢复已记录的地图变化，并恢复玩家状态。

## 安全边界

插件只清理自己通过 PersistentDataContainer 标记的宝石实体；不会批量删除普通方块掉落、普通生物、其他插件实体或原有世界实体。比赛结束会恢复玩家背包、位置、模式、效果、飞行、经验、原计分板和本局记录的方块状态。

地图恢复只覆盖 Bukkit/Paper 事件能够观察并由本插件记录的正常改动。服务器或 JVM 强制崩溃、断电、外部地图编辑器，以及其他插件绕过事件直接写入方块，不保证恢复；请继续执行常规磁盘备份。容器和特殊 TileState 使用 Paper `BlockState` 尽力还原，部署前应在真实 Leaf 测试服验收自定义地图中的特殊方块。

如果交付没有发生，请确认玩家仍在 RUNNING 比赛、站在己方交付点、世界已加载，并检查 `vault-radius` 与 `deposit-vertical-radius`。完整规则、状态机、数据模型和验收标准见 [`SPEC.md`](SPEC.md)。
