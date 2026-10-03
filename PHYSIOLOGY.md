[English Version](PHYSIOLOGY.md) | [中文版本](PHYSIOLOGY_zh.md)

# Physiology

The core physiological simulation of **Aliment**: a continuous mathematical model tracking **inflammatory mediators, electrolytes, trace elements (iodine and vitamin C), hydration, core body temperature, pathogens, and pharmacological compounds** within each player.

The design objective is to make infection an evolving, organic biological process. Eating contaminated food does not deal instant damage; instead, it allows pathogens to grow, challenging and potentially overwhelming the immune system. Willow bark soup (salicin) and dexamethasone serve as clinical tools to modulate these responses, but excessive administration causes severe medical complications of its own.

Target Platform: **Minecraft 26.3** (Fabric, Scala 3.9 / Kotlin 2.4 / Java 25).

---

## Data Model

Each player possesses an `AlimentData` attachment, comprised of: inflammatory mediators, pathogen loads, hydration, serum electrolytes, trace elements (iodine and vitamin C), active drug concentrations, and core body temperature.

### Inflammatory Mediators `Mediators`

Inflammation is not represented by a single arbitrary health bar, but as a **weighted composite** of five biological mediators:

$$\mathcal{I} = \sum_{i=1}^5 w_i M_i = 0.15 \cdot H + 0.20 \cdot P + 0.15 \cdot L_k + 0.35 \cdot C + 0.15 \cdot B$$

where:
* $H$: Histamine
* $P$: Prostaglandin
* $L_k$: Leukotriene
* $C$: Cytokine
* $B$: Bradykinin

| Mediator | Weight $w_i$ | Biological Role | Primary Therapeutic Inhibitor |
| --- | --- | --- | --- |
| Histamine `histamine` ($H$) | 0.15 | Vasodilation, pruritus, edema (mast cell degranulation) | Mildly reduced by both |
| Prostaglandin `prostaglandin` ($P$) | 0.20 | Pain, pyrogenesis / fever (COX pathway) | **Salicin** (aspirin mechanism) |
| Leukotriene `leukotriene` ($L_k$) | 0.15 | Bronchoconstriction, mucus secretion | **Dexamethasone** |
| Cytokine `cytokine` ($C$) | 0.35 | Systemic fever, primary driver of cytokine storms | **Dexamethasone** (most potent) |
| Bradykinin `bradykinin` ($B$) | 0.15 | Pain, vascular permeability | Salicin |

In a healthy resting state, the mediators sit at `(H, P, L_k, C, B) = (25.0, 30.0, 30.0, 20.0, 25.0)`, yielding a weighted sum of **exactly 25.0** (the center of the normal physiological safe zone):

$$\mathcal{I}_{\text{resting}} = 0.15(25) + 0.20(30) + 0.15(30) + 0.35(20) + 0.15(25) = 25.0$$

Detailed formulas are located in `ModelMediators.getInflammation` (weights defined in `MediatorLevels`).

### Serum Electrolytes `ModelElectrolytes` and Trace Elements `ModelTraceElements`

**Values use real-world clinical units**: electrolytes in **mmol/L**, and trace elements in **µmol/L** (as plasma physiological concentrations are several orders of magnitude smaller).

Each mineral has an established **clinical reference range**, with **bilateral thresholds** where both deficiency and excess constitute pathological states:

```
   min    severeLow   safeLow   normal   safeHigh   severeHigh    max
   |    Severe Deficit  |  Mild Deficit  |    Healthy   |   Mild Excess   |    Severe Excess    |
```

| Mineral | Baseline Normal | Reference Range | Mild Boundary | Severe Boundary | Unit |
| --- | --- | --- | --- | --- | --- |
| Sodium `sodium` | 140 | **135 – 145** | 125 / 150 | 100 / 190 | mmol/L |
| Potassium `potassium` | 4.2 | **3.5 – 5.0** | 3.0 / 6.0 | 1.5 / 9.0 | mmol/L |
| Magnesium `magnesium` | 0.85 | **0.70 – 1.00** | 0.50 / 1.50 | 0.20 / 3.0 | mmol/L |
| Chloride `chloride` | 101 | **96 – 106** | 90 / 115 | 70 / 140 | mmol/L |
| Calcium `calcium` | 2.35 | **2.10 – 2.60** | 1.80 / 3.00 | 1.00 / 4.0 | mmol/L |
| **Iodine `iodine`** | 0.50 | **0.40 – 0.80** | 0.20 / 1.20 | 0.05 / 2.0 | **µmol/L** |
| **Vitamin C `vitamin_c`** | 60.0 | **40.0 – 80.0** | 40.0 / 80.0 | 15.0 / 120.0 | **µmol/L** |

**Electrolytes are regulated homeostatically back to baseline**: the endogenous restoration rate is `(normal - current) * 0.00005` (time constant 20,000 ticks, under one game day). **Trace elements (iodine and vitamin C) are exceptions**: they lack endogenous synthesis and must be maintained through dietary intake.

