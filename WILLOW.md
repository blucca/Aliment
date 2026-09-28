# 柳树 Willow

**Outbreak（中文名：爆发）** 的柳树（willow）内容：完整的柳木方块套装、会生成在河边的垂柳树、
可催熟的树苗、柳树皮汤流程，以及配套的标签、合成表、战利品表和贴图。

所有内容都放在模组自己的创造模式物品栏 **「爆发 / Outbreak」** 里（见下）。

柳树皮汤同时也是生理系统（炎症 / 电解质 / 病原体 / 水杨苷）的一部分，
那套系统单独写在 [`PHYSIOLOGY.md`](PHYSIOLOGY.md)。

面向 Minecraft **26.2**（Fabric，Kotlin 2.4 / Java 25）。

---

## 内容一览

### 创造模式物品栏

模组注册了一个自己的物品栏分类 `outbreak:main`，标题为 `itemGroup.outbreak.main`
（英文 `Outbreak`，中文 `爆发`），图标是柳树树苗，位置在创造模式物品栏顶行原版分类之后。
**所有** Outbreak 物品都只放在这个分类里，不再重复塞进原版分类，方便后续继续往里加东西。

### 方块 / 物品

| ID | 中文名 | 说明 |
| --- | --- | --- |
| `outbreak:willow_log` | 柳木原木 | 带 `axis` 属性 |
| `outbreak:willow_wood` | 柳木 | 六面树皮 |
| `outbreak:stripped_willow_log` | 去皮柳木原木 | |
| `outbreak:stripped_willow_wood` | 去皮柳木 | |
| `outbreak:willow_planks` | 柳木木板 | |
| `outbreak:willow_leaves` | 柳树树叶 | 与原地形一样按生物群系染色 |
| `outbreak:willow_sapling` | 柳树树苗 | **可被骨粉催熟** |
| `outbreak:potted_willow_sapling` | 柳树树苗盆栽 | 无物品形式 |
| `outbreak:willow_vines` | 垂柳 | 树冠垂下的柳条末端，可骨粉催长 |
| `outbreak:willow_vines_plant` | 垂柳茎 | 柳条中段，无物品形式 |
| `outbreak:willow_stairs` / `_slab` | 柳木楼梯 / 台阶 | |
| `outbreak:willow_fence` / `_fence_gate` | 柳木栅栏 / 栅栏门 | |
| `outbreak:willow_door` / `_trapdoor` | 柳木门 / 活板门 | 使用自定义 `BlockSetType` |
| `outbreak:willow_pressure_plate` / `_button` | 柳木压力板 / 按钮 | |
| `outbreak:willow_shelf` | 柳木架子 | 复用原版 `SHELF` 方块实体 |
| `outbreak:willow_sign` | 柳木告示牌 | 含 `willow_wall_sign`（无物品） |
| `outbreak:willow_hanging_sign` | 柳木悬挂告示牌 | 含 `willow_wall_hanging_sign`（无物品） |
| `outbreak:willow_boat` | 柳木船 | 自带 `EntityType` 与模型层 |
| `outbreak:willow_chest_boat` | 柳木运输船 | |
| `outbreak:willow_soup_cauldron` | 柳树皮汤炼药锅 | 由水炼药锅 + 柳树皮碎片生成，有 `cooked` 与 `level` 属性；无物品形式 |
| `outbreak:willow_bark` | 柳树树皮 | 斧头给柳木剥皮时掉落 |
| `outbreak:willow_bark_pieces` | 柳树皮碎片 | 树皮在砂轮上磨碎得到 |
| `outbreak:raw_willow_bark_soup_bottle` | 生柳树皮汤 | 玻璃瓶装，可饮用，返还玻璃瓶 |
| `outbreak:raw_willow_bark_soup_bowl` | 生柳树皮汤 | 碗装，可食用，返还碗 |
| `outbreak:willow_bark_soup_bottle` | 柳树皮汤 | 熟的，玻璃瓶装 |
| `outbreak:willow_bark_soup_bowl` | 柳树皮汤 | 熟的，碗装 |

### 柳树皮汤流程

