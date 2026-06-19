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
  - `origins/origins/` — origin definitions (JSON)
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
- Power path: `chaos_addon:eater_of_worlds/chaotic_aura` → `data/chaos_addon/powers/eater_of_worlds/chaotic_aura.json`.
- Commands registered in `ModCommands.register()` — see `init/ModCommands.java`.
- `SoundEvents` fields that are `Holder<SoundEvent>` need `.value()` in `playSound()` calls.