**Because units reflect reality, dietary salt intake produces realistic consequences**: one serving of crude salt or sea water food provides **+3.0 mmol/L sodium**, and refined salt provides **+3.5 mmol/L**. Since the upper normal threshold for sodium is 145:
* **Consuming 1 serving**: 140 → 143 or 143.5 mmol/L (**remains safely within normal range**);
* **Consuming 2 servings**: 140 → 146 or 147 mmol/L (**exceeds reference range**, triggering hypernatremic thirst).

### Iodine
The human body cannot synthesize iodine and **does not retain it permanently**:

| Food Item | Iodine Contribution | Nutritional Value |
| --- | --- | --- |
| Kelp `minecraft:kelp` | **+0.10 µmol/L** | Nutrition: 1 / Saturation: 0.6 |
| Dried Kelp `minecraft:dried_kelp` | **+0.20 µmol/L** | Nutrition: 1 / Saturation: 0.6 |
| Seaweed `aliment:seaweed` | **+0.20 µmol/L** | Nutrition: 1 / Saturation: 0.2; renewable in seabed farms |
| Cooked Seaweed `aliment:cooked_seaweed` | **+0.25 µmol/L** | Nutrition: 3 / Saturation: 0.6; high-density iodine source |
| Seaweed Iodized Salt `aliment:seaweed_iodized_salt` | **+0.40 µmol/L** | Adds **+1.5 mmol/L Na and Cl**; balances electrolytes and prevents hypothyroidism |

Without dietary iodine, plasma concentrations steadily drain at a fixed rate of 0.15 µmol/L per day, **depleting completely to the floor (0.05 µmol/L) in exactly 3 in-game days**:
* **Daily Maintenance**: 0.15 µmol/L is lost daily via basal metabolism (accelerated during fever and sweating).
* **Dietary Strategy**:
  * **Daily Upkeep**: 1 dried kelp (+0.20) or raw seaweed (+0.20) comfortably covers a day's basal decay.
  * **Acute Treatment**: 2 cooked seaweeds (+0.25 each) replenish a severely depleted thyroid reserve back toward the upper safe limit.
  * **Expedition Supply**: Seaweed iodized salt (+0.40) provides substantial iodine while concurrently replenishing sodium and chloride (+1.5 mmol/L each) to counteract dilutional hyponatremia from drinking water.

### Vitamin C (Ascorbic Acid)
Vitamin C cannot be synthesized endogenously by humans. Serum reference values are **40.0 – 80.0 µmol/L** (baseline healthy homeostasis is **60.0 µmol/L**).

* **First-Order Clearance Kinetics (Concentration-Dependent Excretion)**:

  $$\frac{dC}{dt} = -k \cdot C, \quad k = \frac{\ln(2)}{120000} \approx 5.776 \times 10^{-6}\text{ / tick}$$

  - **Excretion rate is directly proportional to current plasma concentration**: higher levels clear rapidly through the kidneys, while lower concentrations clear more slowly.
  - **Half-life is 5 in-game days (120,000 ticks)**: Starting from the upper safe boundary (**80.0 µmol/L**), it takes exactly **5 game days** of zero botanical intake to decay to the abnormal threshold line (**40.0 µmol/L**).
* **Clinical Symptoms**:
  - **Mild Deficiency (< 40.0 µmol/L, subclinical deficiency)**: Impaired collagen synthesis and muscular stamina impart **Mining Fatigue I**.
  - **Severe Deficiency (< 15.0 µmol/L, scurvy)**: Profound physical exhaustion and tissue breakdown add **Weakness I** (simultaneously inflicting Mining Fatigue I + Weakness I).
  - **Excess (> 80.0 µmol/L)**: Ascorbic acid is water-soluble; excess concentrations are safely excreted without adverse pathology.
* **Dietary Sources (Fruits, Root Vegetables, and Plant Foods)**:

| Food Item | Vitamin C Yield |
| --- | --- |
| Apple `minecraft:apple` | **+12.0 µmol/L** |
| Golden Apple `minecraft:golden_apple` | **+20.0 µmol/L** |
| Enchanted Golden Apple `minecraft:enchanted_golden_apple` | **+30.0 µmol/L** |
| Melon Slice `minecraft:melon_slice` | **+8.0 µmol/L** |
| Sweet Berries / Glow Berries | **+6.0 µmol/L** |
| Carrot `minecraft:carrot` | **+10.0 µmol/L** |
| Golden Carrot `minecraft:golden_carrot` | **+15.0 µmol/L** |
| Pumpkin Pie `minecraft:pumpkin_pie` | **+15.0 µmol/L** |
| Beetroot `minecraft:beetroot` | **+6.0 µmol/L** |
| Beetroot Soup `minecraft:beetroot_soup` | **+16.0 µmol/L** |
| Mandrake Fruit `aliment:mandrake_fruit` | **+10.0 µmol/L** |
| Seaweed `aliment:seaweed` | **+5.0 µmol/L** |
| Cooked Seaweed `aliment:cooked_seaweed` | **+3.0 µmol/L** |