```
柳木原木 ──斧头右键剥皮──> 去皮柳木原木 + 柳树树皮 ×1
柳树树皮 ──砂轮右键磨碎──> 柳树皮碎片 ×2
柳树皮碎片 ──右键水炼药锅──> 柳树皮汤炼药锅（生，level 数按原炼药锅水量）
               │
               ├─ 玻璃瓶右键 ──> 生柳树皮汤（瓶）
               └─ 碗右键     ──> 生柳树皮汤（碗）
               │
          下方放篝火 / 灵魂篝火，60 秒（1200 tick）后
               │
               ▼
          柳树皮汤炼药锅（熟，汤色更深）
               ├─ 玻璃瓶右键 ──> 柳树皮汤（瓶）
               └─ 碗右键     ──> 柳树皮汤（碗）
```

* 每盛出一份，炼药锅的水位降一级，空了以后变回普通炼药锅（和原版水炼药锅一致）。
* 篝火中途被移除时，60 秒计时到点后不会变熟；重新放上篝火会重新开始计时。
* 打碎炼药锅会掉落一个普通炼药锅。
* 两种生汤和两种熟汤的**显示名分别相同**（生柳树皮汤 / 柳树皮汤），只有容器不同。
* **生汤颜色浅而浑浊，熟汤颜色更深**；炼药锅里的汤面也一样，煮熟后颜色变深。
  物品贴图分别照原版**蘑菇煲**（碗装）和**龙息**（瓶装）的造型重画，只是换了汤汁配色。

### 食物与效果

| 食物 | 饥饿值（nutrition） | 饱和度（saturation） | 效果 |
| --- | --- | --- | --- |
| 生柳树皮汤（瓶 / 碗） | 1 | 1 | 25% 概率**反胃** 20 秒、15% 概率**饥饿** 20 秒 |
| 柳树皮汤（瓶 / 碗） | 1 | 2 | 无 |

> `FoodProperties.saturationModifier` 是倍率而不是绝对值，实际饱和度 =
> `nutrition × saturationModifier × 2`；营养值 1 时，倍率 0.5 → 1 点，1.0 → 2 点。

两种汤都还会给身体**补充水杨苷**（+1.1，起效浓度 1.0），这是生理系统里控制炎症的手段，
详见 [`PHYSIOLOGY.md`](PHYSIOLOGY.md)。生汤同时是感染源之一（30% 概率感染细菌）。

### 木种

`BlockSetType` 与 `WoodType` 都注册为 `outbreak:willow`（通过 Fabric 的
`BlockSetTypeBuilder` / `WoodTypeBuilder`，它们能写入原版那两个私有映射表）。

---

## 生成

* **只在河边生成**：`OutbreakWorldGen` 通过 Fabric `BiomeModifications.addFeature` 把
  `outbreak:willow_river` 注入 `#minecraft:is_river`（河流、冻河）生物群系的
  `VEGETAL_DECORATION` 阶段。
* `outbreak:willow_river` 放置修饰符与原版 `trees_plains` 完全一致
  （`count` → `in_square` → `surface_water_depth_filter` → `heightmap` →
  `would_survive` 树苗 → `biome`），所以柳树只会长在河岸的土 / 草方块上，
  永远不会插在水里。
* 两棵树形：
  * `outbreak:willow`：树干 5–7 格，`fancy_foliage_placer` 半径 3 的垂坠树冠；
  * `outbreak:tall_willow`：树干 8–10 格。
  树苗生长时 **65% / 35%** 随机二选一。
* **树干向河边倾斜**：两棵树形都用自定义的
  `outbreak:leaning_willow_trunk_placer`。它会在树干周围 7 格内按方向统计水面方块
  （越近权重越高），选出水最多的那一侧，然后把树干上段的 1–4 格逐级向那一侧偏移，
  形成探向水面的倾斜树形。附近没有水（或者扫描会越出区块生成范围）时就直立生长。
  扫描用 `hasChunk` 保护，绝不会因为树长在区块边界而中断世界生成。
* **垂柳** 由自定义 `TreeDecorator`（`outbreak:willow_hanging`）挂在树冠下：
  每个树叶方块有概率向下垂 1–3 格柳条（`willow_vines` 末端 +
  `willow_vines_plant` 中段）。密度刻意压低：
  普通柳树 `probability` 0.18、高柳 0.28，最长 3 格。
  柳条之后会自己缓慢向下生长，也可以用骨粉催长。

