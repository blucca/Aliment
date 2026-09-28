# 生理系统 Physiology

Outbreak（爆发）的核心系统：每个玩家体内持续演算的一套**炎症 / 电解质 / 病原体 / 药物**模型。

设计目标是让"感染"变成一件有过程的事——吃坏东西不会立刻掉血，而是让你的免疫系统慢慢失控，
而柳树皮汤（水杨苷）是控制它的手段，但喝太多同样会出事。

面向 Minecraft **26.2**（Fabric，Kotlin 2.4 / Java 25）。

---

## 数据模型

每个玩家身上挂着一份 `OutbreakData`：

| 字段 | 范围 | 含义 |
| --- | --- | --- |
| `inflammation` | 0 – 100 | **炎症指数**。健康时稳定在 20–30 |
| `electrolytes` | 0 – 100 | **电解质**。发烧时流失，健康时缓慢恢复 |
| `bacteria` | 0 – 100 | **细菌**载量 |
| `virus` | 0 – 100 | **病毒**载量 |
| `salicin` | 0 – 3.0 | **体内药物**：水杨苷浓度 |

派生量：`pathogenLoad = bacteria + virus`、`severity`（0–1，用于缩放症状）、
`isSymptomatic`（载量 ≥ 8）、`isImmuneStorm`（炎症 ≥ 75）、`isImmunosuppressed`（炎症 ≤ 12）。

### 存在哪里

用 Fabric 的 **Data Attachment API**，不占用 NBT、不需要 mixin：

| Attachment | 持久化 | 同步 | 说明 |
| --- | --- | --- | --- |
| `outbreak:physiology` | ✅ 死亡保留 | ❌ | 完整数据，服务端权威 |
| `outbreak:shake` | ✅ | ✅ 全客户端 | 只有一个计数器 + 幅度，客户端用它放镜头抖动 |
| `outbreak:runtime` | ❌ | ❌ | 抖动计时、蝙蝠接触冷却，纯运行期状态 |

只有"抖动事件序号"会同步，所以一场抖动只发一个包，而不是每 tick 都发。

---

## 感染来源

| 来源 | 病原体 | 概率 | 单次载量 |
| --- | --- | --- | --- |
| 生肉（牛 / 猪 / 鸡 / 羊 / 兔 / 鳕鱼 / 鲑鱼 / 热带鱼） | 细菌 | **30%** | +6 |
| 腐肉、毒马铃薯 | 细菌 | **30%** | +6 |
| 生柳树皮汤（瓶 / 碗） | 细菌 | **30%** | +6 |
| **接触蝙蝠**（2 格内） | 病毒 | **15%** | +5 |

* 食物走 `Item.finishUsingItem`（见下面的 mixin），所以是"真正咽下去的那一刻"判定。
* 蝙蝠是**接触**判定：进入 2 格范围后掷一次，然后有 10 秒冷却，避免站在蝙蝠旁边被反复判定。
* 一次 +6 的感染是可以自愈的，但连着吃几次就会跨过临界点——载量大约 **27 以上**时，
  健康人也压不住了。

---

## 模型

每 tick 演算一次，全部是纯数据运算（`OutbreakPhysiology.tick(data)`，不依赖任何 Minecraft 对象）。

### 1. 病原体

```
growth    = 0.0004 * load * (1 - load / 100)      对数增长
clearance = 0.0007 * competence * load            免疫清除
```

`competence` 是**钟形曲线**，在安全区（25）最高，两侧都会塌掉：

```
competence(x) = exp(-(x - 25)² / 450) * clamp(x / 12, 0, 1)
```

| 炎症 | 0 | 6 | 12 | 25 | 40 | 50 | 75 | 100 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 免疫力 | 0.00 | 0.22 | 0.69 | **1.00** | 0.61 | 0.25 | 0.004 | ~0 |

也就是：**炎症太低 → 免疫罢工，感染失控；炎症太高 → 免疫风暴，同样压不住病原体，
而且还会反过来伤害宿主。**

### 2. 炎症

```
response = min(load, 100) / 100 * 80                      病原体引起的反应
damped   = response * (1 - min(suppression, 1))           水杨苷抑制
overdose = max(suppression - 1, 0)                        过量部分
target   = 25 + damped - overdose * 40
inflammation += (target - inflammation) * 0.002           约 25 秒追平一半
```

* 没有病原体时 `target = 25`，正好落在 20–30 的安全区。
* 感染发展到 100 载量时 `target = 105` → 直接顶到 100，**免疫风暴**。
* `suppression = salicin / 1.0`（上限 1.5）。

### 3. 电解质

```
发烧流失 = inflammation / 100 * 0.0009 * (有症状 ? 1.5 : 1)   每 tick
健康恢复 = 0.004                                              无症状且炎症 < 40 时
```

一场没治好的感染会把电解质从 100 拖到 70 左右（实测），低电解质会加重下面所有症状。

### 4. 水杨苷代谢

线性衰减，**3 个游戏日（72000 tick）**从上限 3.0 降到 0：

```
salicin -= 3.0 / 72000   每 tick
```

一服柳树皮汤给 **+1.1**，刚好越過起效浓度 1.0。

---

## 水杨苷怎么起作用