### Hydration `water`

| Parameter | Value |
| --- | --- |
| Normal Range | **30 – 100** |
| Ceiling | 200 |
| Above 100 | **Overhydration**: Weakness + Reduced mining speed |
| Below 30 | **Dehydration** |
| **Basal Water Depletion** | **5 game days from full (100) to empty (0)** (without fever or sweating) |
| **Fever at 39 °C** | **3.5 game days to empty (100 → 0)** (accelerated sweating) |
| **Severe Hyperthermia at 40 °C** | **2 game days to empty (100 → 0)** (profuse sweating) |
| Hydration per Beverage | **+15** (water, potions, stew, willow bark soups, and salted variants) |

Above 100, kidneys accelerate excretion (up to 2x normal rate), concurrently **diluting and washing out serum electrolytes**; high sodium and high calcium also intensify thirst. Temperatures exceeding 38.25 °C activate non-linear diaphoresis (sweating), multiplying water loss with higher fevers.

### Core Body Temperature `temperature`

| Parameter | Value |
| --- | --- |
| Baseline | **37.0 °C** |
| Comfort Zone | **36.0 – 38.5 °C** (no adverse symptoms) |
| Fever / Hyperthermia | ≥ **38.5 °C** / ≥ **40.0 °C** |
| Standard Immune Fever Ceiling | **39.5 °C** (during regular immune response, pathogen load ≤ 55) |
| Stress Storm Fever Ceiling | **42.0 °C** (during severe infection load > 55 or massive pyrogen insult) |
| Mild / Severe Hypothermia | ≤ **36.0 °C** / ≤ **35.0 °C** |
| Model Limits | 30.0 °C – 42.0 °C |
| Thermal Integration Rate | 0.0004 / tick (approx. 2500 ticks to traverse 63% of the delta; ~2 minutes) |

> Fever threshold begins at **38.5 °C** rather than 38.0 °C. 38.0 °C can be reached naturally in warm biomes with slight thyroid activation without requiring antipyretics. During standard immune response (loads 20–55), fever is capped at 39.5 °C; only when loads exceed 55 and trigger cytokine storms does temperature surge past 40.0 °C.

Core temperature is governed by four contributing factors: **prostaglandins** from immune stimulation, injected **pyrogens**, **thyroid** offsets from iodine, and **ambient environment**.

### Pharmacology and Anti-Inflammatory Therapeutics

| Drug | Active Threshold | Ceiling | Clearance Window | Pharmacological Mechanism |
| --- | --- | --- | --- | --- |
| Salicin `salicin` | 1.0 | 3.0 | **3 game days** (linear) | COX inhibitor suppressing prostaglandin synthesis; reduces fever, relieves pain, and dampens inflammation |
| Dexamethasone `dexamethasone` | 1.0 | 2.0 | **2 game days** (linear) | Glucocorticoid; strongly suppresses cytokine and leukotriene transcription; arrests cytokine storms |

* **Zero-Order Metabolic Elimination Rates**:

  $$\frac{d[\text{Salicin}]}{dt} = - \frac{3.0}{72000} = - \frac{1}{24000} \approx -4.167 \times 10^{-5}\text{ / tick}$$

  $$\frac{d[\text{Dex}]}{dt} = - \frac{2.0}{48000} = - \frac{1}{24000} \approx -4.167 \times 10^{-5}\text{ / tick}$$

> **Critical Distinction**: **Neither salicin nor dexamethasone directly kills or clears pathogens.** Their clinical role is strictly **anti-inflammatory and immunosuppressive**:
> - Proper dosage prevents fatal cytokine storms (inflammation ≥ 75 destroys host tissue and causes immune collapse);
> - **Overdosing** suppresses resting inflammation below the minimum immune clearance band (< 12), causing **iatrogenic immunosuppression**. Because pathogen clearance requires an active immune system, immunosuppressed players lose clearance capability, causing pathogens to grow unrestricted!

### Mandrake Alkaloids `scopolamine` / `atropine`

Mandrake fruit and seeds introduce two independent alkaloids, each capped at **5.0**, **clearing linearly over 1 game day**:

$$\frac{d S_{\text{scop}}}{dt} = - \frac{5.0}{24000} \approx -2.083 \times 10^{-4}\text{ / tick}, \quad \frac{d A_{\text{atro}}}{dt} = - \frac{5.0}{24000} \approx -2.083 \times 10^{-4}\text{ / tick}$$

| Ingested Item | Scopolamine ($S_{\text{scop}}$) | Atropine ($A_{\text{atro}}$) |
| --- | --- | --- |
| `mandrake_fruit` | **+1.0** | **+0.1** |
| `mandrake_seeds` | **+0.75** | **+0.1** |