自定义的 `TreeDecoratorType` 与 `TrunkPlacerType` 都在 `Outbreak.onInitialize()`
里注册，早于数据包解析。

---

## 标签

* 自有标签：`#outbreak:willow_logs`（方块 + 物品）。
* 追加到原版标签（不覆盖，`values` 会合并）：
  `planks`、`logs_that_burn`、`leaves`、`saplings`、`flower_pots`、
  `wooden_stairs/slabs/fences/doors/trapdoors/pressure_plates/buttons/shelves`、
  `fence_gates`、`standing_signs`、`wall_signs`、`ceiling_hanging_signs`、
  `wall_hanging_signs`、`mineable/axe`、物品侧的 `signs`、`hanging_signs`、
  `boats`、`chest_boats`，以及 `entity_type/boat`。

因为原版 `#minecraft:planks` 已经在 `#minecraft:wooden_tool_materials` 里，
木棍等原版配方会自动把柳木木板算进去。

---

## 合成表（16 个）

`willow_planks`（无序，`#outbreak:willow_logs`）、`willow_wood`、
`stripped_willow_wood`、`willow_stairs`、`willow_slab`、`willow_fence`、
`willow_fence_gate`、`willow_door`、`willow_trapdoor`、`willow_pressure_plate`、
`willow_button`、`willow_sign`、`willow_hanging_sign`、`willow_shelf`、
`willow_boat`、`willow_chest_boat` —— 数值与原版对应配方一致。

每个方块都有自己的战利品表（`data/outbreak/loot_table/blocks/…`），
树叶的苹果掉落被移除了（柳树不结苹果）。

---

## 贴图

31 张 PNG 全部由 `tools/gen_textures.ps1` 用 ImageMagick 生成，可以随时重新生成：

```
tools\gen_textures.cmd
```

* 方块贴图 17 张（含 32×32 的告示牌 / 悬挂告示牌 / 架子，以及炼药锅里生 / 熟两种汤面）
* 物品贴图 11 张（含树皮、树皮碎片、生 / 熟汤的瓶装与碗装各一张）
* 船只实体贴图 2 张（128×64 / 128×128，按原版 `BoatModel` 的立方体展开逐面绘制）
* 模组图标 1 张（128×128，**水仙花**）

汤类物品贴图以原版素材为造型参考：**碗装照蘑菇煲**（木碗 + 可见汤面），
**瓶装照龙息**（方形玻璃瓶 + 短颈 + 软木塞），只把汤汁换成柳树皮汤的配色。
生汤浅而浑浊、有纤维碎屑；熟汤更深更干净，和炼药锅里「煮熟变深」的表现一致。

柳树叶贴图是**灰度**的，因为方块本身注册了生物群系树叶染色
（`BlockColorRegistry` + `BlockTintSources.foliage()`），和原版树叶一样。

炼药锅的汤面直接复用原版 `template_cauldron_full` / `_level1` / `_level2` 模型，
只替换 `content` 贴图，因此不需要任何自定义方块实体渲染器。

---

## 目录结构

