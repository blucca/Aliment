[English Version](CHANGE_LOG.md) | [中文版本](CHANGE_LOG_zh.md)

# 更新日志 Changelog

本文件记录 **Aliment（供养）** 模组的所有重要更新与改动。

本更新日志遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范，
且版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/) 标准。

---

## [未发布] (Unreleased)

### 新增
- 为砂轮研磨合成添加 JEI（Just Enough Items）配方表支持。
- 添加小麦酿造与啤酒蒸馏生产链：
  - 玻璃发酵罐现支持加入小麦（`minecraft:wheat`）作为糖的替代底物。
  - 加入酿酒酵母（`aliment:brewer_yeast`）后启动发酵，生成发酵麦汁。
  - 在营火上加热发酵罐并通过冷凝管蒸馏，冷凝液流入空炼药锅将生成啤酒炼药锅（`aliment:beer_cauldron`）。
  - 手持玻璃瓶右键啤酒炼药锅即可灌装获得啤酒（`aliment:beer`，酒精度 5%），饮用后补充水分并提供酒精吸收效应。
  - 为啤酒物品、啤酒炼药锅以及发酵罐新增对应液体状态（`wheat` 与 `beer`）的材质、模型和多语言本地化。

### 变更
- 按原版风格重绘黄连、黄柏、甘草药水与啤酒的物品贴图：
  - 黄连药水（`aliment:coptis_potion`）：基于原版药水瓶与灰度液体覆层，调配鲜艳的金黄色（黄连素）药液。
  - 黄柏药水（`aliment:phellodendron_potion`）：基于原版药水瓶与灰度液体覆层，调配深邃温润的黄褐色琥珀药液。
  - 甘草药水（`aliment:licorice_potion`）：基于原版药水瓶与灰度液体覆层，调配焦糖蜜棕色的草本汤剂药液。
  - 啤酒（`aliment:beer`）：规范为原版药水瓶风格，瓶身充盈金黄透亮的啤酒，瓶颈处呈现细腻洁白的泡沫层。
- 为发酵罐投入物品交互增加手部挥动使用动画、客户端动作预测与粒子效果（投入糖冒白烟、投入小麦冒堆肥粒子、投入酵母掉落蜂蜜孢子、装水溅起水花）；在从大釜装瓶时同步增加使用动画。