Their combined load $\Sigma_{\text{alk}} = S_{\text{scop}} + A_{\text{atro}}$ modulates **temperature** (elevates hypothalamic set point independently of prostaglandins, meaning **salicin cannot reduce mandrake fever**):

$$\Delta T_{\text{anticholinergic}} = \begin{cases} 0\ ^\circ\text{C}, & \Sigma_{\text{alk}} < 1.5 \\ 1.0\ ^\circ\text{C} \implies T \to 38.0\ ^\circ\text{C}, & 1.5 \le \Sigma_{\text{alk}} < 2.5 \\ 2.5\ ^\circ\text{C} \implies T \to 39.5\ ^\circ\text{C}, & 2.5 \le \Sigma_{\text{alk}} < 4.0 \\ 4.0\ ^\circ\text{C} \implies T \to 41.0\ ^\circ\text{C}, & \Sigma_{\text{alk}} \ge 4.0 \end{cases}$$

while individual and combined levels dictate **visual blur**:

$$\text{Visual Blur Active} \iff S_{\text{scop}} \ge 2.3 \lor A_{\text{atro}} \ge 2.3 \lor \Sigma_{\text{alk}} \ge 2.7$$

When active, render fog contracts down to **8 blocks**; distant blocks become blurred.

Drug fever and visual blur operate independently and stack with infection effects: eating multiple mandrakes causes both severe hyperthermia and blurred vision.

### Psilocybin and Psilocin `psilocybin` / `psilocin`

Ingesting raw *Gymnopilus* (cooked mushrooms destroy both alkaloids) introduces two compounds, capped at **10.0**:

| Compound | Biological Action | Clearance / Kinetics |
| --- | --- | --- |
| Psilocybin `psilocybin` | Inactive prodrug | Converted 1:1 into psilocin over **half a game day** (1.3 per half day) |
| Psilocin `psilocin` | Active psychedelic compound; visual distortions and drug fever | Fixed metabolic clearance rate of **1.3 per game day** (zero-order elimination) |

* **Conversion and Elimination Dynamics**:

  $$\frac{d[\text{Psilocybin}]}{dt} = - \min\left([\text{Psilocybin}], \frac{1.3}{12000}\right)$$

  $$\frac{d[\text{Psilocin}]}{dt} = \min\left([\text{Psilocybin}], \frac{1.3}{12000}\right) - \frac{1.3}{24000}$$

  where $\frac{1.3}{12000} \approx 1.083 \times 10^{-4}\text{ / tick}$, and $\frac{1.3}{24000} \approx 5.417 \times 10^{-5}\text{ / tick}$.

One raw mushroom yields **1.3 psilocybin and 1.3 psilocin** (total 2.6 units), clearing in **exactly two game days**. Because clearance is zero-order rather than exponential, consuming five mushrooms (13 units total) extends the trip linearly to **ten game days**.

Four visual stages are rendered via client post-processing shaders according to active `psilocin` levels:

| Threshold | Visual Effect |
| --- | --- |
| $[\text{Psilocin}] > 1.2$ | **Edge Ray Projection**: Directional prismatic chromatic streaks emit outwards from block edges. |
| $[\text{Psilocin}] > 1.7$ | **Chromatic Color Shifts**: Blocks blend with oscillating bright hues; subtle full-screen chromatic noise overlays the scene. |
| $[\text{Psilocin}] > 2.5$ | **Spatial Warping**: Mild full-screen sinusoidal distortion waves bend viewport geometry. |
| $[\text{Psilocin}] > 5.0$ | **Intense Hallucinatory Warping**: Violent spatial distortion accompanied by rising core body temperature (up to **39.0 °C**). |
| $[\text{Psilocin}] \ge 7.0$ | Drug fever reaches up to **41.0 °C**. |

### Data Persistence Architecture

Backed by Fabric's **Data Attachment API**:

| Attachment Key | Persisted | Network Synced | Purpose |
| --- | --- | --- | --- |
| `aliment:physiology` | Yes (**Resets on Death**) | No | Authoritative full state on the server. Does not use `copyOnDeath`: a respawned body is fresh and infection-free. |
| `aliment:client_state` | Yes | Yes (to client) | Shiver sequence, oscillation amplitude, and integer hydration for HUD and camera rendering. |
| `aliment:runtime` | No | No | Ephemeral tick counters, bat contact cooldowns, and last-synced caches. |

Hydration packets are dispatched only when **integer values change**, reducing network traffic to ~1 packet per 270 ticks during steady dehydration.

### Creative Mode Immunity

`AlimentSymptoms.isFrozen(player)` activates in Creative mode:
* **Physiology ticks freeze**: pathogens do not grow or clear, temperature does not adjust, and drugs do not metabolize.
* **Symptoms are suppressed**: no effect icons, no sepsis damage, no mining fatigue, and exhaustion multipliers stay at 1.0.
* **Shaders and HUD clear**: active post-processing effects and camera shivering are dismissed immediately.
* **Ingestion is blocked**: eating or drinking does not modify internal physiology values.
Entering Creative mode freezes state rather than resetting it; returning to Survival resumes the exact physiological state.

