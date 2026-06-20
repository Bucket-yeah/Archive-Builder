---
name: NeoOrigins 2.2.5 power type quirks
description: Power JSON format rules specific to NeoOrigins 2.2.5 — types that don't exist, deprecated types, and field format changes.
---

# NeoOrigins 2.2.5 Power JSON Quirks

## `neoorigins:nothing` does not exist
**Rule:** `neoorigins:nothing` is not a valid power type in NeoOrigins 2.2.5. Powers using it fail to load entirely (log: "Unknown power type 'neoorigins:nothing'").
**Fix:** Use `neoorigins:multiple` with no sub-power entries. It loads fine and does nothing on its own.
**Why:** The type was removed/renamed between Origins (Fabric) and NeoOrigins. `neoorigins:multiple` is the safe no-op marker for Java-handled passive powers.
**How to apply:** Any passive power that only exists for Java event handler detection should use `neoorigins:multiple`.

## `neoorigins:spawn_particles` — particle must be a string
**Rule:** The `particle` field in `neoorigins:spawn_particles` must be a plain string like `"minecraft:flame"`, NOT an object like `{"type": "minecraft:flame"}`.
**Fix:** `"particle": "minecraft:flame"` — single string, no wrapper object.
**Why:** NeoOrigins 2.2.5 CompatB layer parses the particle as a string; passing a JsonObject causes "parse error: JsonObject" and the action silently becomes a no-op.
**How to apply:** Every `neoorigins:spawn_particles` action. Only applies to simple particles with no extra data. (Particles with options like dust would need different handling.)

## `neoorigins:action_over_time` is deprecated
**Rule:** Deprecated in NeoOrigins 2.2.5 — still works but logs a warning.
**Fix:** Rename `type` to `neoorigins:condition_passive`. Same fields (`interval`, `entity_action`) work unchanged.
**Why:** API cleanup in NeoOrigins. The behavior is identical.
