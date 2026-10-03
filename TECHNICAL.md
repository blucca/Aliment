# Aliment 技术架构与开发文档

本文档详细记录 **Aliment（爆发）** 模组的技术架构、工程规范、多语言交互接缝、构建机制及测试工具体系。

面向平台：**Minecraft 26.3**，基于 **Fabric Loader 0.19.5** 与 **Fabric API 0.161.0+26.3**。

---

## 1. 多语言分层架构

为保证生理模型数值计算的纯粹性与 Minecraft 平台逻辑的解耦，项目采用严格的单向依赖三层架构：
`src/main/scala` $\rightarrow$ `src/main/kotlin` $\rightarrow$ `src/main/java` $\rightarrow$ `src/client`

```
   src/main/scala (Scala 3.9)
   [纯数值模型，零 Minecraft/Fabric 依赖]
             │
             ▼
   src/main/java (Java 25)
   [AlimentModelBridge: 跨语言安全接缝与类型屏障]
             │
             ▼
   src/main/kotlin (Kotlin 2.4)
   [Minecraft 业务逻辑：注册表、Attachments、方块实体、事件与症状]
             │
             ▼
   src/client (Kotlin + Java)
   [HUD 口渴条、镜头振荡、迷雾收敛、后处理 Shader 视效]
```

### 1.1 Scala 3 模型层 (`src/main/scala/.../physiology/model`)
- **职责**：承载全部微分方程、生理稳态、电解质参考范围、免疫钟形曲线、体内药物动力学以及每 tick 的数值演化。
- **纯函数约束**：该层严禁导入任何 Minecraft、Kotlin、Fabric 的类库。输入输出均为纯数值与标准不可变 Case Class（`ModelState`, `ModelMineral`, `ModelMediators`, `ModelElectrolytes`, `ModelTraceElements`, `ModelDrugs`）。
- **无状态计算**：`Physiology.tick(...)` 接受当前状态与环境输入，纯函数式返回演化后的下一状态；体内 9 种药物与生物碱集中于 `ModelDrugs` 并由 `Physiology.stepDrugs` 统一推进代谢衰减。

### 1.2 Java 接缝层 (`src/main/java/.../physiology/AlimentModelBridge.java`)
- **存在的根本原因**：Kotlin K2 编译器在引用一个含有 Scala 类型的类时，会主动尝试解析其所有父接口（包括 `scala.Product`）。即使 classpath 正确配置，Kotlin 也会报错 `Cannot access 'scala.Product'`。
- **设计规范**：
  - `AlimentModelBridge.java` 是全工程**唯一允许提及 Scala 类型**的文件。
  - 所有 Scala 类型必须限制在 `private` 内部变量和方法体中，不得作为 `public` 字段、参数或返回值暴露给 Kotlin。
  - 向上仅接受并输出 Kotlin 侧的数据载体（`AlimentData`）及 Java 原生基本类型。
  - 常量全部在 Scala 模型中定义，并通过 Bridge 的静态方法重新导出，杜绝阈值双重维护。

### 1.3 Kotlin 业务层 (`src/main/kotlin/...`)
- **职责**：承载与 Minecraft 引擎对接的所有逻辑。
  - **数据持久化与 Codec 突破**：通过 Fabric Data Attachment API 将 `AlimentData` 绑定至 Player 实体。针对 Mojang DataFixerUpper `RecordCodecBuilder.instance.group(...)` 最大支持 16 个字段（`Products.P16`）的硬性限制，在内部构建扁平化辅助 `Compounds` 结构及其 `MapCodec`，内联平铺嵌入主 Codec，在不破坏 NBT 扁平向下兼容的前提下完美容纳扩展指标。
  - **生理症状**：`AlimentSymptoms` 每 tick 驱动体温、脱水掉血、电解质失衡缓慢失明/反胃、麻黄碱速掘等效果。
  - **吃喝钩子**：`AlimentIngestion` 统一处理食物/药剂下肚时的水分、电解质吸收与病原体摄入。
  - **方块与物品交互**：`AlimentInteractions` 处理潜行研磨、炼药锅投料、注射剂使用等。