---

## Infection Pathways

Pathogens enter the body through three deterministic infection pathways:

| Source | Pathogen | Probability | Inoculum Size |
| --- | --- | --- | --- |
| Raw meat (beef, pork, chicken, mutton, rabbit, fish), rotten flesh, poisonous potato | Bacteria | **30%** | +6 load |
| Raw willow bark soup (including salted variants) | Bacteria | **30%** | +6 load |
| Proximity within 2 blocks of **any living mob** | Virus | **5% / second** | +5 load |
| **Severe Immunosuppression** (inflammation ≤ 12) | Bacteria | **15% / second** | **+4 to +12 (random)** |

* Food transmission triggers on `Item.finishUsingItem`.
* Contact transmission evaluates against all nearby `Mob` entities (cows, wolves, villagers, bats). Standing near dense livestock for 10 seconds almost guarantees viral inoculation.
* **Opportunistic Infection**: When inflammation drops to ≤ 12, opportunistic bacterial colonization occurs without external exposure, making excessive dosing of salicin or dexamethasone genuinely dangerous.

---

## Mathematical Model

The physiological model integrates per tick (`AlimentPhysiology.tick(data)`), decoupled from Minecraft engine classes.

### Infection Dynamics and Immune Competence

The baseline logistic proliferation rate of pathogens per tick:

$$\frac{dL_{\text{base}}}{dt} = r \cdot L \cdot \left(1 - \frac{L}{K}\right)$$

where $r = 0.0004\text{ / tick}$ and carrying capacity $K = 100.0$.

The active immune clearance rate:

$$C_{\text{immune}} = \begin{cases} 0, & \text{if } \neg\text{immuneActive} \land L \le 20.0 \\ \left(\dfrac{dL_{\text{base}}}{dt} + c_0\right) \cdot \eta(\mathcal{I}), & \text{otherwise} \end{cases}$$

where $c_0 = \dfrac{20.0}{48000} \approx 4.1667 \times 10^{-4}\text{ / tick}$, and $\eta(\mathcal{I})$ is the immune competence function of composite inflammation $\mathcal{I}$.

- **Covert Growth Phase ($L \le 20.0$)**:
  - Pathogens reproduce logistically unchecked by the immune system ($\neg\text{immuneActive} \implies C_{\text{immune}} = 0$).
  - Inflammation remains resting ($\mathcal{I} = 25.0$).
  - Core body temperature remains normal ($T = 37.0\ ^\circ\text{C}$).
- **Immune Engagement Phase ($20.0 < L \le 55.0$)**:
  - Exceeding load $20.0$ flags `immuneActive = true`, initiating mediator release.
  - Elevated prostaglandins raise core temperature up to $39.5\ ^\circ\text{C}$.
  - At optimal competence ($\eta(\mathcal{I}) = 1.0$), active clearance eradicates the infection within **2 in-game days (48,000 ticks)**.
  - Upon reaching load $0$, `immuneActive` resets to `false`, and mediators return to baseline.
- **Cytokine Storm and Septic Shock ($L > 55.0$)**:
  - If immune clearance fails (due to drug-induced immunosuppression or deficiency), infection surpasses load $55.0$.
  - Severe biological stress triggers a 2.5x cytokine surge, driving inflammation into storm territory ($\mathcal{I} \ge 75.0$).
  - Temperature ceiling unlocks to $42.0\ ^\circ\text{C}$, and septic magic damage is inflicted directly on the player.

The immune competence $\eta(\mathcal{I})$ follows an asymmetric Gaussian bell curve centered at $\mathcal{I}_{\text{optimal}} = 25.0$:

$$\eta(\mathcal{I}) = \begin{cases} \exp\left(-\dfrac{(\mathcal{I} - 25.0)^2}{2 \cdot 10.0^2}\right), & \mathcal{I} < 25.0 \\[8pt] \exp\left(-\dfrac{(\mathcal{I} - 25.0)^2}{2 \cdot 15.0^2}\right), & \mathcal{I} \ge 25.0 \end{cases}$$

| Inflammation $\mathcal{I}$ | 0 | 6 | 12 | 25 | 40 | 50 | 75 | 100 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Competence $\eta(\mathcal{I})$ | 0.00 | 0.22 | 0.69 | **1.00** | 0.61 | 0.25 | 0.004 | ~0 |

**Too little inflammation paralyzes immune response; excessive inflammation causes immune storm collapse while harming the host.**

### Mediator Kinetics

Biological inflammatory stimulus $S(L)$ as a function of total pathogen load $L$:

$$S(L) = \begin{cases} 0, & \text{if } \neg\text{immuneActive} \land L \le 20.0 \\ \dfrac{L - 20.0}{35.0}, & \text{if } 20.0 < L \le 55.0 \\ 1.0 + 2.5 \cdot \dfrac{L - 55.0}{45.0}, & \text{if } L > 55.0 \end{cases}$$

