# Origins Chaos Addon — NeoForge 1.21.1

A NeoForge Minecraft mod adding 10 unique Origins with custom powers, Java event handlers, and Brigadier commands. Built with Gradle.

## Build

```bash
bash chaos_addon_output/build.sh
```

Outputs a `.jar` in `chaos_addon_output/build/libs/`.

## Stack

- Java 21, NeoForge 21.1.x for Minecraft 1.21.1
- NeoOrigins 2.2.5 — power/origin framework
- Gradle build system
- Brigadier commands (server-side, permission level 0)

## Where things live

- `chaos_addon_output/src/main/java/dev/chaosaddon/` — all Java source
  - `events/` — NeoForge event handlers (one per origin group)
  - `commands/` — Brigadier command implementations
  - `data/` — NBT data attachment classes
  - `config/` — TOML config
  - `util/` — OriginHelper, etc.
- `chaos_addon_output/src/main/resources/data/chaos_addon/`
  - `origins/` — origin definitions (JSON, flat — NOT nested `origins/origins/`)
  - `origins/origin_layers/` — origin layer definitions (JSON)
  - `powers/` — power definitions (JSON, loaded by NeoOrigins)

## Origins & their handlers

| Origin | Handler(s) |
|--------|-----------|
| Eater of Worlds | `ChaoticAuraHandler`, `HungerXPHandler`, `GeneralPowerHandler` (mad_whisper) |
| Swarm Lord | `SwarmHandler`, `GeneralPowerHandler` (royal_pheromone) |
| Time Wanderer | `GeneralPowerHandler` (deja_vu, stasis), `MiscCombatCommands` |
| Alchemical Monk | `AlchemistHandler` (material_imbalance, price_of_creation, cycle_of_substances) |
| Phantom Archaeologist | `ArchaeologistHandler` (ephemeral_inventory, mind_backup) |
| Deep Geomancer | `GeneralPowerHandler` (stone_flesh altitude), JSON powers |
| Biomorph | `BiomorphHandler` |
| Parasite | `ParasiteHandler` |
| Lunar Weaver | `LunarHandler` |
| Radioactive Phantom | `RadioactiveHandler`, `GeneralPowerHandler` |
| Nightmare Mimic | `GeneralPowerHandler` (illusory_flesh) |
| Ancient Sentinel | `GeneralPowerHandler` (liquid damage) |
| Dimension Judge | `GeneralPowerHandler` (lawfulness, annihilate) |

## Architecture decisions

- All commands run at permission level 0 (players can self-execute via power JSON).
- NeoOrigins loads powers from `data/<namespace>/powers/<path>.json` only — NOT from `origins/powers/`.
- Passive powers that need Java logic use `neoorigins:nothing` type + a registered event handler checking `OriginHelper.hasPower()`.
- `persistentData` (player NBT) is used for cross-death state (Archaeologist inventory/XP backup).

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Gotchas

- `OriginHelper.hasPower(player, "chaos_addon:<origin>/<power>")` is the pattern for all power checks.
- **NeoOrigins data paths** (differs from Origins/Fabric!):
  - Origins:      `data/chaos_addon/origins/<name>.json`  → ID `chaos_addon:<name>`
  - Origin layers:`data/chaos_addon/origins/origin_layers/<name>.json` → ID `chaos_addon:<name>`
  - Powers:       `data/chaos_addon/powers/<origin>/<power>.json` → ID `chaos_addon:<origin>/<power>`
- Commands registered in `ModCommands.register()` — see `init/ModCommands.java`.
- `SoundEvents` fields in 1.21.1 are plain `SoundEvent`, **not** `Holder<SoundEvent>` — do NOT call `.value()`.
- `LivingAttackEvent` was removed in NeoForge 21.1 — use `LivingIncomingDamageEvent` instead.
- **`neoorigins:nothing` does NOT exist in NeoOrigins 2.2.5** — use `neoorigins:multiple` (no sub-powers) as a no-op marker instead.
- **`neoorigins:spawn_particles` particle field must be a string** — `"particle": "minecraft:flame"`, NOT `{"type": "minecraft:flame"}`. Object format silently becomes no-op.
- **`neoorigins:action_over_time` is deprecated** — rename to `neoorigins:condition_passive` (same fields, same behaviour).