### 1.4 客户端渲染层 (`src/client`)
- **HUD 扩展**：`AlimentThirstHud` 在玩家血量上方渲染 10 格独立口渴条（支持空、半格、满格状态）。
- **视效 Shader**：裸盖菇素中毒时的彩线描边与画面扭曲、高热失真、曼陀罗发热视线模糊。
- **动态迷雾**：曼陀罗抗胆碱能中毒时，通过 Mixin 将渲染迷雾硬性收敛至 8 格内，模拟真实散瞳与失焦。

---

## 2. 核心构建系统与避坑设计

### 2.1 独立的 `compileModelScala` 编译任务
Gradle 原生 Scala 插件默认的 `compileScala` 任务无条件依赖 `compileJava`，无论源码集里是否有 Java 代码。这会导致不可解的循环依赖：
$$\text{compileJava} \rightarrow \text{compileKotlin} \rightarrow \text{compileScala} \rightarrow \text{compileJava}$$

**解决方案**：
在 `build.gradle.kts` 中通过手写独立的 `ScalaCompile` 任务（命名为 `compileModelScala`），并配置四项底层约定：
1. `incrementalOptions.analysisFile`
2. `incrementalOptions.classfileBackupDir`
3. `targetCompatibility`
4. `javaLauncher`
并将原生的 `compileScala` 置空。模型编译产物以普通文件依赖（`files(compileModelScala)`）方式注入主工程编译路径。

### 2.2 IntelliJ IDEA 资源幽灵拷贝防护 (`dropIdeResourceCopies`)
当在 IntelliJ IDEA 中勾选或误选“使用 IntelliJ 运行/构建”时，IDE 会将 `src/main/resources` 拷贝至 `build/classes/java/main`。由于该目录优先于资源目录加载，未经过预处理的 `${version}` 字符串会导致 Fabric 加载崩溃，且打包时会因重复条目引发异常。

**解决方案**：
`build.gradle.kts` 注入专用任务 `dropIdeResourceCopies`，在所有 `runServer` / `runClient` 以及 `jar` 执行前自动清理 `build/classes/java/main` 中的非 `.class` 拷贝。

---

## 3. Mixin 扩展清单

| Mixin 类 | 注入目标 | 实现功能 |
| --- | --- | --- |
| `ItemMixin.java` | `net.minecraft.world.item.ItemStack` | 捕获物品被完整吃完/喝完的时刻，触发 `AlimentIngestion` 吸收逻辑 |
| `PlayerMixin.java` | `net.minecraft.world.entity.player.Player` | 动态干预饱食度消耗与极端失水脱水惩罚 |
| `GrindstoneInputSlotMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` 输入槽 | 解除原版砂轮只允许放入损坏/附魔物品的限制，允许放入树皮、岩盐、麻黄、黄连、黄柏与甘草 |
| `GrindstoneMenuMixin.java` | `net.minecraft.world.inventory.GrindstoneMenu` | 接入 `AlimentGrinding` 的研磨配方映射表，计算并输出研磨产物 |
| `CameraMixin.java` (Client) | `net.minecraft.client.Camera` | 实现发冷/寒战时仅晃动镜头的衰减振荡体验，不干扰实际实体物理位置 |
| `FogRendererMixin.java` (Client) | `net.minecraft.client.renderer.FogRenderer` | 曼陀罗中毒时将视野迷雾收缩至 8 格内 |
| `HudMixin.java` (Client) | `net.minecraft.client.gui.Gui` | 在原版生命值上方挂载口渴条渲染钩子 |

---

## 4. 自动化工具与资源生成体系 (`tools/`)

模组坚持**数据驱动与代码生成优先**原则，游戏内所有 JSON 与贴图均支持一键全量幂等生成：