Pharmacological suppression and damping (Salicin & Dexamethasone):
Let $D_{\text{sal}}$ and $D_{\text{dex}}$ be current active concentrations ($D_{\text{sal,eff}} = 1.0, D_{\text{dex,eff}} = 1.0$):

$$s_{\text{sal}} = \frac{D_{\text{sal}}}{1.0}, \quad s_{\text{dex}} = \frac{D_{\text{dex}}}{1.0}$$

$$f_{\text{sal}} = \min(s_{\text{sal}}, 1.0), \quad f_{\text{dex}} = \min(s_{\text{dex}}, 1.0)$$

$$\delta_{\text{damp}} = \max\left(1.0 - 0.88 \cdot \max(f_{\text{sal}}, f_{\text{dex}}), 0.05\right)$$

$$\text{Overdose} = \max(s_{\text{sal}} - 1.0, 0) + \max(s_{\text{dex}} - 1.0, 0)$$

$$\beta_{\text{base}} = \max(1.0 - 0.5 \cdot \text{Overdose}, 0.0)$$

Target mediator asymptotic values ($M_{i,\text{target}}$):

$$\begin{aligned} C_{\text{target}} &= C_{\text{base}} \cdot \beta_{\text{base}} + 100.0 \cdot S(L) \cdot \delta_{\text{damp}} \cdot (1.0 - 0.4 \cdot f_{\text{dex}}) \\ H_{\text{target}} &= H_{\text{base}} \cdot \beta_{\text{base}} + 45.0 \cdot S(L) \cdot \delta_{\text{damp}} \\ B_{\text{target}} &= B_{\text{base}} \cdot \beta_{\text{base}} + 60.0 \cdot S(L) \cdot \delta_{\text{damp}} \\ P_{\text{target}} &= P_{\text{base}} \cdot \beta_{\text{base}} + \left(0.5 \cdot C_{\text{target}} + 25.0 \cdot S(L)\right) \cdot \delta_{\text{damp}} \cdot (1.0 - 0.5 \cdot f_{\text{sal}}) \\ L_{k,\text{target}} &= L_{k,\text{base}} \cdot \beta_{\text{base}} + \left(0.5 \cdot C_{\text{target}} + 20.0 \cdot S(L)\right) \cdot \delta_{\text{damp}} \cdot (1.0 - 0.4 \cdot f_{\text{dex}}) \end{aligned}$$

where resting baseline values are $(H_{\text{base}}, P_{\text{base}}, L_{k,\text{base}}, C_{\text{base}}, B_{\text{base}}) = (25.0, 30.0, 30.0, 20.0, 25.0)$.

Each mediator approaches its target value via exponential relaxation:

$$\frac{dM_i}{dt} = k_m \cdot (M_{i,\text{target}} - M_i), \quad k_m = 0.002\text{ / tick}$$

Prostaglandins ($P$) and leukotrienes ($L_k$) are **downstream products induced by cytokines ($C$)**, explaining why dexamethasone (acting upstream) and salicin (acting downstream) possess distinct clinical profiles.

### Targeted Antimicrobial Pharmacokinetics (Berberine & Glycyrrhizin)

Unlike symptomatic anti-inflammatories, **Berberine** and **Glycyrrhizin** directly attack and clear pathogens:

For targeted drug concentration $D \in [0.0, 7.0]$, with deceleration threshold $D_{\text{slow}} = 1.5$ and suppression threshold $D_{\text{suppress}} = 3.0$:

$$\frac{dL}{dt} = \begin{cases} \dfrac{dL_{\text{base}}}{dt} - C_{\text{immune}}, & D \le 1.5 \\[6pt] \dfrac{dL_{\text{base}}}{dt} \cdot \left(1.0 - 0.75 \cdot \dfrac{D - 1.5}{1.5}\right) - C_{\text{immune}}, & 1.5 < D < 3.0 \\[6pt] - \left( C_{\text{immune}} + C_{\text{drug}} \right), & D \ge 3.0 \end{cases}$$

where:

$$C_{\text{drug}} = c_{\text{suppress}} \cdot \frac{D}{D_{\text{suppress}}}, \quad c_{\text{suppress}} = \frac{100.0}{1.5 \times 24000} \approx 2.778 \times 10^{-3}\text{ / tick}$$

- **Berberine ($0.0 \sim 7.0$)**: Specifically targets **Bacteria** ($L = \text{Bacteria}$)
  - Clears from peak 7.0 over **2.5 game days (60,000 ticks)**:

    $$\frac{d[\text{Berberine}]}{dt} = - \frac{7.0}{60000} \approx -1.167 \times 10^{-4}\text{ / tick}$$

