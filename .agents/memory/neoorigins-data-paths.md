---
name: NeoOrigins data paths and origin JSON format
description: NeoOrigins 2.2.5 uses nested origins/origins/ paths AND a different JSON schema from Origins (Fabric).
---

## Rule — File paths

NeoOrigins uses **nested** directory structure inside the JAR data pack:

| Type | Correct path | Origin ID |
|------|-------------|-----------|
| Origins | `data/<ns>/origins/origins/<name>.json` | `<ns>:<name>` |
| Origin layers | `data/<ns>/origins/origin_layers/<name>.json` | `<ns>:<name>` |
| Powers | `data/<ns>/powers/<origin>/<power>.json` | `<ns>:<origin>/<power>` |

**Why:** NeoOrigins groups all origin-related data under `data/<ns>/origins/`. Verified by inspecting `neoorigins-2.2.5+1.21.1.jar`.

## Rule — Origin JSON schema (NeoOrigins 2.2.5)

Required fields and format that differ from Origins (Fabric):

| Field | Wrong (old) | Correct (NeoOrigins 2.2.5) |
|-------|-------------|---------------------------|
| icon | `{"item": "minecraft:x"}` | `"minecraft:x"` (plain string) |
| upgrades | missing | `"upgrades": []` required |
| tier_powers | missing | `"tier_powers": []` required |
| loading_priority | present | not used, remove it |

**Why:** NeoOrigins 2.2.5 changed the JSON codec — icon is now a ResourceLocation string, and upgrades/tier_powers are part of the schema. Origins with the wrong icon format silently fail to parse → empty origin list.

**How to apply:** When creating new origins for chaos_addon, always use the string icon format and include `"upgrades": []` and `"tier_powers": []`.