### 4.1 数据生成器 (`tools/gen_data.ps1`)
- 自动提取 Minecraft 26.3 客户端 Jar 中的最新数据结构标准。
- 自动生成 400+ 份 JSON 文件：
  - `blockstates/` 与 `models/block/`：柳木全套、生熟汤炼药锅、盐水炼药锅、发酵罐、冷凝管、麻黄 4 阶段植株，以及黄连、黄柏、甘草各自 4 阶段作物植株。
  - `items/` 与 `models/item/`：全部自定义物品的模型与物品定义（包括原药材、碎药材与药水）。
  - `recipes/`：柳木建材合成表、酿酒酵母、发酵罐、冷凝管、搅拌棒、剪刀剪碎麻黄（自定义配方）、麻黄碱药水、黄连/黄柏/甘草药水合成。
  - `loot_tables/`：方块破坏掉落表（时运加成、未成熟/成熟区分）。
  - `worldgen/`：柳树河流注入、岩盐矿脉地底生成、干旱群系麻黄植被生成。
  - `lang/`：双向对齐同步 `en_us.json`、`zh_cn.json` 与 `ja_jp.json`。

### 4.2 贴图引擎 (`tools/gen_textures.ps1`, `tools/gen_ephedra_textures.ps1`, `tools/gen_herbs_textures.ps1`)
- 全量贴图通过脚本算法自动渲染，绝不依赖手工绘制。
- **原版药水贴图复合算法**：自动提取原版 `potion.png` 玻璃瓶图层与 `potion_overlay.png` 液体遮罩，采用正片叠底根据指定色调矩阵（如麻黄碱药水的琥珀金黄色、黄连药水的清亮苦黄色、黄柏药水的棕金色、甘草药水的深棕色）即时合成，像素级百分之百与原版药水风格融合。

---

## 5. 自动化开发自检框架

由于普通 JUnit 测试难以模拟真实的世界生成（WorldGen）、区块边界检查、玩家手持交互及网络同步，模组设计了基于 Fabric `FakePlayer` 的游戏内无头自检系统：

### 5.1 方块与交互测试 (`AlimentSelfTest.kt`，107 项断言)
- **树木生成与形态**：测试河流河岸检测、树干倾斜算法向水面弯曲、垂柳藤条生成。
- **方块交互**：斧头剥皮掉落树皮、砂轮输入/产出槽研磨逻辑（树皮、岩盐、麻黄、黄连、黄柏、甘草）、剪刀合成耐久扣减 1 点、炼药锅 60 秒营火加热熬汤、蒸馏冷凝管方向判定。
- **配方与战利品**：验证数据包加载后所有 RecipeSerializer 与 LootTable 的正确性。

### 5.2 生理模型与本地化自检 (`AlimentPhysiologySelfTest.kt`，400+ 项断言)
- **数值稳态**：验证健康状态各指标处于参考范围中心。
- **免疫钟形曲线**：严格验证炎症在低区、中区（有效清除）、高区（细胞因子风暴）时的病原体增长速度，以及载量突破 55 时的免疫应激风暴。
- **电解质紊乱演化**：高钠血症、低钠血症、高钾血症对实体造成的负面状态与致死机制。
- **靶向药理动力学**：
  - 水杨苷退烧抗炎、地塞米松强效平息风暴；
  - 麻黄碱每 tick 衰减（1 游戏日完全代谢）及速掘状态激活；
  - 黄连素对抗细菌：$\le 1.5$ 正常生长，$>1.5$ 减缓，$\ge 3.0$ 彻底阻断生长且始终向下压制，在 1.5 游戏日内将满额感染清零，体内 2.5 游戏日完全代谢；
  - 甘草酸对抗病毒：$\le 1.5$ 正常生长，$>1.5$ 减缓，$\ge 3.0$ 彻底阻断生长且始终向下压制，在 1.5 游戏日内将满额感染清零，体内 2.0 游戏日完全代谢。
- **本地化完整性**：自动反射所有已注册物品与方块，确保英、中、日三语翻译字典覆盖率 100%，无任何缺失未汉化键。