- **Glycyrrhizin ($0.0 \sim 7.0$)**: Specifically targets **Viruses** ($L = \text{Virus}$)
  - Clears from peak 7.0 over **2.0 game days (48,000 ticks)**:

    $$\frac{d[\text{Glycyrrhizin}]}{dt} = - \frac{7.0}{48000} \approx -1.458 \times 10^{-4}\text{ / tick}$$

### Hydration and Sweating Model

Total hydration $W$ depletion per tick:

$$\frac{dW}{dt} = - (\Phi_{\text{renal}} + \Phi_{\text{sweat}})$$

Kidney diuresis $\Phi_{\text{renal}}$ accelerates when overhydrated ($W > 100.0$):

$$\Phi_{\text{renal}} = k_w \cdot \left(1.0 + 0.01 \cdot \max(W - 100.0, 0)\right) \cdot \gamma_{\text{Na}} \cdot \gamma_{\text{Ca}}$$

where $k_w = \frac{100.0}{5.0 \times 24000.0} = \frac{1}{1200} \approx 8.333 \times 10^{-4}\text{ / tick}$, $\gamma_{\text{Na}} = 1.3$ if $[\text{Na}] > 150.0$, and $\gamma_{\text{Ca}} = 1.2$ if $[\text{Ca}] > 3.0$.

Diaphoresis $\Phi_{\text{sweat}}$ activates when core temperature $T > 38.25\ ^\circ\text{C}$ ($\Delta T = T - 37.0 > 1.25$):

$$\Phi_{\text{sweat}} = a \cdot \Delta T + b \cdot (\Delta T)^2, \quad a = -\frac{1}{3360}, \quad b = \frac{1}{4200}$$

Total Water Depletion Benchmarks:
- $37.0\ ^\circ\text{C}$ (Basal): $\frac{dW}{dt} = -\frac{1}{1200}\text{ / tick}$ ($100$ units consumed in exactly 5 game days).
- $39.0\ ^\circ\text{C}$ (Fever): $\frac{dW}{dt} = -\frac{1}{840}\text{ / tick}$ ($100$ units consumed in 3.5 game days).
- $40.0\ ^\circ\text{C}$ (Severe Hyperthermia): $\frac{dW}{dt} = -\frac{1}{480}\text{ / tick}$ ($100$ units consumed in 2.0 game days).

### Electrolyte Clearance and Homeostasis

The dynamics of each serum electrolyte $E_i$:

$$\frac{dE_i}{dt} = k_h \cdot (E_{i,\text{norm}} - E_i) - (\Lambda_{\text{flush}} + \Lambda_{\text{sweat}}) \cdot \xi_i \cdot E_{i,\text{norm}}$$

where:

$$\Lambda_{\text{flush}} = \text{clamp}\left(\frac{\max(W - 100.0, 0)}{100.0}, 0, 1\right) \cdot 9 \times 10^{-6}\text{ / tick}$$

$$\Lambda_{\text{sweat}} = \begin{cases} \max(T - 37.0, 0) \cdot 2 \times 10^{-6}\text{ / tick}, & T > 38.25\ ^\circ\text{C} \\ 0, & T \le 38.25\ ^\circ\text{C} \end{cases}$$

$$k_h = 0.00005\text{ / tick} \quad (\approx 20,000\text{ ticks restoration half-life})$$

Relative clearance coefficients: $\xi = (\text{Na}: 1.0, \text{Cl}: 1.0, \text{K}: 0.7, \text{Mg}: 0.4, \text{Ca}: 0.4)$.
Sodium and chloride wash out fastest; over-drinking fresh water quickly triggers dilutional hyponatremia.

### Thermal Balance

Target core temperature $T_{\text{target}}$:

$$T_{\text{target}} = 37.0 + \Delta T_{\text{fever}} + P_{\text{pyrogen}} + \Delta T_{\text{thyroid}} + \Delta T_{\text{anticholinergic}} + \Delta T_{\text{psilocin}} + \Delta T_{\text{ambient}}$$

where:

$$\Delta T_{\text{fever}} = \begin{cases} \min\left(0.05 \cdot \max(P - 30.0, 0), 2.5\right), & L \le 55.0 \\ \min\left(0.05 \cdot \max(P - 30.0, 0), 4.0\right), & L > 55.0 \end{cases}$$

$$\Delta T_{\text{thyroid}} = \text{clamp}\left(2.0 \cdot \frac{I - I_{\text{safe}}}{0.50}, -0.8, 0.8\right)$$

$$\Delta T_{\text{ambient}} = 0.6 \cdot (T_{\text{ambient}} - 37.0)$$

Thermal relaxation rate toward target:

$$\frac{dT}{dt} = k_T \cdot (T_{\text{target}} - T), \quad k_T = 0.0004\text{ / tick}$$

| Ambient Condition | Thermal Offset (°C) |
| --- | --- |
| Biome Ambient (per 1.0 biome temperature) | ×2.0 relative to temperate 0.8 |
| Neutral Comfort Zone | **Ignored if within ±2.0 °C** |
| Submerged in Water / Rain | −2.5 °C |
| Buried in Powder Snow | −4.0 °C (stacks with wetness) |
| On Fire | +4.0 °C |
| In Lava | +6.0 °C |