| 情况 | 水杨苷 | 抑制 | `target` | 结果 |
| --- | --- | --- | --- | --- |
| 健康，没喝 | 0 | 0 | 25 | 安全区 |
| **感染了，喝一碗** | 1.1 | 1.1 | **21** | 反应被完全压掉，免疫力满格 → **把感染清掉** |
| 感染了，不喝 | 0 | 0 | 25 → 105 | 炎症冲进风暴区 → 免疫力塌陷 → 感染失控 |
| **喝太多**（2–3 碗） | 2.4–3.0 | 1.5 | **5** | 炎症被压到 12 以下 → **免疫抑制** → 感染反而跑得更快 |

这正是需求里说的："把炎症控制到合适范围，但喝太多会降低炎症，增加感染风险"。
**关键是浓度，不是次数**——1.1 是药，3.0 是毒。

---

## 症状

有症状（载量 ≥ 8）时：

| 症状 | 实现 |
| --- | --- |
| 挖掘速度略微降低 | `Attributes.BLOCK_BREAK_SPEED` 上挂 `ADD_MULTIPLIED_TOTAL` 修饰符，**最多 -6%**（按 `severity`），电解质 < 30 再 -5% |
| 饱食度下降略微加快 | mixin 缩放 `Player.causeFoodExhaustion` 的参数：`1 + 0.5×severity`，风暴再 +0.5，低电解质再 +0.25 |
| 视角震颤 | 每 **15 秒（300 tick）**掷一次，**20% 概率**触发，持续 16 tick |

额外（需求里没写死、按"过高的免疫风暴"这个概念补的）：

* 炎症 ≥ 75（免疫风暴）：每 2 秒刷新 `虚弱`，并额外扣饱食度；抖动概率翻倍、幅度加大。
* 炎症 ≤ 12（免疫抑制）且电解质 < 30：每 2 秒刷新 `饥饿`。

---

## 用到的 mixin

一共 3 个，都写在 **Java** 里（Kotlin 混入需要 refmap，Java 有 Loom 的注解处理器，
而且能靠 Java 编译器直接校验目标类），全部极短，只做转发：

| Mixin | 目标 | 为什么必须用 mixin |
| --- | --- | --- |
| `ItemMixin` | `Item.finishUsingItem` | 原版**没有**"吃完/喝完"事件，等到 server tick 时物品栏已经变了；Fabric 也没有对应事件 |
| `PlayerMixin` | `Player.causeFoodExhaustion` | 缩放参数才能覆盖**所有**消耗来源（走路、疾跑、挖掘、跳跃）；从 tick 里补扣会漏掉这些 |
| `CameraMixin`（客户端） | `Camera.alignWithEntity` | 原版没有镜头抖动，这个版本的 Fabric 也移除了 `ViewportEvent.ComputeCameraAngles`；只动镜头，不动玩家朝向和瞄准 |

> 挖掘速度、感染判定、抖动计时**都不需要** mixin：前者用原版属性，后两者走事件。

---

## 命令

```
/outbreak status                              查看炎症 / 电解质 / 细菌 / 病毒 / 水杨苷
/outbreak cure                                重置为健康（需要 OP）
/outbreak set <field> <value>                  调数值（需要 OP）
       field = inflammation | electrolytes | bacteria | virus | salicin
```

调试用；`status` 所有人可用。

---

## 验证

`src/main/kotlin/.../dev/OutbreakPhysiologySelfTest.kt`（**默认不启用**）跑出 **28/28 全过**：

```
PHYS homeostasis over 3 days: inflammation 25.0..25.0 electrolytes 100.0
PHYS mild infection: peak load 5.99 peak inflammation 28.4 final load 0.004
PHYS untreated infection: final load 99.97 peak inflammation 100.0 electrolytes 71.4
PHYS treated infection: final load 0.0197 peak inflammation 25.55 salicin 0.0999
PHYS overdose: final load 69.88 inflammation 5.00 salicin 3.00
PHYS salicin after 3 days: 0.0 / after 1.5 days: 1.498
PHYS competence: 0 -> 0.0  baseline -> 1.0  storm -> 0.0039
PHYS raw meat infections: 137/400 = 34.25%
PHYS rotten flesh infections: 110/400 = 27.5%
PHYS poisonous potato infections: 122/400 = 30.5%
PHYS bread infections (control): 0/400
PHYS salicin from soup: cooked=1.1 raw=1.1
PHYS exhaustion multiplier: healthy=1.0 ill=1.458
PHYS mining speed: ill=0.945 healthy=1.0
PHYS shakes over 40 rolls: 16
PHYSIOLOGY SELFTEST DONE passed=28 failed=0
```

覆盖了三件事：**纯模型**（上面 7 项，不需要世界）、**mixin 端到端**
（真的调 `ItemStack.finishUsingItem` 去吃生肉 / 喝汤，统计感染率和药物浓度）、
以及**症状**（属性修饰符、饱食度倍率、抖动计数）。

启用方式见 [`tools/README.md`](tools/README.md)。

> 开发过程中这套自检抓到过一个真 bug：`AttachmentRegistry.create(id)` 建出来的
> attachment 没有默认值，`getAttachedOrCreate` 的单参重载会直接抛
> `IllegalArgumentException` —— 那会在**每个玩家每一 tick**炸一次。已修复为带 supplier 的重载。

---

## 调参入口

所有数字都在两处，改完重新构建即可：

* `physiology/OutbreakData.kt` — 阈值、上限、水杨苷代谢
* `physiology/OutbreakPhysiology.kt` — 增长率、清除率、免疫力曲线、炎症响应
* `physiology/OutbreakInfection.kt` — 感染概率、单次载量、一服汤的药量
* `physiology/OutbreakSymptoms.kt` — 症状强度、抖动频率和概率