```
src/main/kotlin/com/github/kusa233/outbreak/
├─ Outbreak.kt                        模组入口，按依赖顺序初始化各注册表
├─ registry/
│  ├─ Registration.kt                 方块 / 物品注册辅助函数
│  ├─ OutbreakBlocks.kt               全部柳木方块 + 柳树皮汤炼药锅
│  ├─ OutbreakItems.kt                告示牌、悬挂告示牌、船、树皮与四种汤
│  ├─ OutbreakEntities.kt             两个船实体类型
│  ├─ OutbreakBlockEntities.kt        把新方块挂进原版 SIGN / HANGING_SIGN / SHELF
│  ├─ OutbreakWoodTypes.kt            BlockSetType + WoodType
│  ├─ OutbreakTreeGrowers.kt          树苗成长用 TreeGrower
│  ├─ OutbreakWorldGen.kt             注入河流生物群系
│  └─ OutbreakCreativeTabs.kt         模组自己的「爆发」物品栏分类
├─ event/OutbreakInteractions.kt      斧头剥皮、砂轮磨碎、碎片入锅（UseBlockCallback）
├─ command/OutbreakCommand.kt         /outbreak status | set | cure
├─ physiology/                        炎症 / 电解质 / 病原体 / 水杨苷 数据系统
│  ├─ OutbreakData.kt                 数据模型、编解码器、三个 attachment
│  ├─ OutbreakPhysiology.kt           纯数据模型演算（不依赖任何 Minecraft 对象）
│  ├─ OutbreakInfection.kt            感染来源与药量
│  └─ OutbreakSymptoms.kt             症状与每 tick 驱动
├─ dev/                               仅开发用的自检（默认不启用，见 tools/README.md）
└─ world/
   ├─ block/WillowVinesBlock.kt       垂柳末端（可催长）
   ├─ block/WillowVinesPlantBlock.kt  垂柳中段
   ├─ block/WillowSoupCauldronBlock.kt 柳树皮汤炼药锅（水位 + 生/熟 + 篝火计时）
   └─ tree/                           自定义 TreeDecorator、TrunkPlacer 及其注册表类型

src/main/java/com/github/kusa233/outbreak/mixin/
├─ ItemMixin.java                     吃完 / 喝完的那一刻（原版没有这个事件）
└─ PlayerMixin.java                   缩放所有饱食度消耗

src/client/
├─ java/.../mixin/client/CameraMixin.java   镜头抖动（只动镜头）
└─ kotlin/.../client/
   ├─ OutbreakClient.kt               树叶染色、船模型层、船渲染器
   └─ OutbreakClientShake.kt          把服务端的抖动序号变成衰减振荡

src/main/resources/
├─ assets/outbreak/{blockstates,models,items,textures,lang,icon.png}
└─ data/outbreak/{worldgen,loot_table,recipe,tags}
   data/minecraft/tags/…              追加到原版标签
```

---

## 重新生成资源

```
tools\gen_data.cmd        # 全部 data / assets JSON
tools\gen_textures.cmd    # 全部贴图
```

两者都是幂等的，且都以 Minecraft jar 里的原版 JSON 为模板，所以格式永远和
`gradle.properties` 里的版本一致。细节见 [`tools/README.md`](tools/README.md)。

---

## 验证

### 1. 河流生物群系真实生成

`tools/verify-datapack/` 里有一个开发用数据包，会在世界加载后清点已生成区块里的柳木方块。
配合“河流生物群系的超平坦世界”（见 tools/README.md），实际跑出的日志为：

```
[Server] OUTBREAK_RIVERWORLD_VINES_OK     # 垂柳
[Server] OUTBREAK_RIVERWORLD_TREES_OK     # 柳木原木
[Server] OUTBREAK_RIVERWORLD_LEAVES_OK    # 柳树树叶
```

也就是说：Fabric 的生物群系注入 → `outbreak:willow_river` → 配置化地物 →
自定义倾斜树干 + 垂柳装饰器，整条链路在真实区块生成中都正常工作，没有任何异常。

### 2. 交互链路自检

`src/main/kotlin/.../dev/OutbreakSelfTest.kt` 是一个开发用自检入口点（**默认不启用**）。
它在无头服务器上用 Fabric 的 `FakePlayer` 真的去右键方块，覆盖：

```
SELFTEST PASS: axe stripping turns the log into stripped_willow_log
SELFTEST PASS: grinding a willow bark yields bark pieces
SELFTEST PASS: bark pieces turn a water cauldron into raw willow bark soup
SELFTEST PASS: a glass bottle fills with raw willow bark soup
SELFTEST PASS: 60 seconds over a campfire cooks the soup
SELFTEST PASS: a bowl fills with cooked willow bark soup
SELFTEST PASS: willow grows on the test bank
SELFTEST PASS: willow trunk leans towards the water (max log x=8 > 6)
SELFTEST DONE passed=8 failed=0
```

启用方式见 [`tools/README.md`](tools/README.md)。

> 注：无玩家的开发服务器没有任何“实体 tick 区块”，所以掉落的树皮不会出现在实体查询里，
> 自检里用一条临时日志确认了 `Block.popResource` 确实被调用（`dropped willow bark at
> BlockPos{x=4, y=201, z=10}`）。真实游戏里会正常掉落。