---

## Clinical Symptoms

### Pathogen Symptoms (Load ≥ 8)
- Mining speed reduced up to **-6%**.
- Food exhaustion multiplier increased up to `1 + 0.5 * severity`.
- Camera tremors: rolled every **15 seconds** at a **20% chance** for 16 ticks.
- **Septic Magic Damage (Load ≥ 60)**: `1 + (load - 60) / 40` damage every two seconds, ignoring armor.

### Hydration Symptoms
- Water > 100: Weakness, mining speed -8%, increased food exhaustion.
- Water > 150: **Nausea** (dilutional hyponatremia). Camera shivering only unlocks after this threshold.
- Water < 15: Hunger.

### Mineral Disorders

| Mineral | Severe Deficit | Mild Deficit | Mild Excess | Severe Excess |
| --- | --- | --- | --- | --- |
| Sodium | Nausea + Slowness (Confusion) | Weakness | Hunger (Thirst) | Hunger + Weakness |
| Potassium | Weakness II + Mining Fatigue | Weakness | Weakness | Slowness II + Periodic Magic Damage (Arrhythmia) |
| Magnesium | Weakness + Slowness (Tremor) | Weakness | Slowness | Slowness + Weakness (Lethargy) |
| Chloride | Nausea (Alkalosis) | Weakness | Hunger (Acidosis) | Hunger + Nausea |
| Calcium | Slowness + Weakness (Tetany) | Weakness | Slowness | Slowness II + Weakness (Lethargy) |
| Iodine | Slowness II + Weakness II + Fatigue | Weakness + Slowness + Hunger | Hunger + Nausea | Adds Weakness |
| Vitamin C | Mining Fatigue + Weakness | Mining Fatigue | None (Safely Excreted) | None (Safely Excreted) |

### Core Temperature Symptoms
- **Fever (38.5 – 40.0 °C)**: Weakness + Mining Fatigue, perimeter heat shimmer (`aliment:heat_haze`).
- **Severe Hyperthermia (≥ 40.0 °C)**: Adds post-processing dynamic motion blur (`aliment:heat_blur`).
- **Hypothermia (≤ 36.0 °C)**: Weakness + Mining Fatigue + Slowness, perimeter cold shivering tint (`aliment:cold_shiver`).

---

## Applied Mixins

Implemented in **Java** to allow Loom annotation processing and compile-time verification:

| Mixin | Target | Technical Requirement |
| --- | --- | --- |
| `ItemMixin` | `Item.finishUsingItem` | Hooks finished ingestion before server tick inventory mutation |
| `PlayerMixin` | `Player.causeFoodExhaustion` | Scales all exhaustion sources uniformly |
| `GrindstoneInputSlotMixin` | `GrindstoneMenu$2` / `$3.mayPlace` | Allows botanical herbs, salt ore, and bark into vanilla grindstone slots |
| `GrindstoneMenuMixin` | `GrindstoneMenu.computeResult` | Integrates `AlimentGrinding` recipe translation |
| `CameraMixin` (Client) | `Camera.alignWithEntity` | Injects rotational camera shake without moving physical entity hitboxes |
| `FogRendererMixin` (Client) | `FogRenderer.setupFog` | Contracts visual fog distance to 8 blocks during anticholinergic intoxication |
| `HudMixin` (Client) | `Hud.extractPlayerHealth` | Positions thirst HUD directly above player health hearts |

---

## Salt Manufacture and Processing

```
Rock Salt Ore ──Grindstone──> Crude Salt ×9 ──Grindstone──> Crude Salt Powder
                                                    │
                                                    ├─ Use on Water Cauldron ──> Brine Cauldron
                                                    │                                │
                                                    │                         Campfire underneath
                                                    │                                │ Evaporates every 30s
                                                    │                                ▼
                                                    │                       Boiled Dry ──> Salt Powder ×1
                                                    │
Crude Salt + Water/Soup/Stew ──> Crude Salt Water / Crude Salt Broth
Salt Powder + Water/Soup/Stew ──> Salt Water / Refined Salt Broth
```

* **Stirring Rod**: Right-click a brine cauldron to advance evaporation by one stage, consuming 1 durability (16 max). Crafted with **two sticks vertically aligned**.
* Brine cauldrons feature 3 visual concentration stages (`stage 0/1/2`).
* Removing the campfire pauses evaporation without resetting progress.
* Rock salt ore drops itself and requires a stone pickaxe or better, generating between y=20–90 at 6 veins per chunk.

---

## Diagnostic Commands

```
/aliment status                      Inspect full physiological metrics
/aliment fever [temperature]         Induce calibrated fever (or hypothermia) via pyrogens (OP only)
/aliment cure                        Reset body to perfect health and clear screen shaders (OP only)
/aliment set <field> <value>         Directly configure physiological parameters (OP only)
```
