# Aliment

> This project is conceptualized, architecturally designed, tested, and code-reviewed by human developers. Concrete code implementations are generated with AI assistance, with all additions thoroughly verified, proofread, and refined through human oversight.

Aliment is a realistic physiology, pathology, pharmacology, and herbal medicine mod for Minecraft Fabric.

Instead of treating player health as an abstract hitpoint bar that depletes instantly upon eating bad food, Aliment simulates continuous physiological kinetics, multi-pathway immune responses, electrolyte balances, and pharmacodynamics with clinical fidelity.

---

## Key Features

### Dynamic Physiology and Nutrition
- **Thirst and Hydration**: Introduces an independent thirst bar. Water balance drains steadily during daily activity and accelerates significantly during fevers, sweating, and heavy exertion.
- **Serum Electrolytes**: Tracks sodium, potassium, magnesium, chloride, and calcium in clinical mmol/L units. Overhydrating with plain water washes out electrolytes, while consuming salt water or excessive salt induces hypernatremia and acute thirst.
- **Trace Elements and Micronutrients**:
  - **Iodine (µmol/L)**: Essential for thyroid function and metabolic temperature set point regulation. Sourced from kelp, seaweed, and seaweed iodized salt. Unchecked deficiency causes hypothyroidism, hypothermia, sluggishness, and fatigue.
  - **Vitamin C (µmol/L)**: Follows real-world first-order clearance kinetics (higher concentrations clear faster). Replenished by plant foods such as apples, melons, carrots, and pumpkins. Depletion causes mining fatigue and scurvy-induced weakness.

### Infection and Immune Dynamics
- **Logistic Pathogen Growth**: Bacteria and viruses reproduce according to biological carrying capacity models instead of applying static damage ticks.
- **Five-Mediator Immune Regulation**: Histamine, prostaglandins, leukotrienes, cytokines, and bradykinin coordinate inflammatory defenses.
- **Bell-Shaped Immune Clearance**: Too little inflammation fails to clear pathogens, while runaway inflammation triggers dangerous cytokine storms that cause severe organ stress.
- **Thermoregulation and Fevers**: Core temperature reacts dynamically to pyrogens and the surrounding environment. Hypothermia slows physical reactions, whereas severe fevers induce profuse sweating, dehydration, and exhaustion.

### Herbal Medicine and Pharmacology
- **Willow (Salicin)**: Strip bark from riverbank willow trees and brew willow bark soup in cauldrons to obtain natural salicin for fever reduction and pain relief.
- **Ephedra (Ephedrine)**: Forage arid ephedra shrubs and grind twigs to prepare stimulant remedies granting increased mining speed and alertness.
- **Coptis and Phellodendron (Berberine)**: Bitter medicinal herbs yielding berberine, which specifically inhibits bacterial proliferation.
- **Licorice (Glycyrrhizin)**: Sweet root herb containing glycyrrhizin, acting as an antiviral agent and soothing mucosal inflammation.
- **Mandrake and Gymnopilus**: Dangerous nightshade plants carrying anticholinergic alkaloids (scopolamine and atropine) that induce blurred vision and fever, alongside psychoactive mushrooms producing perceptual distortions.
- **Dexamethasone**: Potent glucocorticoid injections that rapidly suppress out-of-control cytokine storms in critical medical emergencies.

### Agriculture, Brewing, and Crafting
- **Salt Processing**: Extract rock salt deposits underground, pulverize chunks on grindstones into crude salt, and refine them into high-grade salt powder.
- **Seaweed Aquaculture**: Plant and harvest underwater seaweed crops, cook them as nutritious food, or grind and combine them with salt to manufacture seaweed iodized salt.
- **Fermentation and Distillation**: Assemble glass fermentation tanks and condenser pipes to ferment mash into wine and distill concentrated ethanol.
- **Willow Woodset**: Complete decorative set featuring willow wood blocks, planks, hanging signs, and cascading hanging willow vines.

---

## Documentation and Guides

For detailed gameplay walkthroughs, mathematical formulas, and clinical reference ranges:

- **Mechanics and Spoilers Guide**: [SPOILER.md](https://github.com/cao-awa/Aliment/blob/main/SPOILER.md)  
  Contains item recipes, progression paths, symptom reference charts, and clinical tips.
- **Mathematical and Biological Model**: [PHYSIOLOGY.md](https://github.com/cao-awa/Aliment/blob/main/PHYSIOLOGY.md)  
  Contains differential equations, pharmacokinetic clearance rates, clinical units, and model derivations.
