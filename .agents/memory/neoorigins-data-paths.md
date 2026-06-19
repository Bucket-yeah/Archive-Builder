---
name: NeoOrigins data paths
description: NeoOrigins uses nested origins/origins/ and origins/origin_layers/ paths, NOT flat origins/ — differs from Origins (Fabric).
---

## Rule
NeoOrigins (NeoForge port) uses a **nested** directory structure inside the JAR data pack — different from the original Origins (Fabric) mod.

| Type | Correct path | Origin ID |
|------|-------------|-----------|
| Origins | `data/<ns>/origins/origins/<name>.json` | `<ns>:<name>` |
| Origin layers | `data/<ns>/origins/origin_layers/<name>.json` | `<ns>:<name>` |
| Powers | `data/<ns>/powers/<origin>/<power>.json` | `<ns>:<origin>/<power>` |

**Why:** NeoOrigins groups all origin-related data under `data/<ns>/origins/` as a sub-namespace. Verified by inspecting `neoorigins-2.2.5+1.21.1.jar` — its own origins live at `data/neoorigins/origins/origins/avian.json` etc.

**How to apply:** Whenever adding or moving origin/layer JSON files for chaos_addon, always place them in `origins/origins/` and `origins/origin_layers/` subdirectories. Powers remain at the flat `powers/<origin>/<power>.json` path.
